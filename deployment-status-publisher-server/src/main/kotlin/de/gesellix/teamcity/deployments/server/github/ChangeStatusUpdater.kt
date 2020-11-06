/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.gesellix.teamcity.deployments.server.github

import com.intellij.openapi.diagnostic.Logger
import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisherProblems
import de.gesellix.teamcity.deployments.server.GITHUB_AUTH_TYPE
import de.gesellix.teamcity.deployments.server.GITHUB_CONTEXT
import de.gesellix.teamcity.deployments.server.GITHUB_PASSWORD
import de.gesellix.teamcity.deployments.server.GITHUB_PASSWORD_DEPRECATED
import de.gesellix.teamcity.deployments.server.GITHUB_SERVER
import de.gesellix.teamcity.deployments.server.GITHUB_TOKEN
import de.gesellix.teamcity.deployments.server.GITHUB_USERNAME
import de.gesellix.teamcity.deployments.server.GitRepositoryParser
import de.gesellix.teamcity.deployments.server.PublisherException
import de.gesellix.teamcity.deployments.server.Repository
import de.gesellix.teamcity.deployments.server.github.api.GitHubApi
import de.gesellix.teamcity.deployments.server.github.api.GitHubApiAuthenticationType
import de.gesellix.teamcity.deployments.server.github.api.GitHubApiFactory
import de.gesellix.teamcity.deployments.server.github.api.GitHubChangeState
import jetbrains.buildServer.messages.Status
import jetbrains.buildServer.serverSide.BuildStatisticsOptions
import jetbrains.buildServer.serverSide.RepositoryVersion
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.TestFailureInfo
import jetbrains.buildServer.serverSide.WebLinks
import jetbrains.buildServer.serverSide.executors.ExecutorServices
import jetbrains.buildServer.serverSide.impl.LogUtil
import jetbrains.buildServer.util.ExceptionUtil
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.vcs.VcsRoot
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.locks.Lock

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 06.09.12 3:29
 */
