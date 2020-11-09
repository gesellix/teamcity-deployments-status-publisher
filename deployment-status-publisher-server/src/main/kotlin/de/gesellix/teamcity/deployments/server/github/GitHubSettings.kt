package de.gesellix.teamcity.deployments.server.github

import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisher
import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisherProblems
import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisherSettingsBase
import de.gesellix.teamcity.deployments.server.GITHUB_AUTH_TYPE
import de.gesellix.teamcity.deployments.server.GITHUB_OAUTH_PROVIDER_ID
import de.gesellix.teamcity.deployments.server.GITHUB_OAUTH_USER
import de.gesellix.teamcity.deployments.server.GITHUB_PUBLISHER_ID
import de.gesellix.teamcity.deployments.server.GITHUB_SERVER
import de.gesellix.teamcity.deployments.server.GITHUB_TOKEN
import de.gesellix.teamcity.deployments.server.PublisherException
import de.gesellix.teamcity.deployments.server.github.api.GitHubApiAuthenticationType
import de.gesellix.teamcity.deployments.server.github.api.GitHubApiFactory
import de.gesellix.teamcity.deployments.server.github.ui.UpdateChangesConstants
import jetbrains.buildServer.parameters.ReferencesResolverUtil
import jetbrains.buildServer.serverSide.BuildTypeIdentity
import jetbrains.buildServer.serverSide.InvalidProperty
import jetbrains.buildServer.serverSide.PropertiesProcessor
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SProject
import jetbrains.buildServer.serverSide.auth.SecurityContext
import jetbrains.buildServer.serverSide.executors.ExecutorServices
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionDescriptor
import jetbrains.buildServer.serverSide.oauth.OAuthConnectionsManager
import jetbrains.buildServer.serverSide.oauth.OAuthTokensStorage
import jetbrains.buildServer.serverSide.oauth.github.GHEOAuthProvider
import jetbrains.buildServer.serverSide.oauth.github.GitHubOAuthProvider
import jetbrains.buildServer.users.SUser
import jetbrains.buildServer.users.User
import jetbrains.buildServer.util.StringUtil
import jetbrains.buildServer.vcs.VcsRoot
import jetbrains.buildServer.web.openapi.PluginDescriptor
import jetbrains.buildServer.web.util.WebUtil
import java.util.*
import kotlin.collections.HashMap
import kotlin.collections.set

