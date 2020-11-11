package de.gesellix.teamcity.deployments.server

import com.intellij.openapi.diagnostic.Logger
import java.net.URI
import java.net.URISyntaxException
import java.util.regex.Pattern

object GitRepositoryParser {

  private val LOG = Logger.getInstance(GitRepositoryParser::class.java.name)

  //git@host:user/repo
  private val SCP_PATTERN = Pattern.compile("[^:@/]+@[^:]+:/?([^/]+)/(.+[^/])/?")
  private val SCP_PATTERN_SLASHES = Pattern.compile("[^:@/]+@[^:]+:/?(.+)/([^/]+)/?")

  //ssh://git@host/user/repo
  private val SSH_PATTERN = Pattern.compile("ssh://(?:[^:@/]+@)?[^:]+(?::[0-9]+)?[:/]([^/:]+)/(.+[^/])/?")
  private val SSH_PATTERN_SLASHES = Pattern.compile("ssh://(?:[^:@/]+@)?[^:/]+(?::[0-9]+)?[:/]([^:]+)/([^/]+)/?")

  @JvmOverloads
  @JvmStatic
  fun parseRepository(uri: String, pathPrefix: String? = null): Repository? {
    var m = (if (null == pathPrefix) SCP_PATTERN else SCP_PATTERN_SLASHES).matcher(uri)
    if (!m.matches()) {
      m = (if (null == pathPrefix) SSH_PATTERN else SSH_PATTERN_SLASHES).matcher(uri)
    }
    if (m.matches()) {
      val userGroup = m.group(1)
      var repo = m.group(2)
      if (repo.endsWith(".git")) repo = repo.substring(0, repo.length - 4)
      return Repository(userGroup, repo)
    }
    val url: URI = try {
      URI(uri)
    } catch (e: URISyntaxException) {
      LOG.warn("Cannot parse Git repository url $uri", e)
      return null
    }
    var path = url.path
    if (path != null) {
      if (path.endsWith("/")) path = path.substring(0, path.length - 1)
      var repo: String
      var owner: String
      val lastSlash = path.lastIndexOf("/")
      if (lastSlash > 0) {
        repo = path.substring(lastSlash + 1)
        if (repo.endsWith(".git")) repo = repo.substring(0, repo.length - 4)
        val ownerStart = pathPrefix?.length ?: path.lastIndexOf("/", lastSlash - 1)
        if (ownerStart >= 0) {
          owner = path.substring(ownerStart, lastSlash)
          if (owner.startsWith("/")) owner = owner.substring(1)
          return Repository(owner, repo)
        }
      }
    }
    LOG.warn("Cannot parse Git repository url $uri")
    return null
  }
}
