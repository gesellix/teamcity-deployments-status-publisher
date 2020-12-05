package de.gesellix.teamcity.deployments.server

import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisher.Event.STARTING
import de.gesellix.teamcity.deployments.server.common.Util
import jetbrains.buildServer.serverSide.BuildRevision
import jetbrains.buildServer.serverSide.BuildStartContext
import jetbrains.buildServer.serverSide.BuildStartContextProcessor

class DeploymentInitiatingBuildStartContextProcessor(
  private val taskRunner: DeploymentsStatusPublishingTaskRunner,
) : BuildStartContextProcessor {

  val logger by logger(DeploymentInitiatingBuildStartContextProcessor::class.java.name)

  private val util = Util()

  override fun updateParameters(context: BuildStartContext) {
    val buildType = util.getBuildType(STARTING, context.build) ?: return
    // TODO return silently?!
    taskRunner.runForEveryPublisher(STARTING, buildType, context.build, object : PublishTask {
      @Throws(PublisherException::class)
      override fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean {
        if (publisher.isCreatingDeploymentEnabled()) {
          val result = publisher.buildStarting(context.build, revision)
          // TODO: if (!deployment) {addProblem()}
          context.addSharedParameter(DEPLOYMENT_ID_PARAM_KEY, result ?: "")
        }
        return publisher.buildStarted(context.build, revision)
      }
    })
  }
}
