package de.gesellix.github.client.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Repository(
  val id: Long,
  val name: String,
  val permissions: Permissions,
)
