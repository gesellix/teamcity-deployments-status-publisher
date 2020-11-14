package de.gesellix.teamcity.deployments.server

import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisher.Event.STARTING
import de.gesellix.teamcity.deployments.server.common.Util
import jetbrains.buildServer.serverSide.BuildRevision
import jetbrains.buildServer.serverSide.BuildStartContext
import jetbrains.buildServer.serverSide.BuildStartContextProcessor
import jetbrains.buildServer.serverSide.SRunningBuild

class DeploymentInitiatingBuildStartContextProcessor(
  private val taskRunner: DeploymentsStatusPublishingTaskRunner
) : BuildStartContextProcessor {

  val logger by logger(DeploymentInitiatingBuildStartContextProcessor::class.java.name)

  private val util = Util()

  override fun updateParameters(context: BuildStartContext) {
    logger.info("updateParameters on build start")
    if (shouldCreateDeployment(context.build)) {
      // TODO return silently?!
      val buildType = util.getBuildType(STARTING, context.build) ?: return
      taskRunner.runForEveryPublisher(STARTING, buildType, context.build, object : PublishTask {
        @Throws(PublisherException::class)
        override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
          val result = publisher.buildStarting(context.build, revision)
          // TODO: if (!deployment) {addProblem()}
          context.addSharedParameter("TC_DEPLOYMENTS_PLUGIN_DEPLOY_ID", result ?: "")
          return true
        }
      })
    }
  }

  fun shouldCreateDeployment(build: SRunningBuild): Boolean {
    return false
  }
}
