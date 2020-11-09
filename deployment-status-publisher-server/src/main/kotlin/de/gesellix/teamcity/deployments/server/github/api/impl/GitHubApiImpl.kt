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
package de.gesellix.teamcity.deployments.server.github.api.impl

import de.gesellix.github.client.GitHubClient
import de.gesellix.github.client.HttpStatusException
import de.gesellix.github.client.data.CommitCommentRequest
import de.gesellix.github.client.data.CommitStatusRequest
import de.gesellix.teamcity.deployments.server.PublisherException
import de.gesellix.teamcity.deployments.server.github.api.GitHubApi
import de.gesellix.teamcity.deployments.server.github.api.GitHubChangeState
import de.gesellix.teamcity.deployments.server.logger
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 * @author Tomaz Cerar
 * Date: 05.09.12 23:39
 */
abstract class GitHubApiImpl(private val gh: GitHubClient) : GitHubApi {

  @Throws(PublisherException::class)
  override fun testConnection(
    repoOwner: String,
    repositoryName: String
  ) {
    val repoInfo = try {
      gh.getRepository(repoOwner, repositoryName)
    } catch (e: HttpStatusException) {
      throw PublisherException(String.format("Repository %s/%s is inaccessible", repoOwner, repositoryName))
    }
    if (null == repoInfo?.name) {
      throw PublisherException(String.format("Repository %s/%s is inaccessible", repoOwner, repositoryName))
    }
    if (!repoInfo.permissions.push) {
      throw PublisherException(String.format("There is no push access to the repository %s/%s", repoOwner, repositoryName))
    }
  }

  @Throws(IOException::class)
  override fun setChangeStatus(
    repoOwner: String,
    repositoryName: String,
    hash: String,
    status: GitHubChangeState,
    targetUrl: String,
    description: String,
    context: String?
  ) {
    val commitStatusRequest = CommitStatusRequest(state = status.state)
    commitStatusRequest.target_url = targetUrl
    commitStatusRequest.description = description
    commitStatusRequest.context = context

    try {
      gh.updateCommitStatus(repoOwner, repositoryName, hash, commitStatusRequest)
    } catch (e: Exception) {
      throw IOException("request failed: " + e.message, e)
    }
  }

  override fun isPullRequestMergeBranch(branchName: String): Boolean {
    val match = PULL_REQUEST_BRANCH.matcher(branchName)
    return match.matches() && "merge" == match.group(2)
  }

  @Throws(IOException::class, PublisherException::class)
  override fun findPullRequestCommit(
    repoOwner: String,
    repoName: String,
    branchName: String
  ): String? {
    val pullRequestId = getPullRequestId(repoName, branchName) ?: return null
    val pullRequest = gh.getPullRequest(repoOwner, repoName, pullRequestId)
    return pullRequest?.head?.sha
  }

  @Throws(IOException::class, PublisherException::class)
  override fun getCommitParents(repoOwner: String, repoName: String, hash: String): Collection<String> {
    val commit = gh.getCommit(repoOwner, repoName, hash)
    if (commit?.parents != null) {
      val parents: MutableSet<String> = HashSet()
      commit.parents?.forEach { p ->
        val sha: String? = p.sha
        if (sha != null) {
          parents.add(sha)
        }
      }
      return parents
    }
    return emptyList()
  }

  @Throws(IOException::class)
  override fun postComment(
    ownerName: String,
    repoName: String,
    hash: String,
    comment: String
  ) {
    gh.addCommitComment(ownerName, repoName, hash, CommitCommentRequest(body = comment))
  }

  companion object {

    val logger by logger(GitHubApiImpl::class.java.name)

    private val PULL_REQUEST_BRANCH = Pattern.compile("/?refs/pull/(\\d+)/(.*)")

    private fun getPullRequestId(
      repoName: String,
      branchName: String
    ): Int? {
      val matcher = PULL_REQUEST_BRANCH.matcher(branchName)
      if (!matcher.matches()) {
        logger.debug("Branch $branchName for repo $repoName does not look like pull request")
        return null
      }
      val pullRequestId = matcher.group(1)
      if (pullRequestId == null) {
        logger.debug("Branch $branchName for repo $repoName does not contain pull request id")
        return null
      }
      return pullRequestId.toInt()
    }
  }
}
