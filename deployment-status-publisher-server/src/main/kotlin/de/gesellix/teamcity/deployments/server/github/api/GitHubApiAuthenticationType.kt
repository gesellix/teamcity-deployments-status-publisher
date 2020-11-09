package de.gesellix.teamcity.deployments.server.github.api

import jetbrains.buildServer.util.StringUtil

enum class GitHubApiAuthenticationType(val value: String) {
  TOKEN_AUTH("token");

  companion object {

    fun parse(value: String?): GitHubApiAuthenticationType {
      //migration
      if (value == null || StringUtil.isEmptyOrSpaces(value)) return TOKEN_AUTH
      for (v in values()) {
        if (v.value == value) return v
      }
      throw IllegalArgumentException("Failed to parse GitHubApiAuthenticationType: $value")
    }
  }
}
