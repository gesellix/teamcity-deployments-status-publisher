package de.gesellix.github.client.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CommitCommentRequest(
  var body: String = "",
) {

  var path: String? = null
  var position: Int? = null
}
