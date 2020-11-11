package de.gesellix.github.client.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class Deployment(
  val id: Long,
) {

  var url: String? = null
  var sha: String? = null
  var ref: String? = null
  var task: String? = null
  var payload: Any? = null //json
  var description: String? = null
  var environment: String? = null
  var creator: User? = null

  // TODO consider something like `@Json(name = "created_at") val createdAt: String`
  var created_at: String? = null //timestamp
  var updated_at: String? = null //timestamp
  var statuses_url: String? = null
  var repository_url: String? = null
  var nodeId: String? = null
}