class GitHubSettings(
  private val changeStatusUpdater: ChangeStatusUpdater,
  executorServices: ExecutorServices,
  descriptor: PluginDescriptor,
  problems: DeploymentsStatusPublisherProblems,
  private val oauthConnectionsManager: OAuthConnectionsManager,
  private val oauthTokensStorage: OAuthTokensStorage,
  private val securityContext: SecurityContext
) :
  DeploymentsStatusPublisherSettingsBase(executorServices, descriptor, problems) {

  private val supportedEvents: Set<DeploymentsStatusPublisher.Event> = object : HashSet<DeploymentsStatusPublisher.Event>() {
    init {
      add(DeploymentsStatusPublisher.Event.STARTED)
      add(DeploymentsStatusPublisher.Event.FINISHED)
      add(DeploymentsStatusPublisher.Event.INTERRUPTED)
      add(DeploymentsStatusPublisher.Event.MARKED_AS_SUCCESSFUL)
    }
  }

  override fun getId(): String {
    return GITHUB_PUBLISHER_ID
  }

  override fun getName(): String {
    return "GitHub"
  }

  override fun isEnabled(): Boolean {
    return true
  }

  override fun getDefaultParameters(): Map<String, String>? {
    val result: MutableMap<String, String> = HashMap()
    val constants = UpdateChangesConstants()
    result[constants.getServerKey()] = GitHubApiFactory.DEFAULT_URL
    return result
  }

  override fun describeParameters(params: Map<String, String>): String {
    var result = super.describeParameters(params)
    val url = params[GITHUB_SERVER]
    if (null != url && url != GitHubApiFactory.DEFAULT_URL) {
      result += ": " + WebUtil.escapeXml(url)
    }
    return result
  }

  override fun getOAuthConnections(project: SProject, user: SUser?): Map<OAuthConnectionDescriptor, Boolean> {
    val validConnections: MutableList<OAuthConnectionDescriptor> = ArrayList()
    val githubConnections: List<OAuthConnectionDescriptor> = oauthConnectionsManager.getAvailableConnectionsOfType(project, GitHubOAuthProvider.TYPE)
    if (githubConnections.isNotEmpty()) {
      validConnections.add(githubConnections[0])
    }
    validConnections.addAll(oauthConnectionsManager.getAvailableConnectionsOfType(project, GHEOAuthProvider.TYPE))
    val connections: MutableMap<OAuthConnectionDescriptor, Boolean> = LinkedHashMap()
    for (c in validConnections) {
      connections[c] = user != null && oauthTokensStorage.getUserTokens(c.id, user).isNotEmpty()
    }
    return connections
  }

  override fun transformParameters(params: Map<String, String>): MutableMap<String, String>? {
    return null
  }

  override fun getEditSettingsUrl(): String? {
    return descriptor.getPluginResourcesPath("github/githubSettings.jsp")
  }

  override fun isTestConnectionSupported(): Boolean {
    return true
  }

  @Throws(PublisherException::class)
  override fun testConnection(buildTypeOrTemplate: BuildTypeIdentity, root: VcsRoot, params: Map<String, String>) {
    changeStatusUpdater.testConnection(root, params)
  }

  override fun getParametersProcessor(): PropertiesProcessor? {
    val constants = UpdateChangesConstants()
    return object : PropertiesProcessor {
      private fun checkNotEmpty(
        properties: Map<String, String>,
        key: String,
        message: String,
        res: MutableCollection<InvalidProperty>
      ): Boolean {
        if (isEmpty(properties, key)) {
          res.add(InvalidProperty(key, message))
          return true
        }
        return false
      }

      private fun isEmpty(
        properties: Map<String, String>,
        key: String
      ): Boolean {
        return StringUtil.isEmptyOrSpaces(properties[key])
      }

      override fun process(p: MutableMap<String, String>?): Collection<InvalidProperty> {
        val result: MutableCollection<InvalidProperty> = ArrayList()
        if (p == null) return result

        val authenticationType: GitHubApiAuthenticationType = GitHubApiAuthenticationType.parse(p[GITHUB_AUTH_TYPE])
        if (authenticationType === GitHubApiAuthenticationType.TOKEN_AUTH) {
          val oauthUsername = p[GITHUB_OAUTH_USER]
          val oauthProviderId = p[GITHUB_OAUTH_PROVIDER_ID]
          if (null != oauthUsername && null != oauthProviderId) {
            val currentUser: User? = securityContext.authorityHolder.associatedUser
            if (null != currentUser && currentUser is SUser) {
              for (token in oauthTokensStorage.getUserTokens(oauthProviderId, currentUser)) {
                if (token.oauthLogin == oauthUsername) {
                  p[GITHUB_TOKEN] = token.accessToken
                  p.remove(GITHUB_OAUTH_PROVIDER_ID)
                }
              }
            }
          }
          checkNotEmpty(p, GITHUB_TOKEN, "Personal Access Token must be specified", result)
        }
        if (!checkNotEmpty(p, constants.getServerKey(), "GitHub API URL must be specified", result)) {
          val url = "" + p[constants.getServerKey()]
          if (!ReferencesResolverUtil.mayContainReference(url) && !(url.startsWith("http://") || url.startsWith("https://"))) {
            result.add(InvalidProperty(constants.getServerKey(), "GitHub API URL should start with http:// or https://"))
          }
        }
        return result
      }
    }
  }

  override fun createPublisher(buildType: SBuildType, buildFeatureId: String, params: Map<String, String>): DeploymentsStatusPublisher? {
    return GitHubPublisher(this, buildType, buildFeatureId, changeStatusUpdater, params, problems)
  }

  override fun isPublishingForVcsRoot(vcsRoot: VcsRoot?): Boolean {
    return "jetbrains.git" == vcsRoot?.vcsName
  }

  override fun isEventSupported(event: DeploymentsStatusPublisher.Event): Boolean {
    return supportedEvents.contains(event)
  }
}
