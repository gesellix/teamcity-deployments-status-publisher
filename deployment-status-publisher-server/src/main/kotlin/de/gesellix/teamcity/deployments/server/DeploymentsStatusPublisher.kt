package de.gesellix.teamcity.deployments.server

import jetbrains.buildServer.serverSide.BuildRevision
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SFinishedBuild
import jetbrains.buildServer.serverSide.SQueuedBuild
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.users.User

interface DeploymentsStatusPublisher {

  @Throws(PublisherException::class)
  fun buildQueued(build: SQueuedBuild, revision: BuildRevision): Boolean

  @Throws(PublisherException::class)
  fun buildRemovedFromQueue(build: SQueuedBuild, revision: BuildRevision, user: User?, comment: String?): Boolean

  @Throws(PublisherException::class)
  fun buildStarting(build: SRunningBuild, revision: BuildRevision): String?

  @Throws(PublisherException::class)
  fun buildStarted(build: SRunningBuild, revision: BuildRevision): Boolean

  @Throws(PublisherException::class)
  fun buildFinished(build: SFinishedBuild, revision: BuildRevision): Boolean

  @Throws(PublisherException::class)
  fun buildCommented(build: SBuild, revision: BuildRevision, user: User?, comment: String?, buildInProgress: Boolean): Boolean

  @Throws(PublisherException::class)
  fun buildInterrupted(build: SFinishedBuild, revision: BuildRevision): Boolean

  @Throws(PublisherException::class)
  fun buildFailureDetected(build: SRunningBuild, revision: BuildRevision): Boolean

  @Throws(PublisherException::class)
  fun buildMarkedAsSuccessful(build: SBuild, revision: BuildRevision, buildInProgress: Boolean): Boolean

  fun getBuildFeatureId(): String

  fun getBuildType(): SBuildType

  fun getVcsRootId(): String?

  fun isCreatingDeploymentEnabled(): Boolean

  override fun toString(): String

  val id: String

  fun getSettings(): DeploymentsStatusPublisherSettings

  fun isPublishingForRevision(revision: BuildRevision): Boolean

  fun setConnectionTimeout(timeout: Int)

  fun isEventSupported(event: Event): Boolean

  enum class Event constructor(name: String, eventPriority: EventPriority = EventPriority.CONSEQUENT) {
    STARTING("buildStarting", EventPriority.FIRST),
    STARTED("buildStarted", EventPriority.FIRST),
    FINISHED("buildFinished"),
    QUEUED("buildQueued", EventPriority.FIRST),
    REMOVED_FROM_QUEUE("buildRemovedFromQueue", EventPriority.FIRST),
    COMMENTED("buildCommented", EventPriority.ANY),
    INTERRUPTED("buildInterrupted"),
    FAILURE_DETECTED("buildFailureDetected"),
    MARKED_AS_SUCCESSFUL("buildMarkedAsSuccessful");

    private val myName: String = "$PUBLISHING_TASK_PREFIX.$name"

    fun getName(): String {
      return myName
    }

    private val myEventPriority: EventPriority = eventPriority

    val isFirstTask: Boolean
      get() = myEventPriority == EventPriority.FIRST

    val isConsequentTask: Boolean
      get() = myEventPriority == EventPriority.CONSEQUENT

    private enum class EventPriority {
      FIRST,  // the event of this priority will not be accepted if any of the previous events are of the type CONSEQUENT
      ANY,  // accepted at any time, will not prevent any events to be accepted after it
      CONSEQUENT // accepted at any time too, but will prevent events of priority FIRST to be accepted after it
    }
  }

  companion object {

    private const val PUBLISHING_TASK_PREFIX = "publishDeploymentsStatus"
  }
}
