package de.gesellix.github.client

import com.squareup.moshi.Moshi
import de.gesellix.github.client.data.CommitCommentRequest
import de.gesellix.github.client.data.CommitStatusRequest
import de.gesellix.github.client.data.DeploymentRequest
import de.gesellix.github.client.data.DeploymentStatusRequest
import de.gesellix.github.client.data.DeploymentStatusState.in_progress
import okhttp3.logging.HttpLoggingInterceptor
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.net.HttpURLConnection

internal class GitHubClientTest {

  companion object {

    private var mockWebServer = MockWebServer()

    private lateinit var client: GitHubClient

    @BeforeAll
    @JvmStatic
    fun setup() {
      mockWebServer.start()
//      client = GitHubClient(token = GitHubClientTest::class.java.getResource("/gh-token.txt").readText())
      client = GitHubClient(mockWebServer.url("/").toString())
      client.loggingInterceptor.level = HttpLoggingInterceptor.Level.BODY
    }

    @AfterAll
    @JvmStatic
    fun teardown() {
      mockWebServer.shutdown()
    }
  }

  @Test
  fun test_get_repository() {
    val response = MockResponse()
      .setResponseCode(HttpURLConnection.HTTP_OK)
      .setBody(javaClass.getResource("/get_repository_response.json").readText())
    mockWebServer.enqueue(response)

    val repo = client.getRepository("gesellix", "deployment-tests")

    assertEquals("deployment-tests", repo?.name)
    assertTrue((repo?.permissions?.push ?: false))
  }

  @Test
  fun test_get_commit() {
    val response = MockResponse()
      .setResponseCode(HttpURLConnection.HTTP_OK)
      .setBody(javaClass.getResource("/get_commit_response.json").readText())
    mockWebServer.enqueue(response)

    val commit = client.getCommit("gesellix", "deployment-tests", "aa3331f82f4990c24ba02f6be8a142405801013f")

    assertEquals("aa3331f82f4990c24ba02f6be8a142405801013f", commit?.sha)
  }

  @Test
  fun test_add_commit_comment() {
    val response = MockResponse()
      .setResponseCode(HttpURLConnection.HTTP_OK)
      .setBody(javaClass.getResource("/add_commit_comment_response.json").readText())
    mockWebServer.enqueue(response)

    val commitCommentRequest = CommitCommentRequest(body = "my comment")
    val commitComment = client.addCommitComment("gesellix", "deployment-tests", "aa3331f82f4990c24ba02f6be8a142405801013f", commitCommentRequest)

    assertEquals(43985286, commitComment?.id)
  }

  @Test
  fun test_get_commit_statuses_summary() {
    val response = MockResponse()
      .setResponseCode(HttpURLConnection.HTTP_OK)
      .setBody(javaClass.getResource("/get_commit_statuses_summary_response.json").readText())
    mockWebServer.enqueue(response)

    val summary = client.getCommitStatusesSummary("gesellix", "deployment-tests", "9bd5374e375e3416ff981122703a0e4079055fea")

    assertEquals("pending", summary?.state)
  }

  @Test
  fun test_get_commit_statuses() {
    val response = MockResponse()
      .setResponseCode(HttpURLConnection.HTTP_OK)
      .setBody(javaClass.getResource("/get_commit_statuses_response.json").readText())
    mockWebServer.enqueue(response)

    val statuses = client.getCommitStatuses("gesellix", "deployment-tests", "9bd5374e375e3416ff981122703a0e4079055fea")

    assertTrue(statuses?.isNotEmpty() ?: false)
  }

  @Test
  fun test_update_commit_status() {
    val response = MockResponse()
      .setResponseCode(HttpURLConnection.HTTP_OK)
      .setBody(javaClass.getResource("/update_commit_status_response.json").readText())
    mockWebServer.enqueue(response)
    val req = CommitStatusRequest(state = "pending")
    val status = client.updateCommitStatus("gesellix", "deployment-tests", "9bd5374e375e3416ff981122703a0e4079055fea", req)

    assertEquals("pending", status?.state)
  }

  @Test
  fun test_get_pull_request() {
    val response = MockResponse()
      .setResponseCode(HttpURLConnection.HTTP_OK)
      .setBody(javaClass.getResource("/get_pull_request_response.json").readText())
    mockWebServer.enqueue(response)

    val pullRequest = client.getPullRequest("gesellix", "deployment-tests", 1)

    assertEquals("https://api.github.com/repos/gesellix/deployment-tests/pulls/1", pullRequest?.url)
    assertEquals("aa3331f82f4990c24ba02f6be8a142405801013f", pullRequest?.head?.sha)
  }

  @Test
  fun test_get_deployments() {
    val response = MockResponse()
      .setResponseCode(HttpURLConnection.HTTP_OK)
      .setBody(javaClass.getResource("/get_deployments_response.json").readText())
    mockWebServer.enqueue(response)

    val deployments = client.getDeployments(
      "gesellix",
      "deployment-tests",
      mapOf(
        "sha" to "9bd5374e375e3416ff981122703a0e4079055fea",
        "environment" to "production"
      )
    )

    assertEquals(5, deployments?.size)
  }

  @Test
  fun test_create_deployment() {
    val response = MockResponse()
      .setResponseCode(HttpURLConnection.HTTP_OK)
      .setBody(javaClass.getResource("/create_deployment_response.json").readText())
    mockWebServer.enqueue(response)
    val payload = Moshi.Builder().build().adapter(Map::class.java).toJson(mapOf("foo" to "bar"))
    val deploymentRequest = DeploymentRequest("9bd5374e375e3416ff981122703a0e4079055fea").apply {
      this.environment = "test"
      this.payload = payload
    }

    val deployment = client.createDeployment("gesellix", "deployment-tests", deploymentRequest)

    assertEquals(289108418, deployment?.id)
    assertEquals("2020-11-10T22:38:11Z", deployment?.created_at)
    assertEquals(payload, deployment?.payload)
  }

  @Test
  fun test_update_deployment_status() {
    val response = MockResponse()
      .setResponseCode(HttpURLConnection.HTTP_OK)
      .setBody(javaClass.getResource("/update_deployment_status_response.json").readText())
    mockWebServer.enqueue(response)
    val deploymentStatusRequest = DeploymentStatusRequest(in_progress)
    deploymentStatusRequest.environment = "test"

    val deploymentStatus = client.updateDeploymentStatus("gesellix", "deployment-tests", 765432198, deploymentStatusRequest)

    assertEquals(425956912, deploymentStatus?.id)
    assertEquals("2020-11-08T16:15:48Z", deploymentStatus?.created_at)
  }
}
