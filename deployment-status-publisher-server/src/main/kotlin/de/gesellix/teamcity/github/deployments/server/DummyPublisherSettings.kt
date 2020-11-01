package de.gesellix.teamcity.github.deployments.server

import jetbrains.buildServer.serverSide.BuildTypeIdentity
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor
import jetbrains.buildServer.users.SUser
import jetbrains.buildServer.vcs.VcsRoot

internal class DummyPublisherSettings : GitHubDeploymentsStatusPublisherSettings {

  companion object {

    const val ID = "--"
  }

  override fun getId(): String {
    return ID
  }

  override fun getName(): String {
    return "--Choose publisher--"
  }

  override fun isEnabled(): Boolean {
    return true
  }

  override fun describeParameters(params: Map<String, String>): String {
    return ""
  }

  override fun getOAuthConnections(project: SProject, user: SUser): Map<OAuthConnectionDescriptor, Boolean> {
    return emptyMap()
  }

  override fun transformParameters(params: Map<String, String>): Map<String, String> {
    return emptyMap()
  }

  override fun getEditSettingsUrl(): String? {
    return null
  }

  override fun isTestConnectionSupported(): Boolean {
    return false
  }

  @Throws(PublisherException::class)
  override fun testConnection(buildTypeOrTemplate: BuildTypeIdentity, root: VcsRoot, params: Map<String, String>) {
    // does nothing
  }

  override fun getParametersProcessor(): PropertiesProcessor? {
    return null
  }

  override fun createPublisher(buildType: SBuildType, buildFeatureId: String, params: Map<String, String>): GitHubDeploymentsStatusPublisher? {
    return null
  }

  override fun isPublishingForVcsRoot(vcsRoot: VcsRoot?): Boolean {
    return true
  }

  override fun isEventSupported(event: GitHubDeploymentsStatusPublisher.Event, buildType: SBuildType, params: Map<String, String>): Boolean {
    return false
  }
}
