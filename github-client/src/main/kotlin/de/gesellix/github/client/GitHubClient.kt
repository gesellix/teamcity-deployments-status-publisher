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
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class GitHubClient(
  baseUrl: String = "https://api.github.com/",
  token: String = "",
  userAgentString: String = "",
  timeout: Timeout = Timeout(10, TimeUnit.SECONDS)
) {

  val loggingInterceptor = HttpLoggingInterceptor()

  private var retrofit = Retrofit.Builder()
    .baseUrl(baseUrl)
    .addConverterFactory(MoshiConverterFactory.create())
//    .addConverterFactory(GraphQLConverterFactory())
//    .addCallAdapterFactory(CallAdapterFactory())
    .client(
      OkHttpClient()
        .newBuilder()
        .connectTimeout(timeout.timeout, timeout.unit)
        .readTimeout(timeout.timeout, timeout.unit)
        .writeTimeout(timeout.timeout, timeout.unit)
        .addInterceptor(loggingInterceptor)
        .addInterceptor(TokenAuthorizationInterceptor(token))
        .addInterceptor(UserAgentInterceptor(userAgentString))
        .build()
    )
    .build()

  private var github = retrofit.create(GitHubService::class.java)

  init {
    loggingInterceptor.level = HttpLoggingInterceptor.Level.BASIC
    loggingInterceptor.redactHeader("Authorization")
    loggingInterceptor.redactHeader("Cookie")
  }

  fun <RES> executeRequest(request: Call<RES>): RES? {
    val response = request.execute() // TODO consider `.enqueue()`
    if (!response.isSuccessful) {
      throw HttpStatusException(response.code(), "request failed", response)
//      throw RuntimeException("error: code=${response.code()} body=${response.errorBody()?.string()}")
    }
    return response.body()
  }

  fun getRepository(owner: String, repo: String): Repository? {
    val request = github.getRepository(owner, repo)
    return executeRequest(request)
  }

  fun getCommit(owner: String, repo: String, commitSha: String): Commit? {
    val request = github.getCommit(owner, repo, commitSha)
    return executeRequest(request)
  }

  fun addCommitComment(owner: String, repo: String, commitSha: String, commitCommentRequest: CommitCommentRequest): CommitComment? {
    val request = github.addCommitComment(owner, repo, commitSha, commitCommentRequest)
    return executeRequest(request)
  }

  fun getCommitStatusesSummary(owner: String, repo: String, ref: String): CommitStatusesSummary? {
    val request = github.getCommitStatusesSummary(owner, repo, ref)
    return executeRequest(request)
  }

  fun getCommitStatuses(owner: String, repo: String, ref: String): Array<CommitStatus>? {
    val request = github.getCommitStatuses(owner, repo, ref)
    return executeRequest(request)
  }

  fun updateCommitStatus(owner: String, repo: String, sha: String, commitStatus: CommitStatusRequest): CommitStatus? {
    val request = github.updateCommitStatus(owner, repo, sha, commitStatus)
    return executeRequest(request)
  }

  fun getPullRequest(owner: String, repo: String, pullNumber: Int): PullRequest? {
    val request = github.getPullRequest(owner, repo, pullNumber)
    return executeRequest(request)
  }

  fun getDeployments(owner: String, repo: String, filters: Map<String, String>): List<Deployment>? {
    val request = github.getDeployments(owner, repo, filters)
    return executeRequest(request)
  }

  fun createDeployment(owner: String, repo: String, deploymentRequest: DeploymentRequest): Deployment? {
    val request = github.createDeployment(owner, repo, deploymentRequest)
    return executeRequest(request)
  }

  fun updateDeploymentStatus(owner: String, repo: String, deploymentId: Long, deploymentStatusRequest: DeploymentStatusRequest): DeploymentStatus? {
    val request = github.updateDeploymentStatus(owner, repo, deploymentId, deploymentStatusRequest)
    return executeRequest(request)
  }
}

class TokenAuthorizationInterceptor(private val token: String) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    return if (token.isEmpty()) {
      chain.proceed(chain.request())
    } else chain.proceed(
      chain.request()
        .newBuilder()
        .header("Authorization", "token $token")
        .build()
    )
  }
}

class UserAgentInterceptor(private val userAgentString: String) : Interceptor {

  override fun intercept(chain: Interceptor.Chain): Response {
    return if (userAgentString.isEmpty()) {
      chain.proceed(chain.request())
    } else chain.proceed(
      chain.request()
        .newBuilder()
        .header("User-Agent", userAgentString)
        .build()
    )
  }
}
