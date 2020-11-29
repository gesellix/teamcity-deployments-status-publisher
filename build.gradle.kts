plugins {
  kotlin("jvm") version "1.4.10" apply false
  kotlin("kapt") version "1.4.10" apply false
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
    "com.squareup.moshi:moshi:1.11.0",
    "com.squareup.okhttp3:okhttp:4.9.0",
    "com.squareup.okio:okio:2.8.0",
    "commons-codec:commons-codec:1.9",
    "commons-httpclient:commons-httpclient:3.1",
    "commons-io:commons-io:1.3.2",
    "commons-logging:commons-logging:1.2",
    "log4j:log4j:1.2.12",
    "junit:junit:4.12",
    "org.jetbrains.kotlin:kotlin-stdlib:1.4.10",
    "org.jetbrains.kotlin:kotlin-stdlib-common:1.4.10",
    "org.jetbrains.kotlin:kotlin-stdlib-jdk7:1.4.10",
    "org.jetbrains.kotlin:kotlin-stdlib-jdk8:1.4.10",
    "org.jetbrains.kotlin:kotlin-reflect:1.4.10",
    "xerces:xercesImpl:2.9.1"
  )
  val dependencyGroupVersions = mapOf(
    "org.hamcrest" to "2.2",
    "org.springframework" to "4.3.12.RELEASE"
  )
  configurations {
    all {
      exclude(module = "hamcrest-integration")
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

tasks {
  wrapper {
    gradleVersion = "6.7.1"
    distributionType = Wrapper.DistributionType.ALL
  }
}
