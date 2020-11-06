package de.gesellix.teamcity.deployments.server.github.api

import jetbrains.buildServer.util.StringUtil

enum class GitHubApiAuthenticationType(val value: String) {
  TOKEN_AUTH("token"),
  PASSWORD_AUTH("password");

  companion object {

    fun parse(value: String?): GitHubApiAuthenticationType {
      //migration
      if (value == null || StringUtil.isEmptyOrSpaces(value)) return PASSWORD_AUTH
      for (v in values()) {
        if (v.value == value) return v
      }
      throw IllegalArgumentException("Failed to parse GitHubApiAuthenticationType: $value")
    }
  }
}
