package de.gesellix.teamcity.deployments.server

/**
 * @author anton.zamolotskikh, 21/12/16.
 */
class HttpPublisherException : PublisherException {

  constructor(message: String?) : super(message!!) {}
  constructor(message: String?, t: Throwable?) : super(message!!, t) {}

  @JvmOverloads
  constructor(statusCode: Int, reason: String?, message: String? = null) : super(String.format("%sresponse code: %d, reason: %s", if (null == message) "" else "$message, ", statusCode, reason)) {
  }
}
