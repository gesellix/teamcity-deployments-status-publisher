package de.gesellix.teamcity.deployments.server;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.version.ServerVersionHolder;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.then;
import static org.awaitility.Awaitility.await;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public abstract class HttpPublisherTest extends AsyncPublisherTest {

  protected String OWNER = "owner";
  protected String CORRECT_REPO = "project";
  protected String READ_ONLY_REPO = "readonly";

  protected RecordingDispatcher recordingDispatcher;

  @Override
  protected RecordedRequest getLastRequest() {
    if (recordingDispatcher.recordedRequests.isEmpty()) {
      return null;
    }
    return recordingDispatcher.recordedRequests.get(recordingDispatcher.recordedRequests.size() - 1);
  }

  protected String getServerUrl(String path) {
    return mockWebServer.url(path.isEmpty() ? "/" : path).toString();
  }

  @Override
  protected int getNumberOfCurrentRequests() {
    return recordingDispatcher.currentRequests.get();
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    recordingDispatcher = new RecordingDispatcher();

    mockWebServer = new MockWebServer();
    mockWebServer.setDispatcher(recordingDispatcher);
    mockWebServer.start();

    myVcsURL = getServerUrl("/" + OWNER + "/" + CORRECT_REPO);
    myReadOnlyVcsURL = getServerUrl("/" + OWNER + "/" + READ_ONLY_REPO);

    super.setUp();

    recordingDispatcher.dispatcherThrottles = throttles;
  }

  @Test(dataProvider = "provideRedirectCodes")
  public void test_redirection(int httpCode) throws Exception {
    SFinishedBuild build = createBuildInCurrentBranch(myBuildType, Status.NORMAL);
    mockWebServer.enqueue(new MockResponse().setResponseCode(httpCode).addHeader("Location", "/redirect-uri"));
    int requestCount = enqueueRequests(EventToTest.FINISHED, build.getBuildId());
    // redirection counts as an additional request
    requestCount++;
    myPublisher.buildFinished(build, myRevision);
    awaitAllRequests(requestCount, 5, TimeUnit.SECONDS);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.FINISHED));
  }

  @DataProvider(name = "provideRedirectCodes")
  public Object[][] provideRedirectCodes() {
    return new Object[][] {{301}, {302}, {307}};
  }

  public void test_user_agent_is_teamcity() throws Exception {
    SFinishedBuild build = createBuildInCurrentBranch(myBuildType, Status.NORMAL);
    int requestCount = enqueueRequests(EventToTest.FINISHED, build.getBuildId());
    myPublisher.buildFinished(build, myRevision);
    awaitAllRequests(requestCount, 5, TimeUnit.SECONDS);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.FINISHED));
    then(getLastRequest().getHeader("User-Agent")).isEqualTo("TeamCity Server " + ServerVersionHolder.getVersion().getDisplayVersion());
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    mockWebServer.shutdown();
  }

  @Override
  protected void awaitAllRequests(int requestCount, int timeout, TimeUnit timeoutUnit) {
    await().atMost(timeout, timeoutUnit).until(() -> mockWebServer.getRequestCount() == requestCount);
  }
}
