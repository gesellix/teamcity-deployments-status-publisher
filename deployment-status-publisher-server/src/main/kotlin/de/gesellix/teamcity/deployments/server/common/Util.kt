package de.gesellix.teamcity.deployments.server.common

import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisher
import de.gesellix.teamcity.deployments.server.logger
import jetbrains.buildServer.serverSide.BuildTypeNotFoundException
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SQueuedBuild
import jetbrains.buildServer.serverSide.impl.LogUtil

class Util {

  val logger by logger()

  fun getBuildType(event: DeploymentsStatusPublisher.Event, build: SBuild): SBuildType? {
    val buildType = build.buildType
    if (buildType == null) {
      logger.debug("Event: " + event.getName() + ", cannot find buildType for build " + LogUtil.describe(build))
    }
    return buildType
  }

  fun getBuildType(event: DeploymentsStatusPublisher.Event, build: SQueuedBuild): SBuildType? {
    return try {
      build.buildType
    } catch (e: BuildTypeNotFoundException) {
      logger.debug("Event: " + event.getName() + ", cannot find buildType for build " + LogUtil.describe(build))
      null
    }
  }
}
