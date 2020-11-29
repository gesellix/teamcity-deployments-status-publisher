package de.gesellix.teamcity.deployments.server.github.api.impl

import de.gesellix.github.client.GitHubClient
import de.gesellix.github.client.HttpStatusException
import de.gesellix.github.client.data.CommitCommentRequest
import de.gesellix.github.client.data.Deployment
import de.gesellix.github.client.data.DeploymentRequest
import de.gesellix.github.client.data.DeploymentStatus
import de.gesellix.github.client.data.DeploymentStatusRequest
import de.gesellix.teamcity.deployments.server.PublisherException
import de.gesellix.teamcity.deployments.server.github.api.GitHubApi
import de.gesellix.teamcity.deployments.server.logger
import java.io.IOException
import java.util.*
import java.util.regex.Pattern

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

  override fun getDeployments(
    owner: String,
    repo: String,
    filters: Map<String, String>
  ): List<Deployment>? {
    return gh.getDeployments(owner, repo, filters)
  }

  override fun createDeployment(
    owner: String,
    repo: String,
    deploymentRequest: DeploymentRequest
  ): Deployment? {
    return gh.createDeployment(owner, repo, deploymentRequest)
  }

  override fun updateDeploymentStatus(
    owner: String,
    repo: String,
    deploymentId: Long,
    deploymentStatusRequest: DeploymentStatusRequest
  ): DeploymentStatus? {
    return gh.updateDeploymentStatus(owner, repo, deploymentId, deploymentStatusRequest)
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
