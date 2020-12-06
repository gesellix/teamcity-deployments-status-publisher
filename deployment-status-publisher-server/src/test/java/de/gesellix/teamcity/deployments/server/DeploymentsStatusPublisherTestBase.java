package de.gesellix.teamcity.deployments.server;

import jetbrains.buildServer.ExtensionHolder;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.controllers.admin.projects.BuildTypeForm;
import jetbrains.buildServer.controllers.admin.projects.MultipleRunnersBean;
import jetbrains.buildServer.controllers.admin.projects.VcsSettingsBean;
import jetbrains.buildServer.parameters.ValueResolver;
import jetbrains.buildServer.serverSide.BuildFeature;
import jetbrains.buildServer.serverSide.BuildTypeSettings;
import jetbrains.buildServer.serverSide.ProjectManager;
import jetbrains.buildServer.serverSide.RunningBuildsManager;
import jetbrains.buildServer.serverSide.SBuildFeatureDescriptor;
import jetbrains.buildServer.serverSide.SBuildType;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.impl.BaseServerTestCase;
import jetbrains.buildServer.serverSide.impl.MockVcsSupport;
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemNotificationEngine;
import jetbrains.buildServer.util.browser.Browser;
import jetbrains.buildServer.vcs.SVcsRoot;
import jetbrains.buildServer.vcs.ServerVcsSupport;
import jetbrains.buildServer.vcs.VcsManager;
import jetbrains.buildServer.vcs.VcsManagerEx;
import jetbrains.buildServer.vcs.VcsRoot;
import jetbrains.buildServer.web.openapi.ControllerAction;
import jetbrains.buildServer.web.openapi.PagePlace;
import jetbrains.buildServer.web.openapi.PlaceId;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import jetbrains.buildServer.web.openapi.WebExtension;
import jetbrains.buildServer.web.openapi.WebPlace;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.mvc.Controller;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static de.gesellix.teamcity.deployments.server.ConstantsKt.PUBLISHER_ID_PARAM;
import static de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisherFeature.BUILD_FEATURE_NAME;

public class DeploymentsStatusPublisherTestBase extends BaseServerTestCase {

  protected PublisherSettingsController mySettingsController;
  protected DeploymentsStatusPublisherFeatureController myFeatureController;
  protected VcsManagerEx myVcsManager;
  protected ProjectManager myProjectManager;
  protected SBuildFeatureDescriptor myFeatureDescriptor;
  protected RunningBuildsManager myRBManager;
  protected MockPublisherSettings myPublisherSettings;
  protected DeploymentsStatusPublisherProblems myProblems;
  protected DeploymentsStatusPublisherFeature myFeature;
  protected Map<String, String> myCurrentVersions;
  protected SystemProblemNotificationEngine myProblemNotificationEngine;

  protected void setUp() throws Exception {
    super.setUp();
    WebControllerManager wcm = new MockWebControllerManager();
    PluginDescriptor pluginDescr = new MockPluginDescriptor();
    myProjectManager = myFixture.getProjectManager();
    myPublisherSettings = new MockPublisherSettings(myProblems);
    final PublisherManager publisherManager = new PublisherManager(Collections.singletonList(myPublisherSettings));

    myCurrentVersions = new HashMap<>();
    mySettingsController = new PublisherSettingsController(wcm, pluginDescr, publisherManager, myProjectManager);

    myFeatureController = new DeploymentsStatusPublisherFeatureController(myProjectManager, wcm, pluginDescr, publisherManager, mySettingsController);
    myVcsManager = myFixture.getVcsManager();

    ServerVcsSupport vcsSupport = new MockVcsSupport("jetbrains.git") {

      private final Map<String, String> myVersions = myCurrentVersions;

      @Override
      @NotNull
      public String getCurrentVersion(@NotNull final VcsRoot root) {
        if (!myVersions.containsKey(root.getName())) {
          throw new IllegalArgumentException("Unknown VCS root");
        }
        return myCurrentVersions.get(root.getName());
      }
    };
    myVcsManager.registerVcsSupport(vcsSupport);

    myFeatureDescriptor = myBuildType.addBuildFeature(BUILD_FEATURE_NAME, Collections.singletonMap(PUBLISHER_ID_PARAM, MockPublisherSettings.PUBLISHER_ID));
    myFeature = new DeploymentsStatusPublisherFeature(myFeatureController, publisherManager);
    myRBManager = myFixture.getSingletonService(RunningBuildsManager.class);
    myProblemNotificationEngine = myFixture.getSingletonService(SystemProblemNotificationEngine.class);
    myProblems = new DeploymentsStatusPublisherProblems(myProblemNotificationEngine);
    ExtensionHolder extensionHolder = myFixture.getSingletonService(ExtensionHolder.class);
    extensionHolder.registerExtension(BuildFeature.class, BUILD_FEATURE_NAME, myFeature);
  }

  private class MockWebControllerManager implements WebControllerManager {

    @Override
    public void registerController(@NotNull String s, @NotNull Controller controller) {
    }

    @Override
    public void registerAction(@NotNull BaseController baseController, @NotNull ControllerAction controllerAction) {
    }

    @Nullable
    @Override
    public ControllerAction getAction(@NotNull BaseController baseController, @NotNull HttpServletRequest httpServletRequest) {
      return null;
    }

    @Override
    public void addPageExtension(WebPlace webPlace, WebExtension webExtension) {
    }

    @Override
    public void removePageExtension(WebPlace webPlace, WebExtension webExtension) {
    }

    @NotNull
    @Override
    public PagePlace getPlaceById(@NotNull PlaceId placeId) {
      return null;
    }
  }

  class MockBuildTypeForm extends BuildTypeForm {

    private final VcsSettingsBean myVcsBean;

    MockBuildTypeForm(SBuildType buildType, VcsSettingsBean bean) {
      super(buildType.getProject());
      myVcsBean = bean;
    }

    @NotNull
    @Override
    protected MultipleRunnersBean createMultipleRunnersBean() {
      return null;
    }

    @NotNull
    @Override
    public VcsSettingsBean getVcsRootsBean() {
      return myVcsBean;
    }

    @NotNull
    @Override
    public ValueResolver getValueResolver() {
      return null;
    }

    @Override
    public boolean isBranchesConfigured() {
      return false;
    }

    @Override
    public boolean isCompositeBuild() {
      return false;
    }
  }

  class MockVcsSettingsBean extends VcsSettingsBean {

    MockVcsSettingsBean(@NotNull SProject project, @NotNull BuildTypeSettings buildTypeSettings, @NotNull VcsManager vcsManager, @NotNull ProjectManager projectManager) {
      super(project, buildTypeSettings, vcsManager, projectManager);
    }

    @Override
    protected boolean supportsLabeling(SVcsRoot sVcsRoot) {
      return false;
    }

    @Nullable
    @Override
    public Browser getVcsBrowser(boolean b, @Nullable String s) {
      return null;
    }

    @Override
    public boolean isDefaultExcluded() {
      return false;
    }
  }
}
