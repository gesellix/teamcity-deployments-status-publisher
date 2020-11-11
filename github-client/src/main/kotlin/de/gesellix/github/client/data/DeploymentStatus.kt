package de.gesellix.github.client.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DeploymentStatus(
  val id: Long,
  // TODO consider something like `@Json(name = "created_at") val createdAt: String`
  val created_at: String, //timestamp
  val state: DeploymentStatusState
)
