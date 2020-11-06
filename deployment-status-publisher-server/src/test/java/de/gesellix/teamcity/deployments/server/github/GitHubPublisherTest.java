package de.gesellix.teamcity.deployments.server.github;

import com.google.gson.Gson;
import de.gesellix.teamcity.deployments.server.HttpPublisherTest;
import de.gesellix.teamcity.deployments.server.MockPluginDescriptor;
import de.gesellix.teamcity.deployments.server.PublisherException;
import de.gesellix.teamcity.deployments.server.github.api.impl.GitHubApiFactoryImpl;
import de.gesellix.teamcity.deployments.server.github.api.impl.HttpClientWrapperImpl;
import de.gesellix.teamcity.deployments.server.github.api.impl.data.Permissions;
import de.gesellix.teamcity.deployments.server.github.api.impl.data.RepoInfo;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.serverSide.BasePropertiesModel;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.serverSide.TeamCityProperties;
import jetbrains.buildServer.vcs.VcsRootInstance;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.entity.StringEntity;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static de.gesellix.teamcity.deployments.server.ConstantsKt.GITHUB_PASSWORD;
import static de.gesellix.teamcity.deployments.server.ConstantsKt.GITHUB_SERVER;
import static de.gesellix.teamcity.deployments.server.ConstantsKt.GITHUB_USERNAME;
import static org.assertj.core.api.BDDAssertions.then;

/**
 * @author anton.zamolotskikh, 05/10/16.
 */
@Test
public class GitHubPublisherTest extends HttpPublisherTest {

  private ChangeStatusUpdater myChangeStatusUpdater;

  public GitHubPublisherTest() {
    myExpectedRegExps.put(EventToTest.QUEUED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.REMOVED, null);  // not to be tested
    myExpectedRegExps.put(EventToTest.STARTED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*build started.*pending.*", REVISION));
    myExpectedRegExps.put(EventToTest.FINISHED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*build finished.*success.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*build failed.*failure.*", REVISION));
    myExpectedRegExps.put(EventToTest.COMMENTED_SUCCESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS, null); // not to be tested
    myExpectedRegExps.put(EventToTest.COMMENTED_INPROGRESS_FAILED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.INTERRUPTED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*failure.*", REVISION));
    myExpectedRegExps.put(EventToTest.FAILURE_DETECTED, null); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_SUCCESSFUL, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*build finished.*success.*", REVISION)); // not to be tested
    myExpectedRegExps.put(EventToTest.MARKED_RUNNING_SUCCESSFUL, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*build started.*pending.*", REVISION)); // not to be tested
    myExpectedRegExps.put(EventToTest.PAYLOAD_ESCAPED, String.format(".*/repos/owner/project/statuses/%s.*ENTITY:.*build failed.*failure.*%s.*", REVISION, BT_NAME_ESCAPED_REGEXP));
    myExpectedRegExps.put(EventToTest.TEST_CONNECTION, String.format(".*/repos/owner/project .*")); // not to be tested
  }

  public void test_buildFinishedSuccessfully_server_url_with_subdir() throws Exception {
    Map<String, String> params = getPublisherParams();
    setExpectedApiPath("/subdir/api/v3");
    params.put(GITHUB_SERVER, getServerUrl() + "/subdir/api/v3");
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/subdir/owner/project"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myPublisher = new GitHubPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myChangeStatusUpdater, params, myProblems);
    test_buildFinished_Successfully();
  }

  public void test_buildFinishedSuccessfully_server_url_with_slash() throws Exception {
    Map<String, String> params = getPublisherParams();
    setExpectedApiPath("/subdir/api/v3");
    params.put(GITHUB_SERVER, getServerUrl() + "/subdir/api/v3/");
    myVcsRoot.setProperties(Collections.singletonMap("url", "https://url.com/subdir/owner/project"));
    VcsRootInstance vcsRootInstance = myBuildType.getVcsRootInstanceForParent(myVcsRoot);
    myRevision = new BuildRevision(vcsRootInstance, REVISION, "", REVISION);
    myPublisher = new GitHubPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myChangeStatusUpdater, params, myProblems);
    test_buildFinished_Successfully();
  }

  public void should_fail_with_error_on_wrong_vcs_url() throws InterruptedException {
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
    setExpectedApiPath("");
    setExpectedEndpointPrefix("/repos/" + OWNER + "/" + CORRECT_REPO);
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

    myChangeStatusUpdater = new ChangeStatusUpdater(myExecServices,
                                                    new GitHubApiFactoryImpl(new HttpClientWrapperImpl()), myWebLinks);

    myPublisherSettings = new GitHubSettings(myChangeStatusUpdater, myExecServices, new MockPluginDescriptor(), myWebLinks, myProblems,
                                             myOAuthConnectionsManager, myOAuthTokenStorage, myFixture.getSecurityContext());
    myPublisher = new GitHubPublisher(myPublisherSettings, myBuildType, FEATURE_ID, myChangeStatusUpdater, params, myProblems);
  }

  @Override
  protected boolean respondToGet(String url, HttpResponse httpResponse) {
    if (url.contains("/repos" + "/" + OWNER + "/" + CORRECT_REPO)) {
      respondWithRepoInfo(httpResponse, CORRECT_REPO, true);
    }
    else if (url.contains("/repos" + "/" + OWNER + "/" + READ_ONLY_REPO)) {
      respondWithRepoInfo(httpResponse, READ_ONLY_REPO, false);
    }
    else {
      respondWithError(httpResponse, 404, String.format("Unexpected URL: %s", url));
      return false;
    }
    return true;
  }

  @Override
  protected boolean respondToPost(String url, String requestData, final HttpRequest httpRequest, HttpResponse httpResponse) {
    return isUrlExpected(url, httpResponse);
  }

  private void respondWithRepoInfo(HttpResponse httpResponse, String repoName, boolean isPushPermitted) {
    Gson gson = new Gson();
    RepoInfo repoInfo = new RepoInfo();
    repoInfo.setName(repoName);
    repoInfo.setPermissions(new Permissions());
    repoInfo.getPermissions().setPull(true);
    repoInfo.getPermissions().setPush(isPushPermitted);
    String jsonResponse = gson.toJson(repoInfo);
    httpResponse.setEntity(new StringEntity(jsonResponse, "UTF-8"));
  }

  @Override
  protected Map<String, String> getPublisherParams() {
    return new HashMap<String, String>() {{
      put(GITHUB_USERNAME, "user");
      put(GITHUB_PASSWORD, "pwd");
      put(GITHUB_SERVER, getServerUrl());
    }};
  }
}
