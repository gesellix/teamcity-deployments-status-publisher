package de.gesellix.github.client.data

data class CommitCommentRequest(
  var body: String = "",
) {

  var path: String? = null
  var position: Int? = null
}
