package de.gesellix.github.client.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Permissions(
  var admin: Boolean = false,
  var push: Boolean = false,
  var pull: Boolean = false,
)
