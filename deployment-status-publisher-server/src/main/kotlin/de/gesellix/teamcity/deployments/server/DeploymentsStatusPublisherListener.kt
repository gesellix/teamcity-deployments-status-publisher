package de.gesellix.teamcity.deployments.server

import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisher.Event.COMMENTED
import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisher.Event.FAILURE_DETECTED
import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisher.Event.FINISHED
import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisher.Event.INTERRUPTED
import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisher.Event.MARKED_AS_SUCCESSFUL
import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisher.Event.QUEUED
import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisher.Event.REMOVED_FROM_QUEUE
import de.gesellix.teamcity.deployments.server.common.Util
import jetbrains.buildServer.BuildProblemData
import jetbrains.buildServer.messages.Status
import jetbrains.buildServer.serverSide.BuildHistory
import jetbrains.buildServer.serverSide.BuildRevision
import jetbrains.buildServer.serverSide.BuildServerAdapter
import jetbrains.buildServer.serverSide.BuildServerListener
import jetbrains.buildServer.serverSide.RunningBuildsManager
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SQueuedBuild
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.serverSide.impl.LogUtil
import jetbrains.buildServer.users.User
import jetbrains.buildServer.util.EventDispatcher

class DeploymentsStatusPublisherListener(
  events: EventDispatcher<BuildServerListener?>,
  private val buildHistory: BuildHistory,
  private val runningBuilds: RunningBuildsManager,
  private val taskRunner: DeploymentsStatusPublishingTaskRunner
) : BuildServerAdapter() {

  private val logger by logger(DeploymentsStatusPublisherListener::class.java.name)

  private val util = Util()

// TODO Remove or fix.
// This event happens before BuildStartContextProcessor::updateParameters.
// See https://youtrack.jetbrains.com/issue/TW-68592 for details.
//  override fun buildStarted(build: SRunningBuild) {
//    val buildType = util.getBuildType(STARTED, build) ?: return
//    taskRunner.runForEveryPublisher(STARTED, buildType, build, object : PublishTask {
//      @Throws(PublisherException::class)
//      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
//        return publisher.buildStarted(build, revision)
//      }
//    })
//  }
//  override fun changesLoaded(@NotNull build: SRunningBuild) {
//    logger.info("## changesLoaded: $build")
//  }

  override fun buildFinished(build: SRunningBuild) {
    val buildType = util.getBuildType(FINISHED, build) ?: return
    val finishedBuild = buildHistory.findEntry(build.buildId)
    if (finishedBuild == null) {
      logger.debug("Event: " + FINISHED + ", cannot find finished build for build " + LogUtil.describe(build))
      return
    }
    taskRunner.runForEveryPublisher(FINISHED, buildType, build, object : PublishTask {
      @Throws(PublisherException::class)
      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
        return publisher.buildFinished(finishedBuild, revision)
      }
    })
  }

  override fun buildCommented(build: SBuild, user: User?, comment: String?) {
    val buildType = util.getBuildType(COMMENTED, build) ?: return
    taskRunner.runForEveryPublisher(COMMENTED, buildType, build, object : PublishTask {
      @Throws(PublisherException::class)
      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
        return publisher.buildCommented(build, revision, user, comment, isBuildInProgress(build))
      }
    })
  }

  override fun buildInterrupted(build: SRunningBuild) {
    val buildType = util.getBuildType(INTERRUPTED, build) ?: return
    val finishedBuild = buildHistory.findEntry(build.buildId)
    if (finishedBuild == null) {
      logger.debug("Event: " + INTERRUPTED.getName() + ", cannot find finished build for build " + LogUtil.describe(build))
      return
    }
    taskRunner.runForEveryPublisher(INTERRUPTED, buildType, build, object : PublishTask {
      @Throws(PublisherException::class)
      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
        return publisher.buildInterrupted(finishedBuild, revision)
      }
    })
  }

  override fun buildChangedStatus(build: SRunningBuild, oldStatus: Status, newStatus: Status) {
    // we are supposed to report failures only
    if (oldStatus.isFailed || !newStatus.isFailed) {
      return
    }
    val buildType = util.getBuildType(FAILURE_DETECTED, build) ?: return
    taskRunner.runForEveryPublisher(FAILURE_DETECTED, buildType, build, object : PublishTask {
      @Throws(PublisherException::class)
      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
        return publisher.buildFailureDetected(build, revision)
      }
    })
  }

  override fun buildProblemsChanged(build: SBuild, before: List<BuildProblemData>, after: List<BuildProblemData>) {
    val buildType = util.getBuildType(MARKED_AS_SUCCESSFUL, build) ?: return
    if (before.isNotEmpty() && after.isEmpty()) {
      taskRunner.runForEveryPublisher(MARKED_AS_SUCCESSFUL, buildType, build, object : PublishTask {
        @Throws(PublisherException::class)
        override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
          return publisher.buildMarkedAsSuccessful(build, revision, isBuildInProgress(build))
        }
      })
    }
  }

  override fun buildTypeAddedToQueue(build: SQueuedBuild) {
    val buildType = util.getBuildType(QUEUED, build) ?: return
    taskRunner.runForEveryPublisherQueued(QUEUED, buildType, build, object : PublishTask {
      @Throws(PublisherException::class)
      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
        return publisher.buildQueued(build, revision)
      }
    })
  }

  override fun buildRemovedFromQueue(build: SQueuedBuild, user: User?, comment: String) {
    val buildType = util.getBuildType(REMOVED_FROM_QUEUE, build) ?: return
    if (user == null) {
      return
    }
    taskRunner.runForEveryPublisherQueued(REMOVED_FROM_QUEUE, buildType, build, object : PublishTask {
      @Throws(PublisherException::class)
      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
        return publisher.buildRemovedFromQueue(build, revision, user, comment)
      }
    })
  }

  private fun isBuildInProgress(build: SBuild): Boolean {
    return runningBuilds.findRunningBuildById(build.buildId) != null
  }

  init {
    events.addListener(this)
  }
}
