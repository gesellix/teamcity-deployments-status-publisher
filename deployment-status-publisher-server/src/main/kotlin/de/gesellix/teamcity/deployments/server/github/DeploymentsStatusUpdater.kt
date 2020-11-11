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

import com.squareup.moshi.Moshi
import de.gesellix.github.client.Timeout
import de.gesellix.github.client.data.DeploymentRequest
import de.gesellix.github.client.data.DeploymentStatusRequest
import de.gesellix.github.client.data.DeploymentStatusState
import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisherProblems
import de.gesellix.teamcity.deployments.server.GITHUB_AUTH_TYPE
import de.gesellix.teamcity.deployments.server.GITHUB_CONTEXT
import de.gesellix.teamcity.deployments.server.GITHUB_SERVER
import de.gesellix.teamcity.deployments.server.GITHUB_TOKEN
import de.gesellix.teamcity.deployments.server.GitRepositoryParser
import de.gesellix.teamcity.deployments.server.PublisherException
import de.gesellix.teamcity.deployments.server.Repository
import de.gesellix.teamcity.deployments.server.github.api.GitHubApi
import de.gesellix.teamcity.deployments.server.github.api.GitHubApiAuthenticationType
import de.gesellix.teamcity.deployments.server.github.api.GitHubApiFactory
import de.gesellix.teamcity.deployments.server.logger
import jetbrains.buildServer.messages.Status
import jetbrains.buildServer.serverSide.RepositoryVersion
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.WebLinks
import jetbrains.buildServer.serverSide.executors.ExecutorServices
import jetbrains.buildServer.serverSide.impl.BaseBuild
import jetbrains.buildServer.serverSide.impl.LogUtil
import jetbrains.buildServer.util.ExceptionUtil
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.vcs.VcsRoot
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.Lock

/**
 * Created by Eugene Petrenko (eugene.petrenko@gmail.com)
 * Date: 06.09.12 3:29
 */
