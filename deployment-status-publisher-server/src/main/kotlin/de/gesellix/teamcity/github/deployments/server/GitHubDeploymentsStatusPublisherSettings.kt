package de.gesellix.teamcity.github.deployments.server

import jetbrains.buildServer.TeamCityExtension
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor
import jetbrains.buildServer.users.SUser

interface GitHubDeploymentsStatusPublisherSettings : TeamCityExtension {

  fun getId(): String

  fun getName(): String

  fun isEnabled(): Boolean

  fun describeParameters(params: Map<String, String>): String

  fun getOAuthConnections(project: SProject, user: SUser): Map<OAuthConnectionDescriptor, Boolean>

  /**
   * Transforms parameters of the publisher before they are shown in UI
   * @param params parameters to transform
   * @return map of transformed parameters or null if no transformation is needed
   */
  fun transformParameters(params: Map<String, String>): Map<String, String>?

  fun getEditSettingsUrl(): String?

  fun isTestConnectionSupported(): Boolean
}
