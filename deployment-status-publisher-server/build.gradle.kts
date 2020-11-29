import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  id("com.github.rodm.teamcity-server")
  id("com.github.rodm.teamcity-environments")
}

val teamcityVersion = rootProject.extra["teamcityVersion"] as String

//val agent = configurations.getByName("agent")

dependencies {
  implementation(project(":deployment-status-publisher-common"))
  implementation(project(":github-client"))
//  agent (project(path = ":agent", configuration = "plugin"))

  implementation(kotlin("stdlib-jdk8"))
//  implementation(kotlin("reflect"))
//  implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.2")
//  implementation("com.github.salomonbrys.kotson:kotson:2.5.0")

  testImplementation("com.squareup.okhttp3:mockwebserver:4.9.0")
  testImplementation("org.awaitility:awaitility:4.0.3")
  testRuntimeOnly("org.hamcrest:hamcrest:2.2")
  testRuntimeOnly("org.hamcrest:hamcrest-core:2.2")

//  implementation("commons-beanutils:commons-beanutils-core:1.8.3")
//  implementation("commons-codec:commons-codec:1.9")
//  implementation("commons-logging:commons-logging:1.2")
  implementation("com.squareup.moshi:moshi:1.11.0")
//  implementation("com.jcraft:jsch:0.1.50")

  provided("org.jetbrains.teamcity:server-api:${teamcityVersion}")
  provided("org.jetbrains.teamcity:oauth:${teamcityVersion}")
  provided("org.jetbrains.teamcity:server-web-api:${teamcityVersion}")
  provided("org.jetbrains.teamcity.internal:server:${teamcityVersion}")
  provided("org.jetbrains.teamcity.internal:web:${teamcityVersion}")

  listOf(
    "/devPackage",
    "/devPackage/tests",
    "/serverLibs"
  ).forEach { path ->
    testImplementation(
      fileTree(
        mapOf(
          "dir" to "${projectDir}/libs/tc-$teamcityVersion$path",
          "include" to listOf("*.jar"),
          "exclude" to listOf("httpcore*.jar", "httpclient*.jar")
        )
      )
    )
  }
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
  jvmTarget = "1.8"
  languageVersion = "1.4"
}

tasks {
  val testNg by creating(Test::class) {
    group = "verification"
    useTestNG() {
//      useDefaultListeners = true
    }
  }
  check {
    dependsOn(testNg)
  }
}

tasks {
  val tcDevPackage = register<Copy>("tcDevPackage") {
    group = "teamcity"
    dependsOn("downloadTeamcity$teamcityVersion")
    from(tarTree(findByName("downloadTeamcity$teamcityVersion")!!.outputs.files.first())) {
      include("/TeamCity/devPackage/**")
      eachFile {
        relativePath = RelativePath(true, *relativePath.segments.drop(1).toTypedArray())
      }
      includeEmptyDirs = false
    }
    into(file("${projectDir}/libs/tc-$teamcityVersion"))
  }
  val tcLibsPackage = register<Copy>("tcLibsPackage") {
    group = "teamcity"
    dependsOn("downloadTeamcity$teamcityVersion")
    from(tarTree(findByName("downloadTeamcity$teamcityVersion")!!.outputs.files.first())) {
      listOf(
        "agent-upgrade.jar",
        "caffeine-2.4.0.jar",
        "cl.jar",
        "cloud-interface.jar",
        "cloud-server.jar",
        "cloud-server-api.jar",
        "common-impl.jar",
        "commons-dbcp-1.4.1-SNAPSHOT.jar",
        "commons-pool-1.6.jar",
        "db.jar",
        "ehcache-1.7.2.jar",
        "hsqldb.jar",
        "hsqldb1.jar",
        "issue-tracker-impl.jar",
        "oauth.jar",
        "patches-impl.jar",
        "remote-api-impl.jar",
        "server.jar",
        "server-tools.jar",
        "spring-security.jar",
        "tomcat-jdbc-7.0.23.jar",
        "web.jar"
      ).forEach {
        include("/TeamCity/webapps/ROOT/WEB-INF/lib/$it")
      }
      eachFile {
        relativePath = RelativePath(true, *relativePath.segments.drop(5).toTypedArray())
      }
      includeEmptyDirs = false
    }
    into(file("${projectDir}/libs/tc-$teamcityVersion/serverLibs"))
  }
  withType(Test::class.java) {
    dependsOn(tcDevPackage, tcLibsPackage)
  }
}

teamcity {
  version = teamcityVersion

  val pluginName = "deployments-status-publisher".replace(Regex("\\s+"), "-").toLowerCase()
  server {
    archiveName = "$pluginName.zip"
//    descriptor = file("teamcity-plugin.xml")
//    tokens = mapOf("Version" to pluginVersion)
    descriptor {
      name = pluginName
      displayName = "Deployments Status Publisher"
      version = rootProject.version as String?
      vendorName = "gesellix"
      vendorUrl = "https://www.gesellix.net"
      description = "Publishes the status of a deployment to an external system"
      email = "tobias@gesellix.de"
      useSeparateClassloader = true
    }
//    files {
//      into("kotlin-dsl") {
//        from("src/kotlin-dsl")
//      }
//    }
    publish {
      setChannels(listOf("beta"))
      setToken(file("$rootDir/.jetbrains-hub-token").readText().trim())
      setNotes("automated publish")
    }
  }

  environments {
//    baseDataDir = "${rootDir}/data"
    register("teamcity$teamcityVersion") {
      version = "2017.2.2"
      dataDir = file("${rootDir}/data/tc-server/datadir")
//      javaHome = file(extra["java8Home"] as String)
//      serverOptions ("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005")
//      agentOptions ("-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5006")
    }
  }
}
