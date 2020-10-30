package de.gesellix.teamcity.github.deployments.server

import jetbrains.buildServer.serverSide.BuildTypeIdentity
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor
import jetbrains.buildServer.users.SUser
import jetbrains.buildServer.vcs.VcsRoot
import java.security.KeyStore

const val ID = "--"

internal class DummyPublisherSettings : GitHubDeploymentsStatusPublisherSettings {

  override fun getId(): String {
    return ID
  }

  override fun getName(): String {
    return "--Choose publisher--"
  }

  override fun getEditSettingsUrl(): String? {
    return null
  }

  fun createPublisher(buildType: SBuildType, buildFeatureId: String, params: Map<String?, String?>): GitHubDeploymentsStatusPublisherFeature? {
    return null
  }

  val defaultParameters: Map<String, String>?
    get() = null

  override fun transformParameters(params: Map<String, String>): Map<String, String> {
    return emptyMap()
  }

  override fun describeParameters(params: Map<String, String>): String {
    return ""
  }

  val parametersProcessor: PropertiesProcessor?
    get() = null

  override fun getOAuthConnections(project: SProject, user: SUser): Map<OAuthConnectionDescriptor, Boolean> {
    return emptyMap()
  }

  override fun isEnabled(): Boolean {
    return true
  }

  fun isPublishingForVcsRoot(vcsRoot: VcsRoot?): Boolean {
    return true
  }

  fun isEventSupported(event: GitHubDeploymentsStatusPublisher.Event?, buildType: SBuildType?, params: Map<String?, String?>?): Boolean {
    return false
  }

  override fun isTestConnectionSupported(): Boolean {
    return false
  }

  @Throws(PublisherException::class)
  fun testConnection(buildTypeOrTemplate: BuildTypeIdentity, root: VcsRoot, params: Map<String?, String?>) {
    // does nothing
  }

  fun trustStore(): KeyStore? {
    return null
  }
}
