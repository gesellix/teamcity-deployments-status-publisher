package de.gesellix.github.client.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeploymentRequest(val ref: String) {

  var environment: String = "production"
  var task = "deploy"
  var description: String? = null
  var payload: String? = null
  var transient_environment = false
  var required_contexts = emptyArray<String>()
}
