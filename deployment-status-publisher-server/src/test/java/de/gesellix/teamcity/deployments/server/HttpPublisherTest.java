package de.gesellix.teamcity.deployments.server;

import com.intellij.openapi.util.io.StreamUtil;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.version.ServerVersionHolder;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public abstract class HttpPublisherTest extends AsyncPublisherTest {

  protected String OWNER = "owner";
  protected String CORRECT_REPO = "project";
  protected String READ_ONLY_REPO = "readonly";

  private MockWebServer mockWebServer;
  private int myNumberOfCurrentRequests = 0;
  private String myLastRequest;
  private String myExpectedApiPath = "";
  private String myExpectedEndpointPrefix = "";
  private String myLastAgent;

  @Override
  protected String getRequestAsString() {
    return myLastRequest;
  }

  protected String getServerUrl(String path) {
    return mockWebServer.url(path.isEmpty() ? "/" : path).toString();
  }

  @Override
  protected int getNumberOfCurrentRequests() {
    return myNumberOfCurrentRequests;
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    myLastRequest = null;
    myLastAgent = null;

    mockWebServer = new MockWebServer();
    mockWebServer.setDispatcher(new QueueDispatcher() {
      @NotNull
      @Override
      public MockResponse dispatch(@NotNull RecordedRequest recordedRequest) {
        myLastAgent = recordedRequest.getHeader("User-Agent");

        myNumberOfCurrentRequests++;
        myProcessingStarted.release(); // indicates that we are processing request

        try {
          if (null != myServerMutex && !myServerMutex.tryAcquire(TIMEOUT * 2, TimeUnit.MILLISECONDS)) {
            myNumberOfCurrentRequests--;
            return new MockResponse();
          }
        }
        catch (InterruptedException ex) {
          myNumberOfCurrentRequests--;
          return new MockResponse().setResponseCode(500);
        }

        myLastRequest = recordedRequest.getRequestLine();

//        MockResponse response = super.dispatch(recordedRequest);
        MockResponse response = new MockResponse().setResponseCode(200);
        String requestData = null;
        if (recordedRequest.getBodySize() != 0) {
          try {
            requestData = StreamUtil.readText(recordedRequest.getBody().inputStream());
            myLastRequest += "\tENTITY: " + requestData;
            response.setResponseCode(201);
          }
          catch (IOException e) {
            //TODO?
          }
        }
        if (!populateResponse(recordedRequest, requestData, response)) {
          myLastRequest = "HTTP error: " + response.getStatus();
        }

        myNumberOfCurrentRequests--;
        myProcessingFinished.release();
        return response;
      }
    });
    mockWebServer.start();

    myVcsURL = getServerUrl("/" + OWNER + "/" + CORRECT_REPO);
    myReadOnlyVcsURL = getServerUrl("/" + OWNER + "/" + READ_ONLY_REPO);
    super.setUp();
  }

  @Test(dataProvider = "provideRedirectCodes")
  public void test_redirection(int httpCode) throws Exception {
    mockWebServer.enqueue(new MockResponse().setResponseCode(httpCode).addHeader("Location", "redirect-uri"));
    mockWebServer.enqueue(new MockResponse().setResponseCode(200));
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    then(waitForRequest()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.FINISHED));
  }

  @DataProvider(name = "provideRedirectCodes")
  public Object[][] provideRedirectCodes() {
    return new Object[][] {{301}, {302}, {307}};
  }

  public void test_user_agent_is_teamcity() throws Exception {
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    then(waitForRequest()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.FINISHED));
    then(myLastAgent).isEqualTo("TeamCity Server " + ServerVersionHolder.getVersion().getDisplayVersion());
  }

  protected boolean populateResponse(RecordedRequest httpRequest, String requestData, MockResponse httpResponse) {
    String method = httpRequest.getMethod();
    String url = httpRequest.getPath();
    if (method.equals("GET")) {
      return respondToGet(url, httpResponse);
    }
    else if (method.equals("POST")) {
      return respondToPost(url, requestData, httpRequest, httpResponse);
    }

    respondWithError(httpResponse, 405, String.format("Wrong method '%s'", method));
    return false;
  }

  protected abstract boolean respondToGet(String url, MockResponse httpResponse);

  protected abstract boolean respondToPost(String url, String requestData, final RecordedRequest httpRequest, MockResponse httpResponse);

  protected boolean isUrlExpected(String url, MockResponse httpResponse) {
    String expected = getExpectedApiPath() + getExpectedEndpointPrefix();
    if (!url.startsWith(expected)) {
      respondWithError(httpResponse, 404, String.format("Unexpected URL: '%s' expected: '%s'", url, expected));
      return false;
    }
    return true;
  }

  protected void respondWithError(MockResponse httpResponse, int statusCode, String msg) {
    httpResponse.setResponseCode(statusCode);
//    httpResponse.setReasonPhrase(msg);
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    mockWebServer.shutdown();
  }

  protected void setExpectedApiPath(String path) {
    myExpectedApiPath = path;
  }

  protected String getExpectedApiPath() {
    return myExpectedApiPath;
  }

  protected void setExpectedEndpointPrefix(String prefix) {
    myExpectedEndpointPrefix = prefix;
  }

  protected String getExpectedEndpointPrefix() {
    return myExpectedEndpointPrefix;
  }
}
