package de.gesellix.github.client.data

class DeploymentRequest(var ref: String, var environment: String) {

  var task = "deploy"
  var description: String? = null
  var payload: String? = null

  // TODO make configurable
  var transientEnvironment = false

  // TODO make configurable
  var requiredContexts = emptyArray<String>()
}
