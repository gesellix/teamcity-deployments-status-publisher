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

import com.google.gson.Gson
import com.intellij.openapi.diagnostic.Logger
import de.gesellix.teamcity.deployments.server.PublisherException
import de.gesellix.teamcity.deployments.server.github.api.GitHubApi
import de.gesellix.teamcity.deployments.server.github.api.GitHubChangeState
import de.gesellix.teamcity.deployments.server.github.api.impl.data.CommitInfo
import de.gesellix.teamcity.deployments.server.github.api.impl.data.CommitStatus
import de.gesellix.teamcity.deployments.server.github.api.impl.data.IssueComment
import de.gesellix.teamcity.deployments.server.github.api.impl.data.PullRequestInfo
import de.gesellix.teamcity.deployments.server.github.api.impl.data.RepoInfo
import jetbrains.buildServer.util.FileUtil
import org.apache.http.HttpHeaders
import org.apache.http.HttpRequest
import org.apache.http.HttpResponse
import org.apache.http.StatusLine
import org.apache.http.auth.AuthenticationException
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.message.BasicHeader
import org.apache.http.util.EntityUtils
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.util.*
import java.util.regex.Pattern

/**
 * @author Eugene Petrenko (eugene.petrenko@gmail.com)
 * @author Tomaz Cerar
 * Date: 05.09.12 23:39
 */
