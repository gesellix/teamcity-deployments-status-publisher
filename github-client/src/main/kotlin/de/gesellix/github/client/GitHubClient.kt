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

  fun getRepository(owner: String, repo: String): Repository? {
    val repoInfo = github.getRepository(owner, repo)
    val response = repoInfo.execute() // TODO consider `.enqueue()`
    if (!response.isSuccessful) {
      throw HttpStatusException(response.code(), "request failed", response)
//      throw RuntimeException("error: code=${response.code()} body=${response.errorBody()?.string()}")
    }
    return response.body()
  }

  fun getCommit(owner: String, repo: String, commitSha: String): Commit? {
    val repoInfo = github.getCommit(owner, repo, commitSha)
    val response = repoInfo.execute() // TODO consider `.enqueue()`
    if (!response.isSuccessful) {
      throw RuntimeException("error: code=${response.code()} body=${response.errorBody()?.string()}")
    }
    return response.body()
  }

  fun addCommitComment(owner: String, repo: String, commitSha: String, commitCommentRequest: CommitCommentRequest): CommitComment? {
    val repoInfo = github.addCommitComment(owner, repo, commitSha, commitCommentRequest)
    val response = repoInfo.execute() // TODO consider `.enqueue()`
    if (!response.isSuccessful) {
      throw RuntimeException("error: code=${response.code()} body=${response.errorBody()?.string()}")
    }
    return response.body()
  }

  fun getCommitStatusesSummary(owner: String, repo: String, ref: String): CommitStatusesSummary? {
    val status = github.getCommitStatusesSummary(owner, repo, ref)
    val response = status.execute() // TODO consider `.enqueue()`
    if (!response.isSuccessful) {
      throw RuntimeException("error: code=${response.code()} body=${response.errorBody()?.string()}")
    }
    return response.body()
  }

  fun getCommitStatuses(owner: String, repo: String, ref: String): Array<CommitStatus>? {
    val status = github.getCommitStatuses(owner, repo, ref)
    val response = status.execute() // TODO consider `.enqueue()`
    if (!response.isSuccessful) {
      throw RuntimeException("error: code=${response.code()} body=${response.errorBody()?.string()}")
    }
    return response.body()
  }

  fun updateCommitStatus(owner: String, repo: String, sha: String, commitStatus: CommitStatusRequest): CommitStatus? {
    val status = github.updateCommitStatus(owner, repo, sha, commitStatus)
    val response = status.execute() // TODO consider `.enqueue()`
    if (!response.isSuccessful) {
      throw RuntimeException("error: code=${response.code()} body=${response.errorBody()?.string()}")
    }
    return response.body()
  }

  fun getPullRequest(owner: String, repo: String, pullNumber: Int): PullRequest? {
    val status = github.getPullRequest(owner, repo, pullNumber)
    val response = status.execute() // TODO consider `.enqueue()`
    if (!response.isSuccessful) {
      throw RuntimeException("error: code=${response.code()} body=${response.errorBody()?.string()}")
    }
    return response.body()
  }

  fun getDeployments(owner: String, repo: String, filters: Map<String, String>): List<Deployment>? {
    val deployments = github.getDeployments(owner, repo, filters)
    val response = deployments.execute() // TODO consider `.enqueue()`
    if (!response.isSuccessful) {
      throw RuntimeException("error: code=${response.code()} body=${response.errorBody()?.string()}")
    }
    return response.body()
  }

  fun createDeployment(owner: String, repo: String, deploymentRequest: DeploymentRequest): Deployment? {
    val createDeployment = github.createDeployment(owner, repo, deploymentRequest)
    val response = createDeployment.execute() // TODO consider `.enqueue()`
    if (!response.isSuccessful) {
      throw RuntimeException("error: code=${response.code()} body=${response.errorBody()?.string()}")
    }
    return response.body()
  }

  fun updateDeploymentStatus(owner: String, repo: String, deploymentId: Long, deploymentStatusRequest: DeploymentStatusRequest): DeploymentStatus? {
    val updateDeploymentStatus = github.updateDeploymentStatus(owner, repo, deploymentId, deploymentStatusRequest)
    val response = updateDeploymentStatus.execute() // TODO consider `.enqueue()`
    if (!response.isSuccessful) {
      throw RuntimeException("error: code=${response.code()} body=${response.errorBody()?.string()}")
    }
    return response.body()
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
