package de.gesellix.teamcity.github.deployments.common

import jetbrains.buildServer.util.StringUtil

object GitHubDeploymentsStatusPublisherBuildFeature {

  const val BUILD_FEATURE_NAME = "github-deployments-status-publisher"

  init {
    // Just simple usage of TeamCity Common API (shared between server and agent)
    assert(StringUtil.isTrue("true"))
  }
}
