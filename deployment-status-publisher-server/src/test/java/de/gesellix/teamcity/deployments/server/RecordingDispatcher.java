package de.gesellix.teamcity.deployments.server;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.QueueDispatcher;
import okhttp3.mockwebserver.RecordedRequest;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RecordingDispatcher extends QueueDispatcher {

  DispatcherThrottles dispatcherThrottles;

  AtomicInteger currentRequests = new AtomicInteger();

  List<RecordedRequest> recordedRequests = new ArrayList<>();

  @NotNull
  @Override
  public MockResponse dispatch(@NotNull RecordedRequest request) throws InterruptedException {
    recordedRequests.add(request);

    currentRequests.getAndIncrement();
    dispatcherThrottles.processingStarted.release(); // indicates that we are processing request

    try {
      if (null != dispatcherThrottles.serverMutex && !dispatcherThrottles.serverMutex.tryAcquire(dispatcherThrottles.timeoutMillis * 2, TimeUnit.MILLISECONDS)) {
        currentRequests.getAndDecrement();
        return new MockResponse();
      }
    }
    catch (InterruptedException ex) {
      currentRequests.getAndDecrement();
      return new MockResponse().setResponseCode(500);
    }

    MockResponse response = super.dispatch(request);

    currentRequests.getAndDecrement();
    dispatcherThrottles.processingFinished.release();

    return response;
  }
}
