package de.gesellix.teamcity.deployments.server

import com.google.common.util.concurrent.Striped
import jetbrains.buildServer.serverSide.BuildRevision
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SFinishedBuild
import jetbrains.buildServer.serverSide.SQueuedBuild
import jetbrains.buildServer.serverSide.SRunningBuild
import jetbrains.buildServer.users.User
import jetbrains.buildServer.vcs.VcsRoot
import java.util.concurrent.locks.Lock

abstract class DeploymentsStatusPublisherBase protected constructor(
  private val settings: DeploymentsStatusPublisherSettings,
  private val buildType: SBuildType,
  private val buildFeatureId: String,
  protected val params: Map<String, String>,
  private val problems: DeploymentsStatusPublisherProblems
) : DeploymentsStatusPublisher {

  private var connectionTimeout = DEFAULT_CONNECTION_TIMEOUT
  private val locks = Striped.lazyWeakLock(100)

  @Throws(PublisherException::class)
  override fun buildQueued(build: SQueuedBuild, revision: BuildRevision): Boolean {
    return false
  }

  @Throws(PublisherException::class)
  override fun buildRemovedFromQueue(build: SQueuedBuild, revision: BuildRevision, user: User?, comment: String?): Boolean {
    return false
  }

  @Throws(PublisherException::class)
  override fun buildStarting(build: SRunningBuild, revision: BuildRevision): String? {
    return null
  }

  @Throws(PublisherException::class)
  override fun buildStarted(build: SRunningBuild, revision: BuildRevision): Boolean {
    return false
  }

  @Throws(PublisherException::class)
  override fun buildFinished(build: SFinishedBuild, revision: BuildRevision): Boolean {
    return false
  }

  @Throws(PublisherException::class)
  override fun buildCommented(build: SBuild, revision: BuildRevision, user: User?, comment: String?, buildInProgress: Boolean): Boolean {
    return false
  }

  @Throws(PublisherException::class)
  override fun buildInterrupted(build: SFinishedBuild, revision: BuildRevision): Boolean {
    return false
  }

  @Throws(PublisherException::class)
  override fun buildFailureDetected(build: SRunningBuild, revision: BuildRevision): Boolean {
    return false
  }

  @Throws(PublisherException::class)
  override fun buildMarkedAsSuccessful(build: SBuild, revision: BuildRevision, buildInProgress: Boolean): Boolean {
    return false
  }

  open fun getLocks(): Striped<Lock> {
    return locks
  }

  open fun getConnectionTimeout(): Int {
    return connectionTimeout
  }

  override fun setConnectionTimeout(timeout: Int) {
    connectionTimeout = timeout
  }

  override fun getVcsRootId(): String? {
    return params[VCS_ROOT_ID_PARAM]
  }

  override fun isCreatingDeploymentEnabled(): Boolean {
    return params[CREATE_DEPLOYMENT_PARAM] == "true"
  }

  override fun getSettings(): DeploymentsStatusPublisherSettings {
    return settings
  }

  override fun isPublishingForRevision(revision: BuildRevision): Boolean {
    val vcsRoot: VcsRoot = revision.root
    return getSettings().isPublishingForVcsRoot(vcsRoot)
  }

  override fun isEventSupported(event: DeploymentsStatusPublisher.Event): Boolean {
    return settings.isEventSupported(event)
  }

  override fun getBuildType(): SBuildType {
    return buildType
  }

  override fun getBuildFeatureId(): String {
    return buildFeatureId
  }

  open fun getProblems(): DeploymentsStatusPublisherProblems {
    return problems
  }

  companion object {

    private const val DEFAULT_CONNECTION_TIMEOUT = 300 * 1000
  }
}
