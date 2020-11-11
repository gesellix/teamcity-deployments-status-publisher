package de.gesellix.teamcity.deployments.server;

import java.util.concurrent.Semaphore;

public class DispatcherThrottles {

  int timeoutMillis = 2000;

  Semaphore
    serverMutex, // released if the test wants the server to finish processing a request
    processingStarted, // released by the server when it has started processing a request
    processingFinished; // released by the server to indicate to the test client that it can check the request data
}
