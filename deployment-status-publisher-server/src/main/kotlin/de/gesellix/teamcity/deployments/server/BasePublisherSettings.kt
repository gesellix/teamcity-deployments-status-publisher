package de.gesellix.teamcity.deployments.server

import jetbrains.buildServer.serverSide.BuildTypeIdentity
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.WebLinks
import jetbrains.buildServer.serverSide.executors.ExecutorServices
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor
import jetbrains.buildServer.users.SUser
import jetbrains.buildServer.vcs.VcsRoot
import jetbrains.buildServer.web.openapi.PluginDescriptor

abstract class BasePublisherSettings(
  protected val executorServices: ExecutorServices,
  protected val descriptor: PluginDescriptor,
  protected val links: WebLinks,
  protected val problems: DeploymentsStatusPublisherProblems
) : DeploymentsStatusPublisherSettings {

  override fun getDefaultParameters(): Map<String, String>? {
    return null
  }

  override fun transformParameters(params: Map<String, String>): Map<String, String>? {
    return null
  }

  override fun describeParameters(params: Map<String, String>): String {
    return java.lang.String.format("Post deployment status to %s", getName())
  }

  override fun getOAuthConnections(project: SProject, user: SUser?): Map<OAuthConnectionDescriptor, Boolean> {
    return emptyMap()
  }

  override fun isEnabled(): Boolean {
    return true
  }

  override fun isPublishingForVcsRoot(vcsRoot: VcsRoot?): Boolean {
    return true
  }

  override fun isEventSupported(event: DeploymentsStatusPublisher.Event): Boolean {
    return false
  }

  override fun isTestConnectionSupported(): Boolean {
    return false
  }

  @Throws(PublisherException::class)
  override fun testConnection(buildTypeOrTemplate: BuildTypeIdentity, root: VcsRoot, params: Map<String, String>) {
    throw UnsupportedOperationException(java.lang.String.format("Test connection functionality is not supported by %s publisher", getName()))
  }
}
