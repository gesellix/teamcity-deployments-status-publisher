package de.gesellix.teamcity.github.deployments.server

import com.intellij.openapi.diagnostic.Logger

const val LOG_CATEGORY = "jetbrains.buildServer.DEPLOYMENTS_STATUS"

fun logger(): Lazy<Logger> {
  return lazy {
    Logger.getInstance(LOG_CATEGORY)
  }
}
