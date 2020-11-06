package de.gesellix.teamcity.deployments.server

import jetbrains.buildServer.serverSide.BuildTypeIdentity
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor
import jetbrains.buildServer.users.SUser
import jetbrains.buildServer.vcs.VcsRoot

open class DummyPublisherSettings : DeploymentsStatusPublisherSettings {

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

  override fun getDefaultParameters(): Map<String, String>? {
    return emptyMap()
  }

  override fun describeParameters(params: Map<String, String>): String {
    return ""
  }

  override fun getOAuthConnections(project: SProject, user: SUser?): Map<OAuthConnectionDescriptor, Boolean> {
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

  override fun createPublisher(buildType: SBuildType, buildFeatureId: String, params: Map<String, String>): DeploymentsStatusPublisher? {
    return null
  }

  override fun isPublishingForVcsRoot(vcsRoot: VcsRoot?): Boolean {
    return true
  }

  override fun isEventSupported(event: DeploymentsStatusPublisher.Event): Boolean {
    return false
  }
}
