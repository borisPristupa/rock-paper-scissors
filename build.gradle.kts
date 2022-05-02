plugins {
  kotlin("jvm") version "1.6.10"
  id("org.jlleitschuh.gradle.ktlint") version "10.2.1"
  application
}

group = "com.boris"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  implementation(kotlin("stdlib"))
  implementation("com.googlecode.lanterna:lanterna:3.1.1")

  testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.2")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}

sourceSets {
  main {
    java.setSrcDirs(listOf("src"))
    resources.setSrcDirs(listOf("resources"))
  }
  test {
    java.setSrcDirs(listOf("test"))
    resources.setSrcDirs(listOf("testResources"))
  }
}

application {
  mainClass.set("com.boris.rps.MainKt")
}

tasks.register<Jar>("uberJar") {
  dependsOn(configurations.runtimeClasspath, "check")

  archiveClassifier.set("uber")
  manifest.attributes["Main-Class"] = application.mainClass.get()
  duplicatesStrategy = DuplicatesStrategy.EXCLUDE

  from(sourceSets.main.get().output)
  from(
    configurations
      .runtimeClasspath
      .get()
      .filter { it.name.endsWith("jar") }
      .map(::zipTree)
  )
}

tasks.getByName<Test>("test") {
  useJUnitPlatform()
}
