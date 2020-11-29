package de.gesellix.teamcity.deployments.server.github.api.impl

import de.gesellix.github.client.GitHubClient
import de.gesellix.github.client.Timeout
import de.gesellix.teamcity.deployments.server.github.api.GitHubApi
import de.gesellix.teamcity.deployments.server.github.api.GitHubApiFactory
import jetbrains.buildServer.version.ServerVersionHolder

class GitHubApiFactoryImpl() : GitHubApiFactory {

  override fun openGitHubForToken(
    url: String,
    token: String,
    timeout: Timeout
  ): GitHubApi {
    return object : GitHubApiImpl(
      GitHubClient(
        baseUrl = url,
        token = token,
        userAgentString = "TeamCity Server ${ServerVersionHolder.getVersion().displayVersion}",
        timeout = timeout
      )
    ) {
    }
  }
}
