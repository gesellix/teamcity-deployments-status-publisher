plugins {
  kotlin("jvm")
  id("com.github.rodm.teamcity-server")
  id("com.github.rodm.teamcity-environments")
}

val pluginVersion = project.findProperty("PluginVersion") ?: "SNAPSHOT"
version = pluginVersion

val teamcityVersion = rootProject.extra["teamcityVersion"] as String
//val teamcityVersion = "2017.2"
//val teamcityVersion = "2020.2-SNAPSHOT"

//val agent = configurations.getByName("agent")

dependencies {
//  api("org.apache.commons:commons-math3:3.6.1")
//  implementation("com.google.guava:guava:29.0-jre")

  implementation(project(":deployment-status-publisher-common"))
//  agent (project(path = ":agent", configuration = "plugin"))

  implementation(kotlin("stdlib-jdk8"))
  implementation(kotlin("reflect"))
  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.2")
  implementation("com.github.salomonbrys.kotson:kotson:2.5.0")

  provided("org.jetbrains.teamcity:server-api:${teamcityVersion}")
  provided("org.jetbrains.teamcity:oauth:${teamcityVersion}")
  provided("org.jetbrains.teamcity:server-web-api:${teamcityVersion}")
  provided("org.jetbrains.teamcity.internal:server:${teamcityVersion}")
  provided("org.jetbrains.teamcity.internal:web:${teamcityVersion}")

  testImplementation("org.assertj:assertj-core:1.7.1")
  testImplementation("org.testng:testng:6.8")
  testImplementation("io.mockk:mockk:1.10.0")
}

tasks {
  compileKotlin {
    kotlinOptions.jvmTarget = "1.8"
  }
  compileTestKotlin {
    kotlinOptions.jvmTarget = "1.8"
  }
}

teamcity {
  version = teamcityVersion
//  version = "2017.2"
//  version = "2020.1"

//  server {
//    archiveName = "github-deployments.zip"
//    descriptor = file("teamcity-plugin.xml")
//    tokens = mapOf("Version" to pluginVersion)
//
//    files {
//      into("kotlin-dsl") {
//        from("src/kotlin-dsl")
//      }
//    }
//  }
  server {
    descriptor {
      name = "Example TeamCity Plugin"
      displayName = "Example TeamCity Plugin"
      version = rootProject.version as String?
      vendorName = "rodm"
      vendorUrl = "https://example.com"
      description = "Example multi-project TeamCity plugin"
      email = "rod.n.mackenzie@gmail.com"
      useSeparateClassloader = true
    }
  }

  environments {
//    downloadsDir = extra["downloadsDir"] as String
//    baseHomeDir = extra["serversDir"] as String
//    baseDataDir = "${rootDir}/data"
//
//    operator fun String.invoke(block: TeamCityEnvironment.() -> Unit) {
//      environments.create(this, closureOf<TeamCityEnvironment>(block))
//    }
//    "teamcity2019.1" {
//      version = "2019.1.5"
//      javaHome = file(extra["java8Home"] as String)
//      serverOptions ("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005")
//      agentOptions ("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006")
//    }
//
//    create("teamcity2019.2") {
//      version = "2019.2.4"
//      javaHome = file(extra["java8Home"] as String)
//    }
//
    register("teamcity2017.2") {
      version = "2017.2"
//      javaHome = file(extra["java8Home"] as String)
    }
  }
}
