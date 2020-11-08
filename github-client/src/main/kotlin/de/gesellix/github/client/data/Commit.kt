package de.gesellix.github.client.data

data class Commit(
  val sha: String,
  val url: String,
  val parents: Array<Commit>? = null
) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as Commit

    if (sha != other.sha) return false
    if (url != other.url) return false
    if (parents != null) {
      if (other.parents == null) return false
      if (!parents.contentEquals(other.parents)) return false
    } else if (other.parents != null) return false

    return true
  }

  override fun hashCode(): Int {
    var result = sha.hashCode()
    result = 31 * result + url.hashCode()
    result = 31 * result + (parents?.contentHashCode() ?: 0)
    return result
  }
}
