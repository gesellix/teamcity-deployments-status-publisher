package de.gesellix.teamcity.deployments.server;

import com.intellij.openapi.util.io.StreamUtil;
import com.squareup.moshi.Moshi;
import jetbrains.buildServer.BuildProblemData;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.executors.ExecutorServices;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.executors.SimpleExecutorServices;
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager;
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemNotificationEngine;
import jetbrains.buildServer.users.SUser;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.VcsRootInstance;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public abstract class DeploymentsStatusPublisherTest extends BaseServerTestCase {

  protected static final String REVISION = "314159";
  protected static final int DEPLOYMENT_ID = 271828;
  protected static final String USER = "MyUser";
  protected static final String COMMENT = "MyComment";
  protected static final String PROBLEM_DESCR = "Problem description";
  protected static final String FEATURE_ID = "MY_FEATURE_ID";
  protected static final String BT_NAME_2BE_ESCAPED = "Name with \\ and \"";
  protected static final String BT_NAME_ESCAPED_REGEXP = BT_NAME_2BE_ESCAPED.replace("\\", "\\\\\\\\").replace("\"", "\\\\\\\"");

  protected DeploymentsStatusPublisher myPublisher;
  protected DeploymentsStatusPublisherSettings myPublisherSettings;
  protected DeploymentsStatusPublisherProblems myProblems;
  protected Map<EventToTest, String> myExpectedRegExps = new HashMap<>();
  protected SimpleExecutorServices myExecServices;
  protected String myVcsURL = "http://localhost/defaultvcs";
  protected String myReadOnlyVcsURL = "http://localhost/owner/readonly";
  protected SVcsRoot myVcsRoot;
  protected SystemProblemNotificationEngine myProblemNotificationEngine;
  protected String myBranch;
  protected BuildRevision myRevision;
  protected SUser myUser;
  protected OAuthConnectionsManager myOAuthConnectionsManager;
  protected OAuthTokensStorage myOAuthTokenStorage;

  protected MockWebServer mockWebServer;
  protected Moshi moshi = new Moshi.Builder().build();

  protected enum EventToTest {
    QUEUED(DeploymentsStatusPublisher.Event.QUEUED),
    REMOVED(DeploymentsStatusPublisher.Event.REMOVED_FROM_QUEUE),
    STARTED(DeploymentsStatusPublisher.Event.STARTED),
    FINISHED(DeploymentsStatusPublisher.Event.FINISHED),
    FAILED(DeploymentsStatusPublisher.Event.FINISHED),
    COMMENTED_SUCCESS(DeploymentsStatusPublisher.Event.COMMENTED),
    COMMENTED_FAILED(DeploymentsStatusPublisher.Event.COMMENTED),
    COMMENTED_INPROGRESS(DeploymentsStatusPublisher.Event.COMMENTED),
    COMMENTED_INPROGRESS_FAILED(DeploymentsStatusPublisher.Event.COMMENTED),
    INTERRUPTED(DeploymentsStatusPublisher.Event.INTERRUPTED),
    FAILURE_DETECTED(DeploymentsStatusPublisher.Event.FAILURE_DETECTED),
    MARKED_SUCCESSFUL(DeploymentsStatusPublisher.Event.MARKED_AS_SUCCESSFUL),
    MARKED_RUNNING_SUCCESSFUL(DeploymentsStatusPublisher.Event.MARKED_AS_SUCCESSFUL),
    TEST_CONNECTION(null),
    TEST_CONNECTION_READ_ONLY_REPO(null),
    PAYLOAD_ESCAPED(DeploymentsStatusPublisher.Event.FINISHED);

    private final DeploymentsStatusPublisher.Event myEvent;

    EventToTest(DeploymentsStatusPublisher.Event event) {
      myEvent = event;
    }

    public DeploymentsStatusPublisher.Event getEvent() {
      return myEvent;
    }
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myVcsRoot = myFixture.addVcsRoot("jetbrains.git", "", myBuildType);
    myVcsRoot.setProperties(Collections.singletonMap("url", myVcsURL));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myUser = myFixture.createUserAccount(USER);
    myExecServices = myFixture.getSingletonService(SimpleExecutorServices.class);
    myExecServices.start();
    myProblemNotificationEngine = myFixture.getSingletonService(SystemProblemNotificationEngine.class);
    myProblems = new DeploymentsStatusPublisherProblems(myProblemNotificationEngine);
    myBranch = null;
    myOAuthConnectionsManager = new OAuthConnectionsManager(myServer);
    myOAuthTokenStorage = new OAuthTokensStorage(myFixture.getServerPaths(), myFixture.getSingletonService(ExecutorServices.class));
  }

  public void test_testConnection() throws Exception {
    if (!myPublisherSettings.isTestConnectionSupported()) {
      return;
    }
    int requestCount = enqueueRequests(EventToTest.TEST_CONNECTION, -1);
    myPublisherSettings.testConnection(myBuildType, myVcsRoot, getPublisherParams());
    awaitAllRequests(requestCount, 5, TimeUnit.SECONDS);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.TEST_CONNECTION));
  }

  public void test_testConnection_fails_on_readonly() {
    int requestCount = enqueueRequests(EventToTest.TEST_CONNECTION_READ_ONLY_REPO, -1);
    test_testConnection_failure(myReadOnlyVcsURL, getPublisherParams());
    awaitAllRequests(requestCount, 5, TimeUnit.SECONDS);
  }

  public void test_testConnection_fails_on_bad_repo_url() {
    test_testConnection_failure("http://localhost/nothing", getPublisherParams());
  }

  public void test_testConnection_fails_on_missing_target() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(404));
    test_testConnection_failure("http://localhost/nouser/norepo", getPublisherParams());
  }

  protected void test_testConnection_failure(String repoURL, Map<String, String> params) {
    if (!myPublisherSettings.isTestConnectionSupported()) {
      return;
    }
    myVcsRoot.setProperties(Collections.singletonMap("url", repoURL));
    try {
      myPublisherSettings.testConnection(myBuildType, myVcsRoot, params);
      fail("Connection testing failure must throw PublishError exception");
    }
    catch (PublisherException ex) {
      // success
    }
  }

  protected abstract Map<String, String> getPublisherParams();

  public void test_buildQueued() throws Exception {
    if (!isToBeTested(EventToTest.QUEUED)) { return; }
    myPublisher.buildQueued(myBuildType.addToQueue(""), myRevision);
    then(waitForRequest()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.QUEUED));
  }

  public void test_buildRemovedFromQueue() throws Exception {
    if (!isToBeTested(EventToTest.REMOVED)) { return; }
    myPublisher.buildRemovedFromQueue(myBuildType.addToQueue(""), myRevision, myUser, COMMENT);
    then(waitForRequest()).isNotNull().matches(myExpectedRegExps.get(EventToTest.REMOVED));
  }

  public void test_buildStarted() throws Exception {
    if (!isToBeTested(EventToTest.STARTED)) {
      return;
    }
    SRunningBuild build = startBuildInCurrentBranch(myBuildType);
    int requestCount = enqueueRequests(EventToTest.STARTED, build.getBuildId());
    myPublisher.buildStarted(build, myRevision);
    awaitAllRequests(requestCount, 5, TimeUnit.SECONDS);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.STARTED));
  }

  public void test_buildFinished_Successfully() throws Exception {
    if (!isToBeTested(EventToTest.FINISHED)) {
      return;
    }
    SFinishedBuild build = createBuildInCurrentBranch(myBuildType, Status.NORMAL);
    int requestCount = enqueueRequests(EventToTest.FINISHED, build.getBuildId());
    myPublisher.buildFinished(build, myRevision);
    awaitAllRequests(requestCount, 5, TimeUnit.SECONDS);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.FINISHED));
  }

  // the implementation must return the number of publishing requests currently being processed by the mock server
  protected abstract int getNumberOfCurrentRequests();

  public void test_buildFinished_Failed() throws Exception {
    if (!isToBeTested(EventToTest.FAILED)) { return; }
    SFinishedBuild build = createBuildInCurrentBranch(myBuildType, Status.FAILURE);
    int requestCount = enqueueRequests(EventToTest.FAILED, build.getBuildId());
    myPublisher.buildFinished(build, myRevision);
    awaitAllRequests(requestCount, 5, TimeUnit.SECONDS);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.FAILED));
  }

  public void test_buildCommented_Success() throws Exception {
    if (!isToBeTested(EventToTest.COMMENTED_SUCCESS)) { return; }
    myPublisher.buildCommented(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision, myUser, COMMENT, false);
    then(waitForRequest()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.COMMENTED_SUCCESS));
  }

  public void test_buildCommented_Failed() throws Exception {
    if (!isToBeTested(EventToTest.COMMENTED_FAILED)) { return; }
    myPublisher.buildCommented(createBuildInCurrentBranch(myBuildType, Status.FAILURE), myRevision, myUser, COMMENT, false);
    then(waitForRequest()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.COMMENTED_FAILED));
  }

  public void test_buildCommented_InProgress() throws Exception {
    if (!isToBeTested(EventToTest.COMMENTED_INPROGRESS)) { return; }
    myPublisher.buildCommented(startBuildInCurrentBranch(myBuildType), myRevision, myUser, COMMENT, true);
    then(waitForRequest()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.COMMENTED_INPROGRESS));
  }

  public void test_buildCommented_InProgress_Failed() throws Exception {
    if (!isToBeTested(EventToTest.COMMENTED_INPROGRESS_FAILED)) { return; }
    SRunningBuild runningBuild = startBuildInCurrentBranch(myBuildType);
    runningBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildCommented(runningBuild, myRevision, myUser, COMMENT, true);
    then(waitForRequest()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.COMMENTED_INPROGRESS_FAILED));
  }

  public void test_buildInterrupted() throws Exception {
    if (!isToBeTested(EventToTest.INTERRUPTED)) {
      return;
    }
    SFinishedBuild build = createBuildInCurrentBranch(myBuildType, Status.NORMAL);
    int requestCount = enqueueRequests(EventToTest.INTERRUPTED, build.getBuildId());
    build.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildInterrupted(build, myRevision);
    awaitAllRequests(requestCount, 5, TimeUnit.SECONDS);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.INTERRUPTED));
  }

  public void test_buildFailureDetected() throws Exception {
    if (!isToBeTested(EventToTest.FAILURE_DETECTED)) { return; }
    SRunningBuild runningBuild = startBuildInCurrentBranch(myBuildType);
    runningBuild.addBuildProblem(BuildProblemData.createBuildProblem("problem", "type", PROBLEM_DESCR));
    myPublisher.buildFailureDetected(runningBuild, myRevision);
    then(waitForRequest()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.FAILURE_DETECTED));
  }

  public void test_buildMarkedAsSuccessful() throws Exception {
    if (!isToBeTested(EventToTest.MARKED_SUCCESSFUL)) {
      return;
    }
    SFinishedBuild build1 = createBuildInCurrentBranch(myBuildType, Status.FAILURE);
    int requestCount = enqueueRequests(EventToTest.FINISHED, build1.getBuildId());
    myPublisher.buildFinished(build1, myRevision);
    SFinishedBuild build2 = createBuildInCurrentBranch(myBuildType, Status.NORMAL);
    requestCount += enqueueRequests(EventToTest.MARKED_SUCCESSFUL, build2.getBuildId());
    myPublisher.buildMarkedAsSuccessful(build2, myRevision, false);

    awaitAllRequests(requestCount, 5, TimeUnit.SECONDS);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.MARKED_SUCCESSFUL));
  }

  public void test_buildMarkedAsSuccessful_WhileRunning() throws Exception {
    if (!isToBeTested(EventToTest.MARKED_RUNNING_SUCCESSFUL)) {
      return;
    }
    SRunningBuild build = startBuildInCurrentBranch(myBuildType);
    int requestCount = enqueueRequests(EventToTest.MARKED_RUNNING_SUCCESSFUL, build.getBuildId());
    myPublisher.buildMarkedAsSuccessful(build, myRevision, true);
    awaitAllRequests(requestCount, 5, TimeUnit.SECONDS);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.MARKED_RUNNING_SUCCESSFUL));
  }

  public void ensure_payload_escaped() throws Exception {
    if (!isToBeTested(EventToTest.PAYLOAD_ESCAPED)) {
      return;
    }
    myBuildType.setName(BT_NAME_2BE_ESCAPED);
    SFinishedBuild build = createBuildInCurrentBranch(myBuildType, Status.FAILURE);
    int requestCount = enqueueRequests(EventToTest.PAYLOAD_ESCAPED, build.getBuildId());
    myPublisher.buildFinished(build, myRevision);
    awaitAllRequests(requestCount, 5, TimeUnit.SECONDS);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.PAYLOAD_ESCAPED));
  }

  private boolean isToBeTested(@NotNull EventToTest eventType) {
    DeploymentsStatusPublisher.Event event = eventType.getEvent();
    if (null != event && !myPublisher.isEventSupported(event)) { return false; }
    then(myExpectedRegExps.containsKey(eventType))
      .as(String.format("Event '%s' must either be tested or explicitly declared as not to be tested.", eventType.toString()))
      .isTrue();
    String regExp = myExpectedRegExps.get(eventType);
    boolean toBeTested = null != regExp;
    then(null == event || toBeTested)
      .as(String.format("Event '%s' is supported by the publisher, but not tested", eventType.toString()))
      .isTrue();
    return toBeTested;
  }

  protected int enqueueRequests(EventToTest eventToTest, long buildId) {
    return 0;
  }

  protected void awaitAllRequests(int requestCount, int timeout, TimeUnit timeoutUnit) {
  }

  protected String waitForRequest() throws InterruptedException {
    return getRequestAsString();
  }

  protected abstract RecordedRequest getLastRequest();

  protected String getRequestAsString() {
    RecordedRequest lastRequest = getLastRequest();
    if (lastRequest == null) {
      return null;
    }
    String requestAsString = lastRequest.getRequestLine();
    if (lastRequest.getBodySize() > 0) {
      try {
        requestAsString += "\tENTITY: " + StreamUtil.readText(lastRequest.getBody().inputStream());
      }
      catch (IOException e) {
        // ignore silently
      }
    }
    return requestAsString;
  }

  protected SRunningBuild startBuildInCurrentBranch(SBuildType buildType) {
    return null == myBranch ? startBuild(buildType) : startBuildInBranch(buildType, myBranch);
  }

  protected SFinishedBuild createBuildInCurrentBranch(SBuildType buildType, Status status) {
    return null == myBranch ? createBuild(buildType, status) : createBuildInBranch(buildType, myBranch, status);
  }
}
