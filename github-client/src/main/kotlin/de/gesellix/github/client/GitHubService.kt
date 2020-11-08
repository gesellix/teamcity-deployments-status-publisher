package de.gesellix.github.client

import de.gesellix.github.client.data.Commit
import de.gesellix.github.client.data.CommitComment
import de.gesellix.github.client.data.CommitCommentRequest
import de.gesellix.github.client.data.CommitStatus
import de.gesellix.github.client.data.CommitStatusRequest
import de.gesellix.github.client.data.CommitStatusesSummary
import de.gesellix.github.client.data.Deployment
import de.gesellix.github.client.data.DeploymentRequest
import de.gesellix.github.client.data.DeploymentStatus
import de.gesellix.github.client.data.DeploymentStatusRequest
import de.gesellix.github.client.data.PullRequest
import de.gesellix.github.client.data.Repository
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.POST
import retrofit2.http.Path

interface GitHubService {

  @GET("repos/{owner}/{repo}")
  fun getRepository(
    @Path("owner") owner: String,
    @Path("repo") repo: String
  ): Call<Repository>

  @GET("repos/{owner}/{repo}/git/commits/{commit_sha}")
  fun getCommit(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("commit_sha") commitSha: String
  ): Call<Commit>

  @POST("repos/{owner}/{repo}/commits/{commit_sha}/comments")
  fun addCommitComment(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("commit_sha") sha: String,
    @Body commitComment: CommitCommentRequest
  ): Call<CommitComment>

  @GET("repos/{owner}/{repo}/commits/{ref}/status")
  fun getCommitStatusesSummary(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("ref") ref: String
  ): Call<CommitStatusesSummary>

  @GET("repos/{owner}/{repo}/commits/{ref}/statuses")
  fun getCommitStatuses(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("ref") ref: String
  ): Call<Array<CommitStatus>>

  @POST("repos/{owner}/{repo}/statuses/{sha}")
  fun updateCommitStatus(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("sha") sha: String,
    @Body commitStatus: CommitStatusRequest
  ): Call<CommitStatus>

  @GET("repos/{owner}/{repo}/pulls/{pull_number}")
  fun getPullRequest(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("pull_number") pullNumber: Int
  ): Call<PullRequest>

  @Headers("Accept: application/vnd.github.ant-man-preview+json, application/vnd.github.flash-preview+json")
  @POST("repos/{owner}/{repo}/deployments")
  fun createDeployment(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Body deployment: DeploymentRequest
  ): Call<Deployment>

  @Headers("Accept: application/vnd.github.ant-man-preview+json, application/vnd.github.flash-preview+json")
  @POST("repos/{owner}/{repo}/deployments/{deploymentId}/statuses")
  fun updateDeploymentStatus(
    @Path("owner") owner: String,
    @Path("repo") repo: String,
    @Path("deploymentId") deploymentId: Int,
    @Body deploymentStatusRequest: DeploymentStatusRequest
  ): Call<DeploymentStatus>
}
