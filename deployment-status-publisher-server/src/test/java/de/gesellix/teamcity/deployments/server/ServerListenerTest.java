package de.gesellix.teamcity.deployments.server;

import jetbrains.buildServer.serverSide.ConfigActionsEventDispatcher;
import jetbrains.buildServer.serverSide.ConfigActionsServerListener;
import jetbrains.buildServer.serverSide.CopyOptions;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.SVcsRoot;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static de.gesellix.teamcity.deployments.common.DeploymentsStatusPublisherBuildFeature.BUILD_FEATURE_NAME;
import static de.gesellix.teamcity.deployments.server.ConstantsKt.VCS_ROOT_ID_PARAM;

@Test
public class ServerListenerTest extends BaseServerTestCase {

  private EventDispatcher<ConfigActionsServerListener> myDispatcher;
  private SVcsRoot myVcsRoot;

  private static final String MY_VCS_ID = "MY_VCS_ID";
  private static final String ANOTHER_VCS_ID = "ANOTHER_VCS_ID";

  @BeforeMethod
  public void setUp() throws Exception {
    super.setUp();
    myVcsRoot = myFixture.addVcsRoot("vcs", "vcs1");
    myVcsRoot.setExternalId(MY_VCS_ID);
    myBuildType.addVcsRoot(myVcsRoot);
    myDispatcher = myFixture.getSingletonService(ConfigActionsEventDispatcher.class);
    ServerListener listener = new ServerListener(myDispatcher);
    myFixture.addService(listener);
  }

  public void must_change_vcs_root_external_id_if_renamed() {
    myBuildType.addBuildFeature(BUILD_FEATURE_NAME, Collections.singletonMap(VCS_ROOT_ID_PARAM, MY_VCS_ID));
    myVcsRoot.setExternalId(ANOTHER_VCS_ID);

    Collection<SBuildFeatureDescriptor> buildFeatures = myBuildType.getBuildFeatures();
    assertEquals(1, buildFeatures.size());

    Map<String, String> params = buildFeatures.iterator().next().getParameters();
    assertEquals(1, params.size());
    assertEquals(ANOTHER_VCS_ID, params.get(VCS_ROOT_ID_PARAM));
  }

  public void must_change_vcs_root_external_id_if_copied() {

    myBuildType.addBuildFeature(BUILD_FEATURE_NAME, Collections.singletonMap(VCS_ROOT_ID_PARAM, MY_VCS_ID));

    SProject p2 = myProjectManager.copyProject(myProject, myProject.getParentProject(), new CopyOptions());

    final SVcsRoot vcsRootCopy = p2.getVcsRoots().iterator().next();
    final SBuildType btCopy = p2.getBuildTypes().iterator().next();

    Collection<SBuildFeatureDescriptor> buildFeatures = btCopy.getBuildFeatures();
    assertEquals(1, buildFeatures.size());

    Map<String, String> params = buildFeatures.iterator().next().getParameters();
    assertEquals(1, params.size());
    assertEquals(vcsRootCopy.getExternalId(), params.get(VCS_ROOT_ID_PARAM));
  }

  public void must_change_vcs_root_internal_id_to_external_if_copied() {
    myBuildType.addBuildFeature(BUILD_FEATURE_NAME, Collections.singletonMap(VCS_ROOT_ID_PARAM, String.valueOf(myVcsRoot.getId())));

    SProject p2 = myProjectManager.copyProject(myProject, myProject.getParentProject(), new CopyOptions());

    final SVcsRoot vcsRootCopy = p2.getVcsRoots().iterator().next();
    final SBuildType btCopy = p2.getBuildTypes().iterator().next();

    Collection<SBuildFeatureDescriptor> buildFeatures = btCopy.getBuildFeatures();
    assertEquals(1, buildFeatures.size());

    Map<String, String> params = buildFeatures.iterator().next().getParameters();
    assertEquals(1, params.size());
    assertEquals(vcsRootCopy.getExternalId(), params.get(VCS_ROOT_ID_PARAM));
  }
}
