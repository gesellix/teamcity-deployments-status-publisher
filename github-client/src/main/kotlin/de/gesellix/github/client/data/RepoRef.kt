package de.gesellix.github.client.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class RepoRef(
  val sha: String,
  val ref: String,
  val label: String,
)
