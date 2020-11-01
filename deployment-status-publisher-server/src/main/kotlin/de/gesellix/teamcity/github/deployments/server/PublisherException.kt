package de.gesellix.teamcity.github.deployments.server

open class PublisherException : Exception {
  constructor(message: String) : super(message)
  constructor(message: String, cause: Throwable?) : super(message, cause)
}
