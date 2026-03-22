import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.gradle.api.attributes.Usage

plugins {
  id("kotlin-project")
  alias(libs.plugins.allopen)
  alias(libs.plugins.quarkus)
}

// Dedicated configuration to resolve the original adapter-out-persistence-mongodb JAR.
// It is NOT added to the main classpath directly; instead the stripped version is used.
val outboxPersistenceOriginal by configurations.creating {
  isCanBeConsumed = false
  isCanBeResolved = true
  isTransitive = false
  attributes {
    attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
  }
}

dependencies {
  outboxPersistenceOriginal(libs.quarkusOutboxAdapterOutPersistenceMongodb)

  implementation(project(":adapter-in-outbox"))
  implementation(project(":adapter-in-scheduler"))
  implementation(project(":adapter-in-starter"))
  implementation(project(":adapter-in-web"))
  implementation(project(":adapter-out-mongodb"))
  implementation(project(":adapter-out-outbox"))
  implementation(project(":adapter-out-scheduler"))
  implementation(project(":adapter-out-slack"))
  implementation(project(":adapter-out-spotify"))
  implementation(project(":domain-impl"))

  implementation(libs.quarkusOutboxAdapterInScheduler)
  implementation(libs.quarkusOutboxAdapterOutExecutor)
  // adapter-out-persistence-mongodb is added as the stripped version below (stripOutboxPersistenceJar)
  implementation(libs.quarkusOutboxDomainImpl)
  implementation(libs.quarkusStartersDomainImpl)
  implementation(libs.quarkusStartersAdapterOutPersistenceMongodb)

  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")

  api(enforcedPlatform(libs.quarkusBom))
  implementation("io.quarkus:quarkus-arc")
  implementation("io.quarkus:quarkus-kotlin")
  implementation("io.quarkus:quarkus-smallrye-health")
  implementation("io.quarkus:quarkus-micrometer-registry-prometheus")
  implementation("io.quarkus:quarkus-logging-json")
  implementation("io.quarkus:quarkus-container-image-docker")

  testImplementation("io.quarkus:quarkus-junit5")
  testImplementation("io.quarkus:quarkus-junit5-mockito")
  testImplementation("io.quarkus:quarkus-test-security")
  testImplementation("io.rest-assured:rest-assured")
}

// Strip LocalVariableTable/LocalVariableTypeTable debug attributes from the outbox persistence
// JAR. This is required because Quarkus Panache Kotlin bytecode transformation inserts code
// into Panache methods without updating debug attribute offsets, causing ClassFormatError when
// the JVM loads the transformed class on Java 25.
val strippedJarFile = layout.buildDirectory.file("stripped/adapter-out-persistence-mongodb.jar")

val stripOutboxPersistenceJar by tasks.registering {
  description = "Creates a debug-stripped version of adapter-out-persistence-mongodb to avoid ClassFormatError on Java 25"
  group = "build"
  inputs.files(outboxPersistenceOriginal)
  outputs.file(strippedJarFile)
  doLast {
    val input = outboxPersistenceOriginal.singleFile
    val output = strippedJarFile.get().asFile
    output.parentFile.mkdirs()
    ZipInputStream(input.inputStream()).use { zis ->
      ZipOutputStream(output.outputStream()).use { zos ->
        var entry = zis.nextEntry
        while (entry != null) {
          zos.putNextEntry(ZipEntry(entry.name))
          if (entry.name.endsWith(".class")) {
            val bytes = zis.readBytes()
            val reader = org.objectweb.asm.ClassReader(bytes)
            val writer = org.objectweb.asm.ClassWriter(0)
            reader.accept(writer, org.objectweb.asm.ClassReader.SKIP_DEBUG)
            zos.write(writer.toByteArray())
          } else {
            zis.copyTo(zos)
          }
          zos.closeEntry()
          entry = zis.nextEntry
        }
      }
    }
  }
}

// Add the stripped JAR to the implementation classpath.
// files(task) creates a lazy FileCollection that depends on the task, so the task runs first.
dependencies {
  implementation(files(stripOutboxPersistenceJar))
}

tasks.withType<Test> {
  systemProperty("java.util.logging.manager", "org.jboss.logmanager.LogManager")
}

allOpen {
  annotation("jakarta.ws.rs.Path")
  annotation("jakarta.enterprise.context.ApplicationScoped")
  annotation("io.quarkus.test.junit.QuarkusTest")
}
