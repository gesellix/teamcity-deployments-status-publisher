package de.gesellix.github.client.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PullRequest(
  val id: Int = 0,
  var url: String = "",
  var head: RepoRef? = null,
  var base: RepoRef? = null,
)
