package de.gesellix.teamcity.github.deployments.server

import jetbrains.buildServer.ExtensionsCollection
import jetbrains.buildServer.ExtensionsProvider

class PublisherManager(extensionsProvider: ExtensionsProvider) {

  private val publisherSettings: ExtensionsCollection<GitHubDeploymentsStatusPublisherSettings?> = extensionsProvider.getExtensionsCollection(GitHubDeploymentsStatusPublisherSettings::class.java)

//  fun createPublisher(buildType: SBuildType, buildFeatureId: String, params: Map<String?, String?>): CommitStatusPublisher? {
//    val publisherId = params[PUBLISHER_ID_PARAM] ?: return null
//    val settings: CommitStatusPublisherSettings = findSettings(publisherId) ?: return null
//    return settings.createPublisher(buildType, buildFeatureId, params)
//  }

  fun findSettings(publisherId: String): GitHubDeploymentsStatusPublisherSettings? {
    return publisherSettings.extensions.stream().filter { s: GitHubDeploymentsStatusPublisherSettings? -> publisherId == s?.getId() }.findFirst().orElse(null)
  }

  val allPublisherSettings: MutableList<GitHubDeploymentsStatusPublisherSettings>
    get() {
      val settings: MutableList<GitHubDeploymentsStatusPublisherSettings> = ArrayList()
      for (s in publisherSettings.extensions) {
        if (s!!.isEnabled()) settings.add(s)
      }
      settings.sortWith(Comparator { o1, o2 -> o1.getName().compareTo(o2.getName()) })
      return settings
    }
}
