package de.gesellix.teamcity.deployments.server

import jetbrains.buildServer.serverSide.BuildRevision

interface PublishTask {

  @Throws(PublisherException::class)
  fun run(publisher: DeploymentsStatusPublisher, revision: BuildRevision): Boolean
}
