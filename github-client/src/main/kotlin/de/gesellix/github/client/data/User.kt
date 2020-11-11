package de.gesellix.github.client.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class User(
  val login: String,
  val id: Int,
)