abstract class GitHubApiImpl(
  private val client: HttpClientWrapper,
  private val urls: GitHubApiPaths
) : GitHubApi {

  private val gson = Gson()

  @Throws(PublisherException::class)
  override fun testConnection(
    repoOwner: String,
    repositoryName: String
  ) {
    val get = HttpGet(urls.getRepoInfo(repoOwner, repositoryName))
    val repoInfo: RepoInfo
    repoInfo = try {
      includeAuthentication(get)
      setDefaultHeaders(get)
      logRequest(get, null)
      processResponse(get, RepoInfo::class.java, true)
    } catch (ex: Throwable) {
      throw PublisherException(String.format("Error while retrieving %s/%s repository information", repoOwner, repositoryName), ex)
    } finally {
      get.abort()
    }
    if (null == repoInfo.name || null == repoInfo.permissions) {
      throw PublisherException(String.format("Repository %s/%s is inaccessible", repoOwner, repositoryName))
    }
    if (!repoInfo.permissions.push) {
      throw PublisherException(String.format("There is no push access to the repository %s/%s", repoOwner, repositoryName))
    }
  }

  @Throws(IOException::class)
  override fun readChangeStatus(
    repoOwner: String,
    repositoryName: String,
    hash: String
  ): String {
    val post = HttpGet(urls.getStatusUrl(repoOwner, repositoryName, hash))
    includeAuthentication(post)
    setDefaultHeaders(post)
    return try {
      logRequest(post, null)
      val execute = client.execute(post)
      if (execute.statusLine.statusCode != HttpURLConnection.HTTP_OK) {
        logFailedResponse(post, null, execute)
        throw IOException(getErrorMessage(execute.statusLine, null))
      }
      "TBD"
    } finally {
      post.abort()
    }
  }

  private fun setDefaultHeaders(request: HttpUriRequest) {
    request.setHeader(BasicHeader(HttpHeaders.ACCEPT_ENCODING, "UTF-8"))
    request.setHeader(BasicHeader(HttpHeaders.ACCEPT, "application/json"))
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
    val requestEntity = GSonEntity(gson, CommitStatus(status.state, targetUrl, description, context))
    val post = HttpPost(urls.getStatusUrl(repoOwner, repositoryName, hash))
    try {
      post.entity = requestEntity
      includeAuthentication(post)
      setDefaultHeaders(post)
      logRequest(post, requestEntity.text)
      val execute = client.execute(post)
      if (execute.statusLine.statusCode != HttpURLConnection.HTTP_CREATED) {
        logFailedResponse(post, requestEntity.text, execute)
        throw IOException(getErrorMessage(execute.statusLine, MSG_PROXY_OR_PERMISSIONS))
      }
    } finally {
      post.abort()
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

    //  /repos/:owner/:repo/pulls/:number
    val requestUrl = urls.getPullRequestInfo(repoOwner, repoName, pullRequestId)
    val get = HttpGet(requestUrl)
    includeAuthentication(get)
    setDefaultHeaders(get)
    val pullRequestInfo: PullRequestInfo = processResponse(get, PullRequestInfo::class.java, false)
    return pullRequestInfo.head?.sha
  }

  @Throws(IOException::class, PublisherException::class)
  override fun getCommitParents(repoOwner: String, repoName: String, hash: String): Collection<String> {
    val requestUrl = urls.getCommitInfo(repoOwner, repoName, hash)
    val get = HttpGet(requestUrl)
    val infos: CommitInfo = processResponse(get, CommitInfo::class.java, false)
    if (infos.parents != null) {
      val parents: MutableSet<String> = HashSet()
      for (p in infos.parents) {
        val sha: String? = p.sha
        if (sha != null) {
          parents.add(sha)
        }
      }
      return parents
    }
    return emptyList()
  }

  @Throws(IOException::class, PublisherException::class)
  private fun <T> processResponse(request: HttpUriRequest, clazz: Class<T>, logErrorsDebugOnly: Boolean): T {
    setDefaultHeaders(request)
    return try {
      logRequest(request, null)
      val execute = client.execute(request)
      if (execute.statusLine.statusCode != HttpURLConnection.HTTP_OK) {
        logFailedResponse(request, null, execute, logErrorsDebugOnly)
        throw IOException(getErrorMessage(execute.statusLine, MSG_PROXY_OR_PERMISSIONS))
      }
      val entity = execute.entity
      if (entity == null) {
        logFailedResponse(request, null, execute, logErrorsDebugOnly)
        throw IOException(getErrorMessage(execute.statusLine, "Empty response."))
      }
      try {
        val bos = ByteArrayOutputStream()
        entity.writeTo(bos)
        val json = bos.toString("utf-8")
        LOG.debug("Parsing json for " + request.uri.toString() + ": " + json)
        val result = gson.fromJson(json, clazz) ?: throw PublisherException("GitHub publisher fails to parse a response")
        result
      } finally {
        EntityUtils.consume(entity)
      }
    } finally {
      request.abort()
    }
  }

  @Throws(IOException::class)
  private fun includeAuthentication(request: HttpRequest) {
    try {
      setAuthentication(request)
    } catch (e: AuthenticationException) {
      throw IOException("Failed to set authentication for request. " + e.message, e)
    }
  }

  @Throws(AuthenticationException::class)
  protected abstract fun setAuthentication(request: HttpRequest)

  @Throws(IOException::class)
  private fun logFailedResponse(
    request: HttpUriRequest,
    requestEntity: String?,
    execute: HttpResponse,
    debugOnly: Boolean = false
  ) {
    var responseText = extractResponseEntity(execute)
    if (responseText == null) {
      responseText = "<none>"
    }
    var requestEntity = requestEntity
    if (requestEntity == null) {
      requestEntity = "<none>"
    }
    val logEntry = """Failed to complete query to GitHub with:
  requestURL: ${request.uri}
  requestMethod: ${request.method}
  requestEntity: $requestEntity
  response: ${execute.statusLine}
  responseEntity: $responseText"""
    if (debugOnly) {
      LOG.debug(logEntry)
    } else {
      LOG.warn(logEntry)
    }
  }

  private fun logRequest(
    request: HttpUriRequest,
    requestEntity: String?
  ) {
    if (!LOG.isDebugEnabled) return
    var requestEntity = requestEntity
    if (requestEntity == null) {
      requestEntity = "<none>"
    }
    LOG.debug(
      """Calling GitHub with:
  requestURL: ${request.uri}
  requestMethod: ${request.method}
  requestEntity: $requestEntity"""
    )
  }

  @Throws(IOException::class)
  private fun extractResponseEntity(execute: HttpResponse): String? {
    val responseEntity = execute.entity ?: return null
    return try {
      val dataSlice = ByteArray(256 * 1024) //limit buffer with 256K
      val content = responseEntity.content
      try {
        val sz = content.read(dataSlice, 0, dataSlice.size)
        String(dataSlice, 0, sz, Charsets.UTF_8)
      } finally {
        FileUtil.close(content)
      }
    } finally {
      EntityUtils.consume(responseEntity)
    }
  }

  @Throws(IOException::class)
  override fun postComment(
    ownerName: String,
    repoName: String,
    hash: String,
    comment: String
  ) {
    val requestUrl = urls.getAddCommentUrl(ownerName, repoName, hash)
    val requestEntity = GSonEntity(gson, IssueComment(comment))
    val post = HttpPost(requestUrl)
    try {
      post.entity = requestEntity
      includeAuthentication(post)
      setDefaultHeaders(post)
      logRequest(post, requestEntity.text)
      val execute = client.execute(post)
      if (execute.statusLine.statusCode != HttpURLConnection.HTTP_CREATED) {
        logFailedResponse(post, requestEntity.text, execute)
        throw IOException(getErrorMessage(execute.statusLine, null))
      }
    } finally {
      post.abort()
    }
  }

  companion object {

    private val LOG = Logger.getInstance(GitHubApiImpl::class.java.name)
    private val PULL_REQUEST_BRANCH = Pattern.compile("/?refs/pull/(\\d+)/(.*)")
    private const val MSG_PROXY_OR_PERMISSIONS = "Please check if the error is not returned by a proxy or caused by the lack of permissions."
    private fun getPullRequestId(
      repoName: String,
      branchName: String
    ): String? {
      val matcher = PULL_REQUEST_BRANCH.matcher(branchName)
      if (!matcher.matches()) {
        LOG.debug("Branch $branchName for repo $repoName does not look like pull request")
        return null
      }
      val pullRequestId = matcher.group(1)
      if (pullRequestId == null) {
        LOG.debug("Branch $branchName for repo $repoName does not contain pull request id")
        return null
      }
      return pullRequestId
    }

    private fun getErrorMessage(statusLine: StatusLine, additionalComment: String?): String {
      var err = ""
      if (null != additionalComment) {
        err = "$additionalComment "
      }
      return String.format("Failed to complete request to GitHub. %sStatus: %s", err, statusLine.toString())
    }
  }
}
