package de.gesellix.teamcity.deployments.server

import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.messages.Status
import jetbrains.buildServer.serverSide.Branch
import jetbrains.buildServer.serverSide.BuildHistory
import jetbrains.buildServer.serverSide.BuildPromotion
import jetbrains.buildServer.serverSide.BuildPromotionEx
import jetbrains.buildServer.serverSide.BuildRevision
import jetbrains.buildServer.serverSide.BuildServerAdapter
import jetbrains.buildServer.serverSide.BuildServerListener
import jetbrains.buildServer.serverSide.BuildTypeEx
import jetbrains.buildServer.serverSide.BuildTypeNotFoundException
import jetbrains.buildServer.serverSide.RunningBuildsManager
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SQueuedBuild
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.serverSide.TeamCityProperties
import jetbrains.buildServer.serverSide.impl.LogUtil
import jetbrains.buildServer.users.User
import jetbrains.buildServer.util.EventDispatcher
import java.util.*

class DeploymentsStatusPublisherListener(
  events: EventDispatcher<BuildServerListener?>,
  private val publisherManager: PublisherManager,
  private val buildHistory: BuildHistory,
  private val runningBuilds: RunningBuildsManager,
  private val problems: DeploymentsStatusPublisherProblems
) : BuildServerAdapter() {

  private val logger by logger(DeploymentsStatusPublisherListener::class.java.name)

  override fun changesLoaded(build: SRunningBuild) {
    val buildType = getBuildType(DeploymentsStatusPublisher.Event.STARTED, build) ?: return
    runForEveryPublisher(DeploymentsStatusPublisher.Event.STARTED, buildType, build, object : PublishTask {
      @Throws(PublisherException::class)
      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
        return publisher.buildStarted(build, revision)
      }
    })
  }

  override fun buildFinished(build: SRunningBuild) {
    val buildType = getBuildType(DeploymentsStatusPublisher.Event.FINISHED, build) ?: return
    val finishedBuild = buildHistory.findEntry(build.buildId)
    if (finishedBuild == null) {
      logger.debug("Event: " + DeploymentsStatusPublisher.Event.FINISHED + ", cannot find finished build for build " + LogUtil.describe(build))
      return
    }
    runForEveryPublisher(DeploymentsStatusPublisher.Event.FINISHED, buildType, build, object : PublishTask {
      @Throws(PublisherException::class)
      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
        return publisher.buildFinished(finishedBuild, revision)
      }
    })
  }

  override fun buildCommented(build: SBuild, user: User?, comment: String?) {
    val buildType = getBuildType(DeploymentsStatusPublisher.Event.COMMENTED, build) ?: return
    runForEveryPublisher(DeploymentsStatusPublisher.Event.COMMENTED, buildType, build, object : PublishTask {
      @Throws(PublisherException::class)
      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
        return publisher.buildCommented(build, revision, user, comment, isBuildInProgress(build))
      }
    })
  }

  override fun buildInterrupted(build: SRunningBuild) {
    val buildType = getBuildType(DeploymentsStatusPublisher.Event.INTERRUPTED, build) ?: return
    val finishedBuild = buildHistory.findEntry(build.buildId)
    if (finishedBuild == null) {
      logger.debug("Event: " + DeploymentsStatusPublisher.Event.INTERRUPTED.getName() + ", cannot find finished build for build " + LogUtil.describe(build))
      return
    }
    runForEveryPublisher(DeploymentsStatusPublisher.Event.INTERRUPTED, buildType, build, object : PublishTask {
      @Throws(PublisherException::class)
      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
        return publisher.buildInterrupted(finishedBuild, revision)
      }
    })
  }

  override fun buildChangedStatus(build: SRunningBuild, oldStatus: Status, newStatus: Status) {
    if (oldStatus.isFailed || !newStatus.isFailed) // we are supposed to report failures only
    {
      return
    }
    val buildType = getBuildType(DeploymentsStatusPublisher.Event.FAILURE_DETECTED, build) ?: return
    runForEveryPublisher(DeploymentsStatusPublisher.Event.FAILURE_DETECTED, buildType, build, object : PublishTask {
      @Throws(PublisherException::class)
      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
        return publisher.buildFailureDetected(build, revision)
      }
    })
  }

  override fun buildProblemsChanged(build: SBuild, before: List<BuildProblemData>, after: List<BuildProblemData>) {
    val buildType = getBuildType(DeploymentsStatusPublisher.Event.MARKED_AS_SUCCESSFUL, build) ?: return
    if (before.isNotEmpty() && after.isEmpty()) {
      runForEveryPublisher(DeploymentsStatusPublisher.Event.MARKED_AS_SUCCESSFUL, buildType, build, object : PublishTask {
        @Throws(PublisherException::class)
        override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
          return publisher.buildMarkedAsSuccessful(build, revision, isBuildInProgress(build))
        }
      })
    }
  }

  override fun buildTypeAddedToQueue(build: SQueuedBuild) {
    val buildType = getBuildType(DeploymentsStatusPublisher.Event.QUEUED, build) ?: return
    runForEveryPublisherQueued(DeploymentsStatusPublisher.Event.QUEUED, buildType, build, object : PublishTask {
      @Throws(PublisherException::class)
      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
        return publisher.buildQueued(build, revision)
      }
    })
  }

  override fun buildRemovedFromQueue(build: SQueuedBuild, user: User?, comment: String) {
    val buildType = getBuildType(DeploymentsStatusPublisher.Event.REMOVED_FROM_QUEUE, build) ?: return
    if (user == null) {
      return
    }
    runForEveryPublisherQueued(DeploymentsStatusPublisher.Event.REMOVED_FROM_QUEUE, buildType, build, object : PublishTask {
      @Throws(PublisherException::class)
      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
        return publisher.buildRemovedFromQueue(build, revision, user, comment)
      }
    })
  }

  private fun isPublishingDisabled(buildType: SBuildType): Boolean {
    val publishingEnabledParam = buildType.getParameterValue(PUBLISHING_ENABLED_PROPERTY_NAME)
    return "false" == publishingEnabledParam || !(TeamCityProperties.getBooleanOrTrue(PUBLISHING_ENABLED_PROPERTY_NAME)
      || "true" == publishingEnabledParam)
  }

  private fun logStatusNotPublished(event: DeploymentsStatusPublisher.Event, buildDescription: String, publisher: DeploymentsStatusPublisher, message: String) {
    logger.info(String.format("Event: %s, build %s, publisher %s: %s", event.getName(), buildDescription, publisher.toString(), message))
  }

  private fun runForEveryPublisher(event: DeploymentsStatusPublisher.Event, buildType: SBuildType, build: SBuild, task: PublishTask) {
    if (build.isPersonal) {
      for (change in build.buildPromotion.personalChanges) {
        if (change.isPersonal) {
          return
        }
      }
    }
    val publishers = getPublishers(buildType)
    logger.debug("Event: " + event.getName() + ", build " + LogUtil.describe(build) + ", publishers: " + publishers.values)
    for ((_, publisher) in publishers) {
      if (!publisher.isEventSupported(event)) {
        continue
      }
      if (isPublishingDisabled(buildType)) {
        logStatusNotPublished(event, LogUtil.describe(build), publisher, "commit status publishing is disabled")
        continue
      }
      val revisions = getBuildRevisionForVote(publisher, build)
      if (revisions.isEmpty()) {
        logStatusNotPublished(event, LogUtil.describe(build), publisher, "no compatible revisions found")
        continue
      }
      problems.clearProblem(publisher)
      for (revision in revisions) {
        runTask(event, build.buildPromotion, LogUtil.describe(build), task, publisher, revision)
      }
    }
    problems.clearObsoleteProblems(buildType, publishers.keys)
  }

  private fun runForEveryPublisherQueued(event: DeploymentsStatusPublisher.Event, buildType: SBuildType, build: SQueuedBuild, task: PublishTask) {
    if (build.isPersonal) {
      for (change in build.buildPromotion.personalChanges) {
        if (change.isPersonal) {
          return
        }
      }
    }
    val publishers = getPublishers(buildType)
    logger.debug("Event: " + event.getName() + ", build " + LogUtil.describe(build) + ", publishers: " + publishers.values)
    for ((_, publisher) in publishers) {
      if (!publisher.isEventSupported(event)) {
        continue
      }
      if (isPublishingDisabled(buildType)) {
        logStatusNotPublished(event, LogUtil.describe(build), publisher, "commit status publishing is disabled")
        continue
      }
      val revisions = getQueuedBuildRevisionForVote(buildType, publisher, build)
      if (revisions.isEmpty()) {
        logStatusNotPublished(event, LogUtil.describe(build), publisher, "no compatible revisions found")
        continue
      }
      problems.clearProblem(publisher)
      for (revision in revisions) {
        runTask(event, build.buildPromotion, LogUtil.describe(build), task, publisher, revision)
      }
    }
    problems.clearObsoleteProblems(buildType, publishers.keys)
  }

  private fun runTask(
    event: DeploymentsStatusPublisher.Event,
    promotion: BuildPromotion,
    buildDescription: String,
    task: PublishTask,
    publisher: DeploymentsStatusPublisher,
    revision: BuildRevision
  ) {
    try {
      task.run(publisher, revision)
    } catch (t: Throwable) {
      problems.reportProblem(String.format("Commit Status Publisher has failed to publish %s status", event.getName()), publisher, buildDescription, null, t, logger)
      if (shouldFailBuild(publisher.getBuildType())) {
        val problemId = "deploymentsStatusPublisher." + publisher.id + "." + revision.root.id
        val problemDescription = if (t is PublisherException) t.message else t.toString()
        val buildProblem = BuildProblemData.createBuildProblem(problemId, "deploymentsStatusPublisherProblem", problemDescription)
        (promotion as BuildPromotionEx).addBuildProblem(buildProblem)
      }
    }
  }

  private fun getPublishers(buildType: SBuildType): Map<String?, DeploymentsStatusPublisher> {
    val publishers: MutableMap<String?, DeploymentsStatusPublisher> = LinkedHashMap()
    for (buildFeatureDescriptor in buildType.resolvedSettings.buildFeatures) {
      val buildFeature = buildFeatureDescriptor.buildFeature
      if (buildFeature is DeploymentsStatusPublisherFeature) {
        val featureId = buildFeatureDescriptor.id
        val publisher = publisherManager.createPublisher(buildType, featureId, buildFeatureDescriptor.parameters)
        if (publisher != null) {
          publishers[featureId] = publisher
        }
      }
    }
    return publishers
  }

  private fun getBuildRevisionForVote(publisher: DeploymentsStatusPublisher, build: SBuild): List<BuildRevision> {
    if (build.buildPromotion.isFailedToCollectChanges) {
      return emptyList()
    }
    val vcsRootId = publisher.getVcsRootId()
    if (vcsRootId == null) {
      val revisions: MutableList<BuildRevision> = ArrayList()
      for (revision in build.revisions) {
        if (publisher.isPublishingForRevision(revision)) {
          revisions.add(revision)
        }
      }
      return revisions
    }
    for (revision in build.revisions) {
      val root = revision.root.parent
      if (vcsRootId == root.externalId || vcsRootId == root.id.toString()) {
        return listOf(revision)
      }
    }
    return emptyList()
  }

  private fun getQueuedBuildRevisionForVote(
    buildType: SBuildType,
    publisher: DeploymentsStatusPublisher,
    build: SQueuedBuild
  ): List<BuildRevision> {
    val p = build.buildPromotion
    val b = p.associatedBuild
    if (b != null) {
      val revisions = getBuildRevisionForVote(publisher, b)
      if (revisions.isNotEmpty()) {
        return revisions
      }
    }
    val branchName = getBranchName(p)
    val branch = (buildType as BuildTypeEx).getBranch(branchName)
    return getBuildRevisionForVote(publisher, branch.dummyBuild)
  }

  private fun getBranchName(p: BuildPromotion): String {
    val b = p.branch ?: return Branch.DEFAULT_BRANCH_NAME
    return b.name
  }

  private fun getBuildType(event: DeploymentsStatusPublisher.Event, build: SBuild): SBuildType? {
    val buildType = build.buildType
    if (buildType == null) {
      logger.debug("Event: " + event.getName() + ", cannot find buildType for build " + LogUtil.describe(build))
    }
    return buildType
  }

  private fun getBuildType(event: DeploymentsStatusPublisher.Event, build: SQueuedBuild): SBuildType? {
    return try {
      build.buildType
    } catch (e: BuildTypeNotFoundException) {
      logger.debug("Event: " + event.getName() + ", cannot find buildType for build " + LogUtil.describe(build))
      null
    }
  }

  private interface PublishTask {

    @Throws(PublisherException::class)
    fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean
  }

  private fun isBuildInProgress(build: SBuild): Boolean {
    return runningBuilds.findRunningBuildById(build.buildId) != null
  }

  private fun shouldFailBuild(buildType: SBuildType): Boolean {
    return java.lang.Boolean.valueOf(buildType.parameters["teamcity.deploymentsStatusPublisher.failBuildOnPublishError"])
  }

  companion object {

    const val PUBLISHING_ENABLED_PROPERTY_NAME = "teamcity.deploymentsStatusPublisher.enabled"
  }

  init {
    events.addListener(this)
  }
}
