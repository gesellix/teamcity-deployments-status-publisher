package de.gesellix.teamcity.deployments.server

import com.intellij.openapi.diagnostic.Logger

const val LOG_CATEGORY = "de.gesellix.server.DEPLOYMENTS_STATUS"

fun logger(): Lazy<Logger> {
  return lazy {
    Logger.getInstance(LOG_CATEGORY)
  }
}
