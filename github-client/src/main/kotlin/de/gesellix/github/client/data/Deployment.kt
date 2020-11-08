package de.gesellix.github.client.data

data class Deployment(
  val id: Long,
  val url: String,
  val sha: String,
  val ref: String,
  val task: String,
  val payload: Any, //json
  val description: String,
  val environment: String,
  val creator: User,
  // TODO consider something like `@Json(name = "created_at") val createdAt: String`
  val created_at: String, //timestamp
  val updated_at: String, //timestamp
  val statuses_url: String,
  val repository_url: String,
  val nodeId: String,
)
