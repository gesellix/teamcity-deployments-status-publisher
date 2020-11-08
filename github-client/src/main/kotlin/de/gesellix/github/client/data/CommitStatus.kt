package de.gesellix.github.client.data

data class CommitStatus(
  val id: Long,
  val state: String,
  // TODO consider something like `@Json(name = "created_at") val createdAt: String`
  val created_at: String, //timestamp
)
