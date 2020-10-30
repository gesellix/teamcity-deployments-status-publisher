package de.gesellix.teamcity.github.deployments.server

import de.gesellix.teamcity.github.deployments.common.GitHubDeploymentsStatusPublisherBuildFeature.BUILD_FEATURE_NAME
import jetbrains.buildServer.serverSide.BuildFeature
import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.PropertiesProcessor

class GitHubDeploymentsStatusPublisherFeature(
  private val controller: GitHubDeploymentsStatusPublisherFeatureController,
  private val publisherManager: PublisherManager) : BuildFeature() {

  override fun getType(): String {
    return BUILD_FEATURE_NAME
  }

  override fun getDisplayName(): String {
    return "Github Deployments Status Publisher"
  }

  override fun getEditParametersUrl(): String? {
    return controller.url
  }

  override fun isMultipleFeaturesPerBuildTypeAllowed(): Boolean {
    return false
  }

  override fun isRequiresAgent(): Boolean {
    return false
  }

  override fun describeParameters(params: Map<String, String>): String {
    val publisherId = params[PUBLISHER_ID_PARAM] ?: return ""
    val settings = publisherManager.findSettings(publisherId) ?: return ""
    return settings.describeParameters(params)
  }

  override fun getParametersProcessor(): PropertiesProcessor? {
    return PropertiesProcessor { params ->
      val errors: MutableList<InvalidProperty> = java.util.ArrayList()
//      val publisherId = params[Constants.PUBLISHER_ID_PARAM]
//      if (StringUtil.isEmptyOrSpaces(publisherId) || DummyPublisherSettings.ID.equals(publisherId)) {
//        errors.add(InvalidProperty(Constants.PUBLISHER_ID_PARAM, "Choose a publisher"))
//        return@PropertiesProcessor errors
//      }
//      val settings: CommitStatusPublisherSettings = myPublisherManager.findSettings(publisherId) ?: return@PropertiesProcessor errors
//      val proc: PropertiesProcessor = settings.getParametersProcessor()
//      if (proc != null) errors.addAll(proc.process(params))
      errors
    }
  }
}
