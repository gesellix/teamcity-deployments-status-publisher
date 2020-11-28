package de.gesellix.teamcity.deployments.server.github;

import de.gesellix.github.client.data.Deployment;
import de.gesellix.github.client.data.DeploymentStatus;
import de.gesellix.github.client.data.DeploymentStatusState;
import de.gesellix.github.client.data.Permissions;
import de.gesellix.github.client.data.Repository;
import de.gesellix.teamcity.deployments.server.HttpPublisherTest;
import de.gesellix.teamcity.deployments.server.MockPluginDescriptor;
import de.gesellix.teamcity.deployments.server.PublisherException;
import de.gesellix.teamcity.deployments.server.github.api.impl.GitHubApiFactoryImpl;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.VcsRootInstance;
import okhttp3.mockwebserver.MockResponse;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static de.gesellix.teamcity.deployments.server.ConstantsKt.GITHUB_SERVER;
import static de.gesellix.teamcity.deployments.server.ConstantsKt.GITHUB_TOKEN;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public class GitHubPublisherTest extends HttpPublisherTest {

  private DeploymentsStatusUpdater myDeploymentsStatusUpdater;

  public GitHubPublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.REMOVED, null);  // not to be tested
    myExpectedRegExps.put(EventToTest.STARTED, String.format(".*/repos/owner/project/deployments.*ENTITY:.*build started.*"));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format(".*/repos/owner/project/deployments/%s/statuses.*ENTITY:.*success.*build finished.*", DEPLOYMENT_ID));
    myExpectedRegExps.put(EventToTest.FAILED, String.format(".*/repos/owner/project/deployments/%s/statuses.*ENTITY:.*failure.*build failed.*", DEPLOYMENT_ID));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format(".*/repos/owner/project/deployments/%s/statuses.*ENTITY:.*failure.*", DEPLOYMENT_ID));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*/repos/owner/project/deployments/%s/statuses.*ENTITY:.*success.*build finished.*", DEPLOYMENT_ID)); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*/repos/owner/project/deployments.*ENTITY:.*build started.*")); // not to be tested
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, String.format(".*/repos/owner/project/deployments/%s/statuses.*ENTITY:.*failure.*build failed.*%s.*", DEPLOYMENT_ID, BT_NAME_ESCAPED_REGEXP));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, String.format(".*/repos/owner/project .*")); // not to be tested
  }

  public void test_buildFinishedSuccessfully_server_url_with_subdir() throws Exception {
    Map<String, String> params = getPublisherParams();
    params.put(GITHUB_SERVER, getServerUrl("/subdir/api/v3/"));
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/subdir/owner/project"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myPublisher = new GitHubPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myDeploymentsStatusUpdater, params, myProblems);
    test_buildFinished_Successfully();
  }

  public void should_fail_with_error_on_wrong_vcs_url() {
    myVcsRoot.setProperties(Collections.singletonMap("url", "wrong://url.com"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    BuildRevision revision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    try {
      myPublisher.buildFinished(myFixture.createBuild(myBuildType, Status.NORMAL), revision);
      fail("PublishError exception expected");
    }
    catch (PublisherException ex) {
      then(ex.getMessage()).matches("Cannot parse.*" + myVcsRoot.getName() + ".*");
    }
  }

  @BeforeMethod
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    new TeamCityProperties() {{
      setModel(new BasePropertiesModel() {
        @NotNull
        @Override
        public Map<String, String> getUserDefinedProperties() {
          return Collections.singletonMap("teamcity.github.http.timeout", String.valueOf(TIMEOUT / 2));
        }
      });
    }};

    Map<String, String> params = getPublisherParams();

    myDeploymentsStatusUpdater = new DeploymentsStatusUpdater(myExecServices, new GitHubApiFactoryImpl(), myWebLinks);

    myPublisherSettings = new GitHubSettings(myDeploymentsStatusUpdater, new MockPluginDescriptor(), myProblems,
                                             myOAuthConnectionsManager, myOAuthTokenStorage, myFixture.getSecurityContext());
    myPublisher = new GitHubPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myDeploymentsStatusUpdater, params, myProblems);
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return new HashMap<String, String>() {{
      put(GITHUB_TOKEN, "token");
      put(GITHUB_SERVER, getServerUrl("/"));
    }};
  }

  @Override
  protected int enqueueRequests(EventToTest eventToTest, long buildId) {
    if (eventToTest == EventToTest.TEST_CONNECTION) {
      Repository repoInfo = new Repository(-1, CORRECT_REPO, new Permissions());
      repoInfo.getPermissions().setPull(true);
      repoInfo.getPermissions().setPush(true);
      mockWebServer.enqueue(new MockResponse().setResponseCode(202).setBody(moshi.adapter(Repository.class).toJson(repoInfo)));
      return 1;
    }
    if (eventToTest == EventToTest.TEST_CONNECTION_READ_ONLY_REPO) {
      Repository repoInfo = new Repository(-1, READ_ONLY_REPO, new Permissions());
      repoInfo.getPermissions().setPull(true);
      repoInfo.getPermissions().setPush(false);
      mockWebServer.enqueue(new MockResponse().setResponseCode(202).setBody(moshi.adapter(Repository.class).toJson(repoInfo)));
      return 1;
    }
    if (eventToTest == EventToTest.STARTED) {
      List<Deployment> deployments = new ArrayList<>();
      Deployment deployment = new Deployment(DEPLOYMENT_ID);
      deployment.setPayload(moshi.adapter(Map.class).toJson(Collections.singletonMap("buildIdAsString", "" + buildId)));
      deployments.add(deployment);
      mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(moshi.adapter(List.class).toJson(deployments)));
      mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody(moshi.adapter(DeploymentStatus.class).toJson(new DeploymentStatus(4141, "123", DeploymentStatusState.in_progress))));
      return 2;
    }
    if (eventToTest == EventToTest.INTERRUPTED) {
      List<Deployment> deployments = new ArrayList<>();
      Deployment deployment = new Deployment(DEPLOYMENT_ID);
      deployment.setPayload(moshi.adapter(Map.class).toJson(Collections.singletonMap("buildIdAsString", "" + buildId)));
      deployments.add(deployment);
      mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(moshi.adapter(List.class).toJson(deployments)));
      mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody(moshi.adapter(DeploymentStatus.class).toJson(new DeploymentStatus(4141, "123", DeploymentStatusState.failure))));
      return 2;
    }
    if (eventToTest == EventToTest.MARKED_RUNNING_SUCCESSFUL) {
      List<Deployment> deployments = new ArrayList<>();
      Deployment deployment = new Deployment(DEPLOYMENT_ID);
      deployment.setPayload(moshi.adapter(Map.class).toJson(Collections.singletonMap("buildIdAsString", "" + buildId)));
      deployments.add(deployment);
      mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(moshi.adapter(List.class).toJson(deployments)));
      mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody(moshi.adapter(DeploymentStatus.class).toJson(new DeploymentStatus(4141, "123", DeploymentStatusState.in_progress))));
      return 2;
    }
    if (eventToTest == EventToTest.MARKED_SUCCESSFUL) {
      List<Deployment> deployments = new ArrayList<>();
      Deployment deployment = new Deployment(DEPLOYMENT_ID);
      deployment.setPayload(moshi.adapter(Map.class).toJson(Collections.singletonMap("buildIdAsString", "" + buildId)));
      deployments.add(deployment);
      mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(moshi.adapter(List.class).toJson(deployments)));
      mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody(moshi.adapter(DeploymentStatus.class).toJson(new DeploymentStatus(4141, "123", DeploymentStatusState.failure))));
      return 2;
    }
    if (eventToTest == EventToTest.PAYLOAD_ESCAPED) {
      List<Deployment> deployments = new ArrayList<>();
      Deployment deployment = new Deployment(DEPLOYMENT_ID);
      deployment.setPayload(moshi.adapter(Map.class).toJson(Collections.singletonMap("buildIdAsString", "" + buildId)));
      deployments.add(deployment);
      mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(moshi.adapter(List.class).toJson(deployments)));
      mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody(moshi.adapter(DeploymentStatus.class).toJson(new DeploymentStatus(4141, "123", DeploymentStatusState.failure))));
      return 2;
    }
    if (eventToTest == EventToTest.FAILED) {
      List<Deployment> deployments = new ArrayList<>();
      Deployment deployment = new Deployment(DEPLOYMENT_ID);
      deployment.setPayload(moshi.adapter(Map.class).toJson(Collections.singletonMap("buildIdAsString", "" + buildId)));
      deployments.add(deployment);
      mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(moshi.adapter(List.class).toJson(deployments)));
      mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody(moshi.adapter(DeploymentStatus.class).toJson(new DeploymentStatus(4141, "123", DeploymentStatusState.failure))));
      return 2;
    }
    if (eventToTest == EventToTest.FINISHED) {
      List<Deployment> deployments = new ArrayList<>();
      Deployment deployment = new Deployment(DEPLOYMENT_ID);
      deployment.setPayload(moshi.adapter(Map.class).toJson(Collections.singletonMap("buildIdAsString", "" + buildId)));
      deployments.add(deployment);
      mockWebServer.enqueue(new MockResponse().setResponseCode(200).setBody(moshi.adapter(List.class).toJson(deployments)));
      mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody(moshi.adapter(DeploymentStatus.class).toJson(new DeploymentStatus(4141, "123", DeploymentStatusState.failure))));
      return 2;
    }
    return 0;
  }
}
