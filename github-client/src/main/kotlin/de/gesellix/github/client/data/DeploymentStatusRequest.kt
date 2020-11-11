package de.gesellix.github.client.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeploymentStatusRequest(val state: DeploymentStatusState) {

  var log_url: String? = null
  var description: String? = null
  var environment: String? = null
}
