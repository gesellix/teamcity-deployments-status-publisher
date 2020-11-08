package de.gesellix.github.client.data

class DeploymentStatusRequest {

  lateinit var state: String
  var description: String? = null
  lateinit var environment: String
}
