package de.gesellix.teamcity.deployments.server;

import com.intellij.openapi.util.io.StreamUtil;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.version.ServerVersionHolder;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.RequestLine;
import org.apache.http.config.SocketConfig;
import org.apache.http.impl.bootstrap.HttpServer;
import org.apache.http.impl.bootstrap.ServerBootstrap;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.InputStream;
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

  private HttpServer myHttpServer;
  private int myNumberOfCurrentRequests = 0;
  private String myLastRequest;
  private String myExpectedApiPath = "";
  private String myExpectedEndpointPrefix = "";
  private int myRespondWithRedirectCode = 0;
  private String myLastAgent;

  @Override
  protected String getRequestAsString() {
    return myLastRequest;
  }

  protected String getServerUrl() {
    return "http://localhost:" + myHttpServer.getLocalPort();
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

    final SocketConfig socketConfig = SocketConfig.custom().setSoTimeout(TIMEOUT * 2).build();
    ServerBootstrap bootstrap = ServerBootstrap.bootstrap().setSocketConfig(socketConfig).setServerInfo("TEST/1.1")
      .registerHandler("/*", (httpRequest, httpResponse, httpContext) -> {
        myLastAgent = httpRequest.getLastHeader("User-Agent").getValue();
        if (myRespondWithRedirectCode > 0) {
          setRedirectionResponse(httpRequest, httpResponse);
          return;
        }

        myNumberOfCurrentRequests++;
        myProcessingStarted.release(); // indicates that we are processing request
        try {
          if (null != myServerMutex && !myServerMutex.tryAcquire(TIMEOUT * 2, TimeUnit.MILLISECONDS)) {
            myNumberOfCurrentRequests--;
            return;
          }
        }
        catch (InterruptedException ex) {
          httpResponse.setStatusCode(500);
          myNumberOfCurrentRequests--;
          return;
        }
        myLastRequest = httpRequest.getRequestLine().toString();
        String requestData = null;

        if (httpRequest instanceof HttpEntityEnclosingRequest) {
          HttpEntity entity = ((HttpEntityEnclosingRequest) httpRequest).getEntity();
          InputStream is = entity.getContent();
          requestData = StreamUtil.readText(is);
          myLastRequest += "\tENTITY: " + requestData;
          httpResponse.setStatusCode(201);
        }
        else {
          httpResponse.setStatusCode(200);
        }
        if (!populateResponse(httpRequest, requestData, httpResponse)) {
          myLastRequest = "HTTP error: " + httpResponse.getStatusLine();
        }

        myNumberOfCurrentRequests--;
        myProcessingFinished.release();
      });

    myHttpServer = bootstrap.create();
    myHttpServer.start();
    myVcsURL = getServerUrl() + "/" + OWNER + "/" + CORRECT_REPO;
    myReadOnlyVcsURL = getServerUrl() + "/" + OWNER + "/" + READ_ONLY_REPO;
    super.setUp();
  }

  protected void setRedirectionResponse(final HttpRequest httpRequest, final HttpResponse httpResponse) {
    httpResponse.setStatusCode(307);
    httpResponse.setHeader("Location", httpRequest.getRequestLine().getUri());
    myRespondWithRedirectCode = 0;
  }

  @Test(dataProvider = "provideRedirectCodes")
  public void test_redirection(int httpCode) throws Exception {
    myRespondWithRedirectCode = httpCode;
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

  protected boolean populateResponse(HttpRequest httpRequest, String requestData, HttpResponse httpResponse) {
    RequestLine requestLine = httpRequest.getRequestLine();
    String method = requestLine.getMethod();
    String url = requestLine.getUri();
    if (method.equals("GET")) {
      return respondToGet(url, httpResponse);
    }
    else if (method.equals("POST")) {
      return respondToPost(url, requestData, httpRequest, httpResponse);
    }

    respondWithError(httpResponse, 405, String.format("Wrong method '%s'", method));
    return false;
  }

  protected abstract boolean respondToGet(String url, HttpResponse httpResponse);

  protected abstract boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse);

  protected boolean isUrlExpected(String url, HttpResponse httpResponse) {
    String expected = getExpectedApiPath() + getExpectedEndpointPrefix();
    if (!url.startsWith(expected)) {
      respondWithError(httpResponse, 404, String.format("Unexpected URL: '%s' expected: '%s'", url, expected));
      return false;
    }
    return true;
  }

  protected void respondWithError(HttpResponse httpResponse, int statusCode, String msg) {
    httpResponse.setStatusCode(statusCode);
    httpResponse.setReasonPhrase(msg);
  }

  @AfterMethod
  @Override
  protected void tearDown() throws Exception {
    super.tearDown();
    myHttpServer.stop();
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
