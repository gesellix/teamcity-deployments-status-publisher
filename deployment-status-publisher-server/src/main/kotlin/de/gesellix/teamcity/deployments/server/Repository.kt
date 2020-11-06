package de.gesellix.teamcity.deployments.server

import com.intellij.openapi.util.Pair

class Repository(owner: String, repo: String) : Pair<String, String>(owner, repo) {

  fun owner(): String {
    return first
  }

  fun repositoryName(): String {
    return second
  }
}
