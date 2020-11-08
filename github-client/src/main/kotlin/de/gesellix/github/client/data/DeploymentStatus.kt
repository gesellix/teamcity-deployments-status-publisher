package de.gesellix.github.client.data

data class DeploymentStatus(
  val id: Long,
  // TODO consider something like `@Json(name = "created_at") val createdAt: String`
  val created_at: String, //timestamp
)
