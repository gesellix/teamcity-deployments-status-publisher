package de.gesellix.teamcity.deployments.common

import jetbrains.buildServer.util.StringUtil

object DeploymentsStatusPublisherBuildFeature {

  const val BUILD_FEATURE_NAME = "deployments-status-publisher"

  init {
    // Just simple usage of TeamCity Common API (shared between server and agent)
    assert(StringUtil.isTrue("true"))
  }
}