class ChangeStatusUpdater(
  services: ExecutorServices,
  private val factory: GitHubApiFactory,
  private val webLinks: WebLinks
) {

  private val executor: ExecutorService = services.lowPriorityExecutorService

  private fun getGitHubApi(params: Map<String, String>): GitHubApi {
    val serverUrl = params[GITHUB_SERVER]
    require(!(serverUrl == null || StringUtil.isEmptyOrSpaces(serverUrl))) { "Failed to read GitHub URL from the feature settings" }
    return when (val authenticationType = GitHubApiAuthenticationType.parse(params[GITHUB_AUTH_TYPE])) {
      GitHubApiAuthenticationType.PASSWORD_AUTH -> {
        val username = params[GITHUB_USERNAME]
        var password = params[GITHUB_PASSWORD]
        if (password == null) {
          password = params[GITHUB_PASSWORD_DEPRECATED]
        }
        factory.openGitHubForUser(serverUrl, username!!, password!!)
      }
      GitHubApiAuthenticationType.TOKEN_AUTH -> {
        val token = params[GITHUB_TOKEN]
        factory.openGitHubForToken(serverUrl, token!!)
      }
      else -> throw IllegalArgumentException("Failed to parse authentication type:$authenticationType")
    }
  }

  @Throws(PublisherException::class)
  fun testConnection(root: VcsRoot, params: Map<String, String>) {
    val api = getGitHubApi(params)
    val repo: Repository = parseRepository(root)
    api.testConnection(repo.owner(), repo.repositoryName())
  }

  @Throws(PublisherException::class)
  private fun parseRepository(root: VcsRoot): Repository {
    val url = root.getProperty("url")
    val repo: Repository?
    repo = if (null == url) {
      null
    } else {
      GitRepositoryParser.parseRepository(url)
    }
    if (null == repo) throw PublisherException("Cannot parse repository URL from VCS root " + root.name)
    return repo
  }

  @Throws(PublisherException::class)
  fun getUpdateHandler(
    root: VcsRoot,
    params: Map<String, String>,
    publisher: GitHubPublisher
  ): Handler {
    val api = getGitHubApi(params)
    val repo: Repository = parseRepository(root)
    val repositoryOwner: String = repo.owner()
    val repositoryName: String = repo.repositoryName()
    val ctx = params[GITHUB_CONTEXT]
    val context = if (StringUtil.isEmpty(ctx)) "continuous-integration/teamcity" else ctx!!
    val addComments = false
    val shouldReportOnStart = true
    val shouldReportOnFinish = true
    return object : Handler {
      private fun getViewResultsUrl(build: SBuild): String {
        return webLinks.getViewResultsUrl(build)
      }

      override fun shouldReportOnStart(): Boolean {
        return shouldReportOnStart
      }

      override fun shouldReportOnFinish(): Boolean {
        return shouldReportOnFinish
      }

      override fun scheduleChangeStarted(hash: RepositoryVersion, build: SBuild) {
        scheduleChangeUpdate(hash, build, "TeamCity build started", GitHubChangeState.Pending)
      }

      override fun scheduleChangeCompleted(hash: RepositoryVersion, build: SBuild) {
        LOG.debug("Status :" + build.statusDescriptor.status.text)
        LOG.debug("Status Priority:" + build.statusDescriptor.status.priority)
        val status = getGitHubChangeState(build)
        val text = getGitHubChangeText(build)
        scheduleChangeUpdate(hash, build, text, status)
      }

      val publisher: GitHubPublisher
        get() = publisher

      private fun getGitHubChangeText(build: SBuild): String {
        return if (build.buildStatus.isSuccessful) {
          "TeamCity build finished"
        } else {
          "TeamCity build failed"
        }
      }

      private fun getGitHubChangeState(build: SBuild): GitHubChangeState {
        val status = build.statusDescriptor.status
        val priority = status.priority
        return if (priority == Status.NORMAL.priority) {
          GitHubChangeState.Success
        } else if (priority == Status.FAILURE.priority) {
          GitHubChangeState.Failure
        } else {
          GitHubChangeState.Error
        }
      }

      private fun scheduleChangeUpdate(
        version: RepositoryVersion,
        build: SBuild,
        message: String,
        status: GitHubChangeState
      ) {
        LOG.info(
          "Scheduling GitHub status update for " +
            "hash: " + version.version + ", " +
            "branch: " + version.vcsBranch + ", " +
            "buildId: " + build.buildId + ", " +
            "status: " + status
        )
        executor.submit(ExceptionUtil.catchAll("set change status on github", object : Runnable {
          private fun getFailureText(failureInfo: TestFailureInfo?): String {
            val noData = "<no details available>"
            if (failureInfo == null) return noData
            val stacktrace = failureInfo.shortStacktrace
            return if (stacktrace == null || StringUtil.isEmptyOrSpaces(stacktrace)) noData else stacktrace
          }

          private fun getFriendlyDuration(seconds: Long): String {
            val second = seconds % 60
            val minute = seconds / 60 % 60
            val hour = seconds / 60 / 60
            return String.format("%02d:%02d:%02d", hour, minute, second)
          }

          private fun getComment(
            version: RepositoryVersion,
            build: SBuild,
            completed: Boolean,
            hash: String
          ): String {
            val comment = StringBuilder()
            comment.append("TeamCity ")
            val bt = build.buildType
            if (bt != null) {
              comment.append(bt.fullName)
            }
            comment.append(" [Build ")
            comment.append(build.buildNumber)
            comment.append("](")
            comment.append(getViewResultsUrl(build))
            comment.append(") ")
            if (completed) {
              comment.append("outcome was **").append(build.statusDescriptor.status.text).append("**")
            } else {
              comment.append("is now running")
            }
            comment.append("\n")
            val text = build.statusDescriptor.text
            if (completed && text != null) {
              comment.append("Summary: ")
              comment.append(text)
              comment.append(" Build time: ")
              comment.append(getFriendlyDuration(build.duration))
              if (build.buildStatus != Status.NORMAL) {
                val stats = build.getBuildStatistics(BuildStatisticsOptions.ALL_TESTS_NO_DETAILS)
                val failedTests = stats.failedTests
                if (!failedTests.isEmpty()) {
                  comment.append("\n### Failed tests\n")
                  comment.append("```\n")
                  for (i in failedTests.indices) {
                    val testRun = failedTests[i]
                    comment.append("")
                    comment.append(testRun.test.name.toString())
                    comment.append("\n")
                    if (i == 10) {
                      comment.append("\n##### there are ")
                        .append(stats.failedTestCount - i)
                        .append(" more failed tests, see build details\n")
                      break
                    }
                  }
                  comment.append("```\n")
                }
              }
            }
            return comment.toString()
          }

          private fun resolveCommitHash(): String {
            val vcsBranch = version.vcsBranch
            if (vcsBranch != null && api.isPullRequestMergeBranch(vcsBranch)) {
              try {
                val hash = api.findPullRequestCommit(repositoryOwner, repositoryName, vcsBranch) ?: throw IOException("Failed to find head hash for commit from $vcsBranch")
                LOG.info(
                  "Resolved GitHub change commit for " + vcsBranch + " to point to pull request head for " +
                    "hash: " + version.version + ", " +
                    "newHash: " + hash + ", " +
                    "branch: " + version.vcsBranch + ", " +
                    "buildId: " + build.buildId + ", " +
                    "status: " + status
                )
                return hash
              } catch (e: Exception) {
                LOG.warn("Failed to find status update hash for $vcsBranch for repository $repositoryName")
              }
            }
            return version.version
          }

          override fun run() {
            val hash = resolveCommitHash()
            val lock: Lock = publisher.getLocks()[publisher.getBuildType().externalId]
            val problems: DeploymentsStatusPublisherProblems = publisher.getProblems()
            val prMergeBranch = hash != version.version
            lock.lock()
            val url: String
            try {
              try {
                url = getViewResultsUrl(build)
                api.setChangeStatus(
                  repositoryOwner,
                  repositoryName,
                  hash,
                  status,
                  url,
                  message,
                  if (prMergeBranch) "$context - merge" else context
                )
                LOG.info("Updated GitHub status for hash: " + hash + ", buildId: " + build.buildId + ", status: " + status)
              } catch (e: IOException) {
                problems.reportProblem(String.format("Deployments Status Publisher error. GitHub status: '%s'", status.toString()), publisher, LogUtil.describe(build), publisher.serverUrl, e, LOG)
              }
              if (addComments) {
                try {
                  api.postComment(
                    repositoryOwner,
                    repositoryName,
                    hash,
                    getComment(version, build, status !== GitHubChangeState.Pending, hash)
                  )
                  LOG.info("Added comment to GitHub commit: " + hash + ", buildId: " + build.buildId + ", status: " + status)
                } catch (e: IOException) {
                  problems.reportProblem("Deployments Status Publisher has failed to add a comment", publisher, LogUtil.describe(build), null, e, LOG)
                }
              }
            } finally {
              lock.unlock()
            }
          }
        }))
      }
    }
  }

  interface Handler {

    fun shouldReportOnStart(): Boolean
    fun shouldReportOnFinish(): Boolean
    fun scheduleChangeStarted(hash: RepositoryVersion, build: SBuild)
    fun scheduleChangeCompleted(hash: RepositoryVersion, build: SBuild)
  }

  companion object {

    private val LOG = Logger.getInstance(ChangeStatusUpdater::class.java.name)
  }
}