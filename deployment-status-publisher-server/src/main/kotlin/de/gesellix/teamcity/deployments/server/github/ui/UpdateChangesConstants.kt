package de.gesellix.teamcity.deployments.server.github.ui

import de.gesellix.teamcity.deployments.server.GITHUB_AUTH_TYPE
import de.gesellix.teamcity.deployments.server.GITHUB_OAUTH_PROVIDER_ID
import de.gesellix.teamcity.deployments.server.GITHUB_OAUTH_USER
import de.gesellix.teamcity.deployments.server.GITHUB_SERVER
import de.gesellix.teamcity.deployments.server.GITHUB_TOKEN
import de.gesellix.teamcity.deployments.server.github.api.GitHubApiAuthenticationType

class UpdateChangesConstants() {

  fun getServerKey(): String = GITHUB_SERVER
  fun getAccessTokenKey(): String = GITHUB_TOKEN
  fun getOAuthUserKey(): String = GITHUB_OAUTH_USER
  fun getOAuthProviderIdKey(): String = GITHUB_OAUTH_PROVIDER_ID
  fun getAuthenticationTypeKey(): String = GITHUB_AUTH_TYPE
  fun getAuthenticationTypeTokenValue(): String = GitHubApiAuthenticationType.TOKEN_AUTH.value
}
