package de.gesellix.teamcity.github.deployments.server

class PublisherException : Exception {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable?) : super(message, cause)
}
