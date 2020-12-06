package de.gesellix.teamcity.deployments.server

import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.serverSide.BuildPromotionEx
import jetbrains.buildServer.serverSide.BuildPromotionOwner
import jetbrains.buildServer.serverSide.BuildRevision
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SQueuedBuild
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.serverSide.impl.LogUtil

const val PUBLISHING_ENABLED_PROPERTY_NAME = "teamcity.deploymentsStatusPublisher.enabled"

class DeploymentsStatusPublishingTaskRunner(
  private val publisherService: PublisherService,
  private val problems: DeploymentsStatusPublisherProblems
) {

  private val logger by logger(DeploymentsStatusPublishingTaskRunner::class.java.name)

  private fun isPublishingDisabled(buildType: SBuildType): Boolean {
    val publishingEnabledParam = buildType.getParameterValue(PUBLISHING_ENABLED_PROPERTY_NAME)
    return "false" == publishingEnabledParam || !(TeamCityProperties.getBooleanOrTrue(PUBLISHING_ENABLED_PROPERTY_NAME)
      || "true" == publishingEnabledParam)
  }

  private fun logStatusNotPublished(event: DeploymentsStatusPublisher.Event, buildDescription: String, publisher: DeploymentsStatusPublisher, message: String) {
    logger.info(String.format("Event: %s, build %s, publisher %s: %s", event.getName(), buildDescription, publisher.toString(), message))
  }

  fun runForEveryPublisher(event: DeploymentsStatusPublisher.Event, buildType: SBuildType, build: SBuild, task: PublishTask) {
    if (build.isPersonal) {
      for (change in build.buildPromotion.personalChanges) {
        if (change.isPersonal) {
          return
        }
      }
    }
    val publishers = publisherService.getPublishers(buildType)
    logger.debug("Event: " + event.getName() + ", build " + LogUtil.describe(build) + ", publishers: " + publishers.values)
    for ((_, publisher) in publishers) {
      if (!publisher.isEventSupported(event)) {
        continue
      }
      if (isPublishingDisabled(buildType)) {
        logStatusNotPublished(event, LogUtil.describe(build), publisher, "deployments status publishing is disabled")
        continue
      }
      val revisions = publisherService.getBuildRevisionForVote(publisher, build)
      if (revisions.isEmpty()) {
        logStatusNotPublished(event, LogUtil.describe(build), publisher, "no compatible revisions found")
        continue
      }
      problems.clearProblem(publisher)
      for (revision in revisions) {
        runTask(event, build, task, publisher, revision)
      }
    }
    problems.clearObsoleteProblems(buildType, publishers.keys)
  }

  fun runForEveryPublisherQueued(event: DeploymentsStatusPublisher.Event, buildType: SBuildType, build: SQueuedBuild, task: PublishTask) {
    if (build.isPersonal) {
      for (change in build.buildPromotion.personalChanges) {
        if (change.isPersonal) {
          return
        }
      }
    }
    val publishers = publisherService.getPublishers(buildType)
    logger.debug("Event: " + event.getName() + ", build " + LogUtil.describe(build) + ", publishers: " + publishers.values)
    for ((_, publisher) in publishers) {
      if (!publisher.isEventSupported(event)) {
        continue
      }
      if (isPublishingDisabled(buildType)) {
        logStatusNotPublished(event, LogUtil.describe(build), publisher, "deployments status publishing is disabled")
        continue
      }
      val revisions = publisherService.getQueuedBuildRevisionForVote(buildType, publisher, build)
      if (revisions.isEmpty()) {
        logStatusNotPublished(event, LogUtil.describe(build), publisher, "no compatible revisions found")
        continue
      }
      problems.clearProblem(publisher)
      for (revision in revisions) {
        runTask(event, build, task, publisher, revision)
      }
    }
    problems.clearObsoleteProblems(buildType, publishers.keys)
  }

  private fun runTask(
    event: DeploymentsStatusPublisher.Event,
    build: BuildPromotionOwner,
    task: PublishTask,
    publisher: DeploymentsStatusPublisher,
    revision: BuildRevision
  ) {
    try {
      task.run(publisher, revision)
    } catch (t: Throwable) {
      problems.reportProblem(String.format("Deployments Status Publisher has failed to publish %s status", event.getName()), publisher, LogUtil.describe(build), null, t, logger)
      if (shouldFailBuild(publisher.getBuildType())) {
        val problemId = "deploymentsStatusPublisher." + publisher.id + "." + revision.root.id
        val problemDescription = if (t is PublisherException) t.message else t.toString()
        val buildProblem = BuildProblemData.createBuildProblem(problemId, "deploymentsStatusPublisherProblem", problemDescription)
        (build.buildPromotion as BuildPromotionEx).addBuildProblem(buildProblem)
      }
    }
  }

  private fun shouldFailBuild(buildType: SBuildType): Boolean {
    return java.lang.Boolean.valueOf(buildType.parameters["teamcity.deploymentsStatusPublisher.failBuildOnPublishError"])
  }
}
