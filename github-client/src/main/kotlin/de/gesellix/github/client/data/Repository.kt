package de.gesellix.github.client.data

data class Repository(
  val id: Long,
  val name: String,
  val permissions: Permissions,
)
