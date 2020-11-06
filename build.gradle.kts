plugins {
  kotlin("jvm") version "1.3.72" apply false
  id("com.github.rodm.teamcity-server") version "1.3.2" apply false
  id("com.github.rodm.teamcity-environments") version "1.3.2" apply false
}

group = "de.gesellix"
version = "0.1"

//extra["teamcityVersion"] = findProperty("teamcity.version") ?: "2020.1"
//extra["teamcityVersion"] = findProperty("teamcity.version") ?: "2019.2"
//extra["teamcityVersion"] = findProperty("teamcity.version") ?: "2018.2"
extra["teamcityVersion"] = findProperty("teamcity.version") ?: "2017.2"

allprojects {
  repositories {
//    maven { url = uri("https://download.jetbrains.com/teamcity-repository") }
//    mavenCentral()
//    jcenter()
    maven(url = "https://cache-redirector.jetbrains.com/maven-central")
    maven(url = "https://cache-redirector.jetbrains.com/jcenter")
  }

  val dependencyVersions = listOf(
    "com.google.code.gson:gson:2.2.4",
    "commons-codec:commons-codec:1.9",
    "commons-httpclient:commons-httpclient:3.1",
    "commons-io:commons-io:1.3.2",
    "commons-logging:commons-logging:1.2",
    "log4j:log4j:1.2.12",
    "junit:junit:4.12",
    "xerces:xercesImpl:2.9.1"
  )
  val dependencyGroupVersions = mapOf(
    "org.hamcrest" to "1.3",
    "org.springframework" to "4.3.12.RELEASE"
  )
  configurations {
    all {
      resolutionStrategy {
        failOnVersionConflict()
        force(dependencyVersions)
        eachDependency {
          val forcedVersion = dependencyGroupVersions[requested.group]
          if (forcedVersion != null) {
            useVersion(forcedVersion)
          }
        }
        cacheDynamicVersionsFor(0, "seconds")
      }
    }
  }
}
