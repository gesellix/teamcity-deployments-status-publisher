package de.gesellix.teamcity.deployments.server;

import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.SFinishedBuild;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemEntry;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.BDDAssertions.then;

@Test
public abstract class AsyncPublisherTest extends DeploymentsStatusPublisherTest {

  DispatcherThrottles throttles;
  protected static final int TIMEOUT = 2000;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    throttles = new DispatcherThrottles();
    throttles.timeoutMillis = TIMEOUT;
    System.out.println("setup ->null serverMutex:" + throttles.serverMutex);
    throttles.serverMutex = null;
    throttles.processingStarted = new Semaphore(0);
    throttles.processingFinished = new Semaphore(0);
  }

  @Override
  protected String waitForRequest() throws InterruptedException {
    if (!throttles.processingFinished.tryAcquire(TIMEOUT, TimeUnit.MILLISECONDS)) {
      return null;
    }
    return super.waitForRequest();
  }

  public void test_publishing_is_async() throws Exception {
    SFinishedBuild build = createBuildInCurrentBranch(myBuildType, Status.NORMAL);
    int requestCount = enqueueRequests(EventToTest.FINISHED, build.getBuildId());

    throttles.serverMutex = new Semaphore(1);
    throttles.serverMutex.acquire();
    myPublisher.buildFinished(build, myRevision);
    throttles.serverMutex.release();

    awaitAllRequests(requestCount, 5, TimeUnit.SECONDS);
    then(getRequestAsString()).isNotNull().doesNotMatch(".*error.*")
      .matches(myExpectedRegExps.get(EventToTest.FINISHED));
  }

  public void should_report_publishing_failure() throws Exception {
    throttles.serverMutex = new Semaphore(1);
    throttles.serverMutex.acquire();
    // The HTTP client is supposed to wait for server for twice as less as we are waiting for its results
    // and the test HTTP server is supposed to wait for twice as much
    myPublisher.setConnectionTimeout(TIMEOUT / 2);
    myPublisher.buildFinished(createBuildInCurrentBranch(myBuildType, Status.NORMAL), myRevision);
    // The server mutex is never released, so the server does not respond until it times out
    then(waitForRequest()).isNull();
    Collection<SystemProblemEntry> problems = myProblemNotificationEngine.getProblems(myBuildType);
    then(problems.size()).isEqualTo(1);
    then(problems.iterator().next().getProblem().getDescription()).matches(String.format("Deployments Status Publisher.*%s.*timed?\\s?out.*", myPublisher.getId()));
    throttles.serverMutex.release();
  }

  public void should_publish_in_sequence() throws Exception {
    SFinishedBuild build = createBuildInCurrentBranch(myBuildType, Status.NORMAL);
    int requestCount = enqueueRequests(EventToTest.FINISHED, build.getBuildId());

    throttles.serverMutex = new Semaphore(1);
    throttles.serverMutex.acquire();

    myPublisher.setConnectionTimeout(throttles.timeoutMillis);
    myPublisher.buildFinished(build, myRevision);
    myPublisher.buildFinished(build, myRevision);

    then(throttles.processingStarted.tryAcquire(throttles.timeoutMillis, TimeUnit.MILLISECONDS)).isTrue(); // At least one request must arrive
    then(throttles.serverMutex.tryAcquire(throttles.timeoutMillis / 2, TimeUnit.MILLISECONDS)).isFalse(); // just wait till it all fails
    then(getNumberOfCurrentRequests()).as("the second request should not be sent until the first one is processed").isEqualTo(1);
    throttles.serverMutex.release();

    awaitAllRequests(requestCount, 5, TimeUnit.SECONDS);
  }
}
