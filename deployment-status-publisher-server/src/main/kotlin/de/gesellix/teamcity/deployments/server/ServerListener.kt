package de.gesellix.teamcity.deployments.server

import de.gesellix.teamcity.deployments.server.DeploymentsStatusPublisherFeature.Companion.BUILD_FEATURE_NAME
import jetbrains.buildServer.serverSide.BuildTypeSettings
import jetbrains.buildServer.serverSide.ConfigAction
import jetbrains.buildServer.serverSide.ConfigActionsServerAdapter
import jetbrains.buildServer.serverSide.ConfigActionsServerListener
import jetbrains.buildServer.serverSide.CopiedObjects
import jetbrains.buildServer.serverSide.CustomSettingsMapper
import jetbrains.buildServer.serverSide.impl.VcsLabelingBuildFeature.VCS_ROOT_ID_PARAM
import jetbrains.buildServer.util.EventDispatcher
import jetbrains.buildServer.vcs.SVcsRoot
import java.util.*

class ServerListener(dispatcher: EventDispatcher<ConfigActionsServerListener?>) : ConfigActionsServerAdapter(), CustomSettingsMapper {

  override fun vcsRootExternalIdChanged(cause: ConfigAction, vcsRoot: SVcsRoot, oldExternalId: String, newExternalId: String) {
    super.vcsRootExternalIdChanged(cause, vcsRoot, oldExternalId, newExternalId)
    val vcsRootProject = vcsRoot.project
    for (bt in vcsRootProject.buildTypes) {
      if (updateFeatures(oldExternalId, null, newExternalId, bt)) {
        bt.persist(cause)
      }
    }
    for (tpl in vcsRootProject.buildTypeTemplates) {
      if (updateFeatures(oldExternalId, null, newExternalId, tpl)) {
        tpl.persist(cause)
      }
    }
  }

  override fun mapData(copiedObjects: CopiedObjects) {
    val mappedRoots = copiedObjects.copiedVcsRootsMap
    if (mappedRoots.isEmpty()) return
    for (bt in copiedObjects.copiedSettingsMap.values) {
      for ((key, value) in mappedRoots) {
        updateFeatures(key.externalId, key.id, value.externalId, bt)
      }
    }
  }

  companion object {

    private fun updateFeatures(oldExternalId: String, oldInternalId: Long?, newExternalId: String, btSettings: BuildTypeSettings): Boolean {
      var updated = false
      for (bf in btSettings.getBuildFeaturesOfType(BUILD_FEATURE_NAME)) {
        val vcsRootId = bf.parameters[VCS_ROOT_ID_PARAM]
        val internalId: Long? = try {
          java.lang.Long.valueOf(vcsRootId)
        } catch (ex: NumberFormatException) {
          null
        }
        if (oldExternalId == vcsRootId || null != oldInternalId && oldInternalId == internalId) {
          val params: MutableMap<String, String> = HashMap(bf.parameters)
          params[VCS_ROOT_ID_PARAM] = newExternalId
          btSettings.updateBuildFeature(bf.id, bf.type, params)
          updated = true
        }
      }
      return updated
    }
  }

  init {
    dispatcher.addListener(this)
  }
}