plugins {
  kotlin("jvm") version "1.3.72" apply false
  id("com.github.rodm.teamcity-server") version "1.3.1" apply false
  id("com.github.rodm.teamcity-environments") version "1.3.1" apply false
//  id("com.github.rodm.teamcity-server") version "1.2" apply false
}

group = "de.gesellix"
//version = "1.0-SNAPSHOT"

extra["teamcityVersion"] = findProperty("teamcity.version") ?: "2017.2" // "2019.1"

allprojects {
  repositories {
//  maven { url = uri("https://download.jetbrains.com/teamcity-repository") }
//  mavenCentral()
//  jcenter()
    maven(url = "https://cache-redirector.jetbrains.com/maven-central")
    maven(url = "https://cache-redirector.jetbrains.com/jcenter")
  }
}
