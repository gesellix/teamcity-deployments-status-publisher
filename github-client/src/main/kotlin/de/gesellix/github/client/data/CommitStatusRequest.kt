package de.gesellix.github.client.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class CommitStatusRequest(var state: String = "") {

  var target_url: String? = null
  var description: String? = null
  var context: String? = "default"

  companion object {

    private fun truncateStringValueWithDotsAtEnd(str: String?, maxLength: Int): String? {
      if (str == null) return null
      return if (str.length > maxLength) {
        str.substring(0, maxLength - 2) + "\u2026"
      } else str
    }
  }

  init {
    this.description = truncateStringValueWithDotsAtEnd(description, 140)
  }
}
