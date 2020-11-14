package de.gesellix.teamcity.deployments.server

import jetbrains.buildServer.serverSide.Branch
import jetbrains.buildServer.serverSide.BuildPromotion
import jetbrains.buildServer.serverSide.BuildRevision
import jetbrains.buildServer.serverSide.BuildTypeEx
import jetbrains.buildServer.serverSide.SBuild
import jetbrains.buildServer.serverSide.SBuildType
import jetbrains.buildServer.serverSide.SQueuedBuild
import java.util.ArrayList
import java.util.LinkedHashMap

class PublisherService(private val publisherManager: PublisherManager) {

  val logger by logger(PublisherService::class.java.name)

  fun getPublishers(buildType: SBuildType): Map<String?, DeploymentsStatusPublisher> {
    val publishers: MutableMap<String?, DeploymentsStatusPublisher> = LinkedHashMap()
    for (buildFeatureDescriptor in buildType.resolvedSettings.buildFeatures) {
      val buildFeature = buildFeatureDescriptor.buildFeature
      if (buildFeature is DeploymentsStatusPublisherFeature) {
        val featureId = buildFeatureDescriptor.id
        val publisher = publisherManager.createPublisher(buildType, featureId, buildFeatureDescriptor.parameters)
        if (publisher != null) {
          publishers[featureId] = publisher
        }
      }
    }
    return publishers
  }

  fun getBuildRevisionForVote(publisher: DeploymentsStatusPublisher, build: SBuild): List<BuildRevision> {
    if (build.buildPromotion.isFailedToCollectChanges) {
      return emptyList()
    }
    val vcsRootId = publisher.getVcsRootId()
    if (vcsRootId == null) {
      logger.warn("fallback to find a publishing revision")
      val revisions: MutableList<BuildRevision> = ArrayList()
      for (revision in build.revisions) {
        if (publisher.isPublishingForRevision(revision)) {
          revisions.add(revision)
        }
      }
      return revisions
    }
    for (revision in build.revisions) {
      val root = revision.root.parent
      if (vcsRootId == root.externalId || vcsRootId == root.id.toString()) {
        return listOf(revision)
      }
    }
    return emptyList()
  }

  fun getQueuedBuildRevisionForVote(
    buildType: SBuildType,
    publisher: DeploymentsStatusPublisher,
    build: SQueuedBuild
  ): List<BuildRevision> {
    val promotion = build.buildPromotion
    val associatedBuild = promotion.associatedBuild
    if (associatedBuild != null) {
      val revisions = getBuildRevisionForVote(publisher, associatedBuild)
      if (revisions.isNotEmpty()) {
        return revisions
      }
    }
    val branchName = getBranchName(promotion)
    val branch = (buildType as BuildTypeEx).getBranch(branchName)
    return getBuildRevisionForVote(publisher, branch.dummyBuild)
  }

  private fun getBranchName(p: BuildPromotion): String {
    val b = p.branch ?: return Branch.DEFAULT_BRANCH_NAME
    return b.name
  }
}
