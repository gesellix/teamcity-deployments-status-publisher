package de.gesellix.teamcity.deployments.server.github.api

import de.gesellix.github.client.data.Deployment
import de.gesellix.github.client.data.DeploymentRequest
import de.gesellix.github.client.data.DeploymentStatus
import de.gesellix.github.client.data.DeploymentStatusRequest
import de.gesellix.teamcity.deployments.server.PublisherException
import java.io.IOException

interface GitHubApi {

  @Throws(PublisherException::class)
  fun testConnection(repoOwner: String, repositoryName: String)

  fun getDeployments(
    owner: String,
    repo: String,
    filters: Map<String, String>
  ): List<Deployment>?

  fun createDeployment(
    owner: String,
    repo: String,
    deploymentRequest: DeploymentRequest
  ): Deployment?

  fun updateDeploymentStatus(
    owner: String,
    repo: String,
    deploymentId: Long,
    deploymentStatusRequest: DeploymentStatusRequest
  ): DeploymentStatus?

  /**
   * checks if specified branch represents GitHub pull request merge branch,
   * i.e. /refs/pull/X/merge
   * @param branchName branch name
   * @return true if branch is pull's merge
   */
  fun isPullRequestMergeBranch(branchName: String): Boolean

  /**
   * this method parses branch name and attempts to detect
   * /refs/pull/X/head revision for given branch
   *
   * The main use-case for it is to resolve /refs/pull/X/merge branch
   * into head commit hash in order to call github status API
   *
   * @param repoOwner repository owner name (who owns repo where you see pull request)
   * @param repoName repository name (where you see pull request)
   * @param branchName detected branch name in TeamCity, i.e. /refs/pull/X/merge
   * @return found /refs/pull/X/head or null
   * @throws IOException on communication error
   */
  @Throws(IOException::class, PublisherException::class)
  fun findPullRequestCommit(
    repoOwner: String,
    repoName: String,
    branchName: String
  ): String?

  /**
   * return parent commits for given commit
   * @param repoOwner repo owner
   * @param repoName repo name
   * @param hash commit hash
   * @return colleciton of commit parents
   * @throws IOException
   */
  @Throws(IOException::class, PublisherException::class)
  fun getCommitParents(
    repoOwner: String,
    repoName: String,
    hash: String
  ): Collection<String?>

  /**
   * Post comment to pull request
   * @param repoName
   * @param hash
   * @param comment
   * @throws IOException
   */
  @Throws(IOException::class)
  fun postComment(
    ownerName: String,
    repoName: String,
    hash: String,
    comment: String
  )
}
