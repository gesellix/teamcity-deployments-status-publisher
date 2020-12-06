package de.gesellix.teamcity.deployments.server.github.api

import de.gesellix.github.client.Timeout
import java.util.concurrent.TimeUnit

interface GitHubApiFactory {

  fun openGitHubForToken(
    url: String,
    token: String,
    timeout: Timeout = Timeout(10, TimeUnit.SECONDS)
  ): GitHubApi

  companion object {

    const val DEFAULT_URL = "https://api.github.com"
  }
}
