package de.gesellix.teamcity.deployments.server.github

import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisherBase
import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisherProblems
import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisherSettings
import de.gesellix.teamcity.deployments.server.GITHUB_CONTEXT
import de.gesellix.teamcity.deployments.server.GITHUB_CUSTOM_CONTEXT_BUILD_PARAM
import de.gesellix.teamcity.deployments.server.GITHUB_DEPLOYMENT_ENVIRONMENT
import de.gesellix.teamcity.deployments.server.GITHUB_PUBLISHER_ID
import de.gesellix.teamcity.deployments.server.GITHUB_SERVER
import de.gesellix.teamcity.deployments.server.PublisherException
import de.gesellix.teamcity.deployments.server.logger
import jetbrains.buildServer.serverSide.BuildRevision
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SFinishedBuild
import jetbrains.buildServer.serverSide.SRunningBuild
import java.util.*

class GitHubPublisher(
  settings: DeploymentsStatusPublisherSettings,
  buildType: SBuildType,
  buildFeatureId: String,
  private val updater: DeploymentsStatusUpdater,
  params: Map<String, String>,
  problems: DeploymentsStatusPublisherProblems
) : DeploymentsStatusPublisherBase(settings, buildType, buildFeatureId, params, problems) {

  private val logger by logger(GitHubPublisher::class.java.name)

  override fun toString(): String {
    return "github"
  }

  override val id: String
    get() = GITHUB_PUBLISHER_ID

  @Throws(PublisherException::class)
  override fun buildStarted(build: SRunningBuild, revision: BuildRevision): Boolean {
    updateBuildStatus(build, revision, true)
    return true
  }

  @Throws(PublisherException::class)
  override fun buildFinished(build: SFinishedBuild, revision: BuildRevision): Boolean {
    updateBuildStatus(build, revision, false)
    return true
  }

  @Throws(PublisherException::class)
  override fun buildInterrupted(build: SFinishedBuild, revision: BuildRevision): Boolean {
    updateBuildStatus(build, revision, false)
    return true
  }

  @Throws(PublisherException::class)
  override fun buildMarkedAsSuccessful(build: SBuild, revision: BuildRevision, buildInProgress: Boolean): Boolean {
    updateBuildStatus(build, revision, buildInProgress)
    return true
  }

  val serverUrl: String?
    get() = params[GITHUB_SERVER]

  @Throws(PublisherException::class)
  private fun updateBuildStatus(build: SBuild, revision: BuildRevision, isStarting: Boolean) {
    val h = updater.getUpdateHandler(revision.root, getParams(build), this)
    if (isStarting && !h.shouldReportOnStart()) return
    if (!isStarting && !h.shouldReportOnFinish()) return
    if (revision.root.vcsName != "jetbrains.git") {
      logger.warn("No revisions were found to update GitHub status. Please check you have Git VCS roots in the build configuration")
      return
    }
    val environment = build.parametersProvider[GITHUB_DEPLOYMENT_ENVIRONMENT] ?: "production"
    if (isStarting) {
      h.scheduleChangeStarted(revision.repositoryVersion, build, environment)
    } else {
      h.scheduleChangeCompleted(revision.repositoryVersion, build, environment)
    }
  }

  private fun getParams(build: SBuild): Map<String, String> {
    var context = getCustomContextFromParameter(build)
    if (context == null) context = getDefaultContext(build)
    val result: MutableMap<String, String> = HashMap(params)
    result[GITHUB_CONTEXT] = context
    return result
  }

  private fun getDefaultContext(build: SBuild): String {
    val buildType = build.buildType
    return if (buildType != null) {
      String.format("%s (%s)", buildType.name, buildType.project.name)
    } else {
      "<Removed build configuration>"
    }
  }

  private fun getCustomContextFromParameter(build: SBuild): String? {
    val value = build.parametersProvider[GITHUB_CUSTOM_CONTEXT_BUILD_PARAM]
    return if (value == null) {
      null
    } else {
      build.valueResolver.resolve(value).result
    }
  }
}