class DeploymentsStatusUpdater(
  services: ExecutorServices,
  private val factory: GitHubApiFactory,
  private val webLinks: WebLinks
) {

  private val logger by logger(DeploymentsStatusUpdater::class.java.name)
  private val executor: ExecutorService = services.lowPriorityExecutorService

  private fun getGitHubApi(params: Map<String, String>, timeout: Timeout = Timeout(10, TimeUnit.SECONDS)): GitHubApi {
    val serverUrl = params[GITHUB_SERVER]
    require(!(serverUrl == null || StringUtil.isEmptyOrSpaces(serverUrl))) { "Failed to read GitHub URL from the feature settings" }
    return when (val authenticationType = GitHubApiAuthenticationType.parse(params[GITHUB_AUTH_TYPE])) {
      GitHubApiAuthenticationType.TOKEN_AUTH -> {
        val token = params[GITHUB_TOKEN]
        factory.openGitHubForToken(serverUrl, token!!, timeout)
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
    val repo: Repository? = if (null == url) {
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
    val moshi = Moshi.Builder().build()
    val api = getGitHubApi(params, Timeout(publisher.getConnectionTimeout().toLong(), TimeUnit.MILLISECONDS))
    val repo: Repository = parseRepository(root)
    val repositoryOwner: String = repo.owner()
    val repositoryName: String = repo.repositoryName()
    val ctx = params[GITHUB_CONTEXT]
    val context = if (StringUtil.isEmpty(ctx)) "continuous-integration/teamcity" else ctx!!
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

      private fun shouldCreateDeployment(build: SBuild): Boolean {
        val isPartOfBuildChain = build.buildPromotion.isPartOfBuildChain
        if (isPartOfBuildChain && build.buildPromotion.dependencies.isNotEmpty()) {
          return false
//          val dependencies = build.buildPromotion.dependencies
//          println("deps: $dependencies")
//          // TODO don't use `first()`, replace with config
//          val associatedBuild = build.buildPromotion.dependencies.first().dependOn.associatedBuild
//          if (associatedBuild is BaseBuild) {
//            val deploymentId = associatedBuild.buildFinishParameters?.get("GITHUB_DEPLOYMENT_ID")
//            println("deploymentId: $deploymentId from build ${associatedBuild.id}/${associatedBuild.buildTypeName}")
//          }
        }
        return true
      }

      override fun scheduleChangeStarted(hash: RepositoryVersion, build: SBuild, environment: String) {
        if (shouldCreateDeployment(build)) {
          scheduleDeploymentCreation(hash, build, "TeamCity build started", DeploymentStatusState.pending, environment)
        } else {
          scheduleDeploymentStatusUpdate(hash, build, "TeamCity build started", DeploymentStatusState.pending, environment)
        }
      }

      override fun scheduleChangeCompleted(hash: RepositoryVersion, build: SBuild, environment: String) {
        logger.debug("Status :" + build.statusDescriptor.status.text)
        logger.debug("Status Priority:" + build.statusDescriptor.status.priority)
        val status = getGitHubDeploymentStatus(build)
        val text = getGitHubChangeText(build)
        scheduleDeploymentStatusUpdate(hash, build, text, status, environment)
      }

      private fun getGitHubChangeText(build: SBuild): String {
        return if (build.buildStatus.isSuccessful) {
          "TeamCity build finished"
        } else {
          "TeamCity build failed"
        }
      }

      private fun getGitHubDeploymentStatus(build: SBuild): DeploymentStatusState {
        val status = build.statusDescriptor.status
        return when (status.priority) {
          Status.NORMAL.priority -> {
            DeploymentStatusState.success
          }
          Status.FAILURE.priority -> {
            DeploymentStatusState.failure
          }
          else -> {
            DeploymentStatusState.error
          }
        }
      }

      private fun resolveCommitHash(
        version: RepositoryVersion,
        build: SBuild,
        status: DeploymentStatusState
      ): String {
        val vcsBranch = version.vcsBranch
        if (vcsBranch != null && api.isPullRequestMergeBranch(vcsBranch)) {
          try {
            val hash = api.findPullRequestCommit(repositoryOwner, repositoryName, vcsBranch) ?: throw IOException("Failed to find head hash for commit from $vcsBranch")
            logger.info(
              "Resolved GitHub change commit for " + vcsBranch + " to point to pull request head for " +
                "hash: " + version.version + ", " +
                "newHash: " + hash + ", " +
                "branch: " + version.vcsBranch + ", " +
                "buildId: " + build.buildId + ", " +
                "status: " + status
            )
            return hash
          } catch (e: Exception) {
            logger.warn("Failed to find status update hash for $vcsBranch for repository $repositoryName")
          }
        }
        return version.version
      }

      // TODO get deployment id from dependent build if part and not root of a build chain - or default to null
      private fun findDeploymentId(sha: String, environment: String, build: SBuild): Long? {
        val deployments = api.getDeployments(repositoryOwner, repositoryName, mapOf("sha" to sha, "environment" to environment)) ?: return null
        deployments.forEach {
          if (it.payload != null) {
            val payload = moshi.adapter(Map::class.java).fromJson(it.payload as String)
            if (payload?.get("buildIdAsString") == "" + build.buildId) {
              return it.id
            }
          }
        }
        return null
      }

      private fun scheduleDeploymentCreation(
        version: RepositoryVersion,
        build: SBuild,
        message: String,
        status: DeploymentStatusState,
        environment: String
      ) {
        logger.info(
          "Scheduling GitHub deployment for " +
            "hash: " + version.version + ", " +
            "branch: " + version.vcsBranch + ", " +
            "buildId: " + build.buildId + ", " +
            "status: " + status
        )
        executor.submit(ExceptionUtil.catchAll("create deployment on github", object : Runnable {

          override fun run() {
            val hash = resolveCommitHash(version, build, status)
            val lock: Lock = publisher.getLocks()[publisher.getBuildType().externalId]
            val problems: DeploymentsStatusPublisherProblems = publisher.getProblems()
            val prMergeBranch = hash != version.version
            lock.lock()
            try {
              try {
                val deployment = api.createDeployment(
                  repositoryOwner,
                  repositoryName,
                  DeploymentRequest(hash).apply {
                    this.environment = environment
                    this.payload = moshi.adapter(Map::class.java).toJson(mapOf("buildId" to build.buildId))
                    this.description = "$message (${if (prMergeBranch) "$context - merge" else context})"
                  })
                logger.info("Created GitHub deployment ${deployment?.id} for hash: $hash, buildId: ${build.buildId}, status: $status")
              } catch (e: IOException) {
                problems.reportProblem(String.format("Deployments Status Publisher error. GitHub status: '%s'", status.toString()), publisher, LogUtil.describe(build), publisher.serverUrl, e, logger)
              }
            } finally {
              lock.unlock()
            }
          }
        }))
      }

      private fun scheduleDeploymentStatusUpdate(
        version: RepositoryVersion,
        build: SBuild,
        message: String,
        status: DeploymentStatusState,
        environment: String
      ) {
        logger.info(
          "Scheduling GitHub deployment status update for " +
            "hash: " + version.version + ", " +
            "branch: " + version.vcsBranch + ", " +
            "buildId: " + build.buildId + ", " +
            "status: " + status
        )
        executor.submit(ExceptionUtil.catchAll("set deployment status on github", object : Runnable {

          override fun run() {
            val hash = resolveCommitHash(version, build, status)
            val lock: Lock = publisher.getLocks()[publisher.getBuildType().externalId]
            val problems: DeploymentsStatusPublisherProblems = publisher.getProblems()
            val prMergeBranch = hash != version.version
            lock.lock()
            val url: String
            try {
              try {
                var deploymentId: Long? = findDeploymentId(hash, environment, build)
                if (deploymentId == null) {
                  problems.reportProblem(
                    "Deployments Status Publisher error. DeploymentId not found for hash: $hash, environment: $environment, build: ${LogUtil.describe(build)}",
                    publisher,
                    LogUtil.describe(build),
                    publisher.serverUrl,
                    null,
                    logger
                  )
                  return
                }
                url = getViewResultsUrl(build)
                api.updateDeploymentStatus(
                  repositoryOwner,
                  repositoryName,
                  deploymentId,
                  DeploymentStatusRequest(status).apply {
                    this.environment = environment
                    this.description = "$message (${if (prMergeBranch) "$context - merge" else context})"
                    this.log_url = url
                  },
                )
                logger.info("Updated GitHub deployment status for hash: $hash, buildId: ${build.buildId}, status: $status")
                if (build is BaseBuild) {
                  build.buildFinishParameters?.put("TGE_TEST", "done")
                }
              } catch (e: IOException) {
                problems.reportProblem(String.format("Deployments Status Publisher error. GitHub status: '%s'", status.toString()), publisher, LogUtil.describe(build), publisher.serverUrl, e, logger)
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
    fun scheduleChangeStarted(hash: RepositoryVersion, build: SBuild, environment: String)
    fun scheduleChangeCompleted(hash: RepositoryVersion, build: SBuild, environment: String)
  }
}
