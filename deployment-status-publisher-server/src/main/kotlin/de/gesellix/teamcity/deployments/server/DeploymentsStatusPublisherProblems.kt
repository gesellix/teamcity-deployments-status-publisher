package de.gesellix.teamcity.deployments.server

import com.google.common.util.concurrent.Striped
import com.intellij.openapi.diagnostic.Logger
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.systemProblems.SystemProblem
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemNotification
import jetbrains.buildServer.serverSide.systemProblems.SystemProblemTicket
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DeploymentsStatusPublisherProblems(private val problems: SystemProblemNotification) {

  private val tickets = ConcurrentHashMap<String, MutableMap<String, MutableSet<SystemProblemTicket>?>?>()
  private val locks = Striped.lazyWeakLock(256)

  fun reportProblem(
    publisher: DeploymentsStatusPublisher,
    buildDescription: String,
    destination: String?,
    t: Throwable?,
    logger: Logger
  ) {
    reportProblem("Deployments Status Publisher error", publisher, buildDescription, destination, t, logger)
  }

  fun reportProblem(
    errorMessage: String,
    publisher: DeploymentsStatusPublisher,
    buildDescription: String,
    destination: String?,
    t: Throwable?,
    logger: Logger
  ) {
    val dst = if (null == destination) "" else "($destination)"
    var errorDescription = String.format("%s. Publisher: %s%s.", errorMessage, publisher.id, dst)
    val logEntry = String.format("%s. Build: %s", errorDescription, buildDescription)
    if (null != t) {
      val exMsg = t.message
      errorDescription += if (null != exMsg) {
        " $exMsg"
      } else {
        " $t"
      }
      logger.warnAndDebugDetails(logEntry, t)
    } else {
      logger.warn(logEntry)
    }
    val buildType = publisher.getBuildType()
    val lock = locks[buildType]
    lock.lock()
    try {
      val problem = SystemProblem(errorDescription, null, DEPLOYMENTS_STATUS_PUBLISHER_PROBLEM_TYPE, null)
      putTicket(buildType.internalId, publisher.getBuildFeatureId(), problems.raiseProblem(buildType, problem))
    } finally {
      lock.unlock()
    }
  }

  fun clearProblem(publisher: DeploymentsStatusPublisher) {
    val buildType = publisher.getBuildType()
    val featureId = publisher.getBuildFeatureId()
    val lock = locks[buildType]
    lock.lock()
    try {
      val btId = buildType.internalId
      if (tickets.containsKey(btId)) {
        val ticketsForPublishers: MutableMap<String, MutableSet<SystemProblemTicket>?>? = tickets[btId]
        if (ticketsForPublishers!!.containsKey(featureId)) {
          val tickets = ticketsForPublishers[featureId]
          for (ticket in tickets!!) {
            ticket.cancel()
          }
          ticketsForPublishers.remove(featureId)
        }
      }
    } finally {
      lock.unlock()
    }
  }

  fun clearObsoleteProblems(buildType: SBuildType, currentFeatureIds: Collection<String?>) {
    val lock = locks[buildType]
    lock.lock()
    try {
      val btId = buildType.internalId
      if (tickets.containsKey(btId)) {
        val ticketsForPublishers: MutableMap<String, MutableSet<SystemProblemTicket>?>? = tickets[btId]
        val featureIdsToRemove: MutableSet<String> = HashSet(ticketsForPublishers!!.keys)
        featureIdsToRemove.removeAll(currentFeatureIds)
        for (featureId in featureIdsToRemove) {
          if (ticketsForPublishers.containsKey(featureId)) {
            val tickets = ticketsForPublishers[featureId]
            for (ticket in tickets!!) {
              ticket.cancel()
            }
            ticketsForPublishers.remove(featureId)
          }
        }
      }
    } finally {
      lock.unlock()
    }
  }

  private fun putTicket(buildTypeInternalId: String, publisherBuildFeatureId: String, ticket: SystemProblemTicket) {
    val ticketsForPublishers: MutableMap<String, MutableSet<SystemProblemTicket>?>?
    if (tickets.containsKey(buildTypeInternalId)) {
      ticketsForPublishers = tickets[buildTypeInternalId]
    } else {
      ticketsForPublishers = HashMap()
      tickets[buildTypeInternalId] = ticketsForPublishers
    }
    val tickets: MutableSet<SystemProblemTicket>?
    if (ticketsForPublishers!!.containsKey(publisherBuildFeatureId)) {
      tickets = ticketsForPublishers[publisherBuildFeatureId]
    } else {
      tickets = HashSet()
      ticketsForPublishers[publisherBuildFeatureId] = tickets
    }
    tickets!!.add(ticket)
  }
}
