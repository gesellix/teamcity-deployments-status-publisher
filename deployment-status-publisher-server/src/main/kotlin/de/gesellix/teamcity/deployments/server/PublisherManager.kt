package de.gesellix.teamcity.deployments.server

import jetbrains.buildServer.ExtensionsProvider
import jetbrains.buildServer.serverSide.SBuildType

class PublisherManager {

  private val publisherSettingsById: Map<String, DeploymentsStatusPublisherSettings>

  constructor(extensionsProvider: ExtensionsProvider) : this(extensionsProvider.getExtensionsCollection(DeploymentsStatusPublisherSettings::class.java).extensions)

  constructor(publisherSettings: Collection<DeploymentsStatusPublisherSettings?>) {
    this.publisherSettingsById = publisherSettings.filterNotNull().associateBy { it.getId() }
  }

  fun createPublisher(buildType: SBuildType, buildFeatureId: String, params: Map<String, String>): DeploymentsStatusPublisher? {
    val publisherId = params[PUBLISHER_ID_PARAM] ?: return null
    val settings: DeploymentsStatusPublisherSettings = findSettings(publisherId) ?: return null
    return settings.createPublisher(buildType, buildFeatureId, params)
  }

  fun findSettings(publisherId: String): DeploymentsStatusPublisherSettings? {
    return publisherSettingsById[publisherId]
  }

  val allPublisherSettings: MutableList<DeploymentsStatusPublisherSettings>
    get() {
      val settings: MutableList<DeploymentsStatusPublisherSettings> = ArrayList()
      for (s in publisherSettingsById.values) {
        if (s.isEnabled()) {
          settings.add(s)
        }
      }
      settings.sortWith(Comparator { o1, o2 -> o1.getName().compareTo(o2.getName()) })
      return settings
    }
}
