import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  kotlin("jvm")
  kotlin("kapt")
  id("maven-publish")
}

dependencies {
  implementation(kotlin("stdlib-jdk8"))
//  implementation(kotlin("reflect"))
  implementation("com.squareup.retrofit2:retrofit:2.9.0")
  implementation("com.squareup.retrofit2:converter-moshi:2.9.0")
  implementation("com.squareup.moshi:moshi:1.11.0")
  implementation("com.squareup.moshi:moshi-kotlin:1.11.0")
  kapt("com.squareup.moshi:moshi-kotlin-codegen:1.11.0")
  implementation("com.squareup.okhttp3:okhttp:4.9.0")
  testImplementation("com.squareup.okhttp3:mockwebserver:4.9.0")
  implementation("com.squareup.okhttp3:logging-interceptor:4.9.0")
  testImplementation("org.junit.jupiter:junit-jupiter:5.4.2")
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
  jvmTarget = "1.8"
  languageVersion = "1.4"
}

val test: Test by tasks
test.useJUnitPlatform()

val sourcesJar by tasks.registering(Jar::class) {
  dependsOn("classes")
  archiveClassifier.set("sources")
  from(sourceSets.main.get().allSource)
}

artifacts {
  add("archives", sourcesJar.get())
}

configure<PublishingExtension> {
  publications {
    register("lib", MavenPublication::class) {
      from(components["java"])
      artifact(sourcesJar.get())
      artifactId = "teamcity-deployments-${project.name}"
    }
  }
}
