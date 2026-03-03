import dev.detekt.gradle.Detekt
import kotlinx.kover.api.CounterType
import kotlinx.kover.api.VerificationTarget
import kotlinx.kover.api.VerificationValueType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_25
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_3

plugins {
  kotlin("jvm")

  `java-library`
  `java-test-fixtures`

  id("dev.detekt")
  id("org.jetbrains.kotlinx.kover")
}

repositories {
  mavenCentral()
  maven {
    this.name = "Jitpack.io"
    url = uri("https://jitpack.io")
  }
}

// Access the version catalog
val libs = extensions.getByType(VersionCatalogsExtension::class.java).named("libs")

dependencies {
  implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
  implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
  implementation("org.jetbrains.kotlin:kotlin-reflect")
  implementation(libs.findLibrary("kotlinLogging").get())
  implementation(libs.findLibrary("kotlinxCoroutinesCore").get())
  implementation(libs.findLibrary("kotlinxDatetime").get())
  api(libs.findLibrary("arrowCore").get())

  testImplementation(libs.findLibrary("assertJ").get())
  testImplementation(libs.findLibrary("junit").get())
  testImplementation(libs.findLibrary("mockk").get())
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")

  testFixturesImplementation(libs.findLibrary("assertJ").get())
  testFixturesImplementation(libs.findLibrary("junit").get())
  testFixturesImplementation(libs.findLibrary("mockk").get())
}

java {
  sourceCompatibility = JavaVersion.VERSION_25
  targetCompatibility = JavaVersion.VERSION_25
}

detekt {
  buildUponDefaultConfig = true
  config.setFrom(files("${rootProject.projectDir}/detekt-config.yaml"))
}

tasks {

  withType<Detekt> {
    this.jvmTarget.set(JVM_25.target)
  }

  kotlin {
    compilerOptions.apiVersion = KOTLIN_2_3
    compilerOptions.languageVersion = KOTLIN_2_3
    compilerOptions.jvmTarget = JVM_25
    compilerOptions.allWarningsAsErrors = true
    compilerOptions.optIn = listOf("kotlin.time.ExperimentalTime")
  }

  test {
    useJUnitPlatform()
    testLogging {
      events("passed", "skipped", "failed")
    }
  }

  kover {
    htmlReport {
      onCheck.set(true)
    }

    verify {
      onCheck.set(true)

      rule {
        name = "Cover coverage bounds"
        isEnabled = true

        target = VerificationTarget.ALL
        bound {
          minValue = 0
          valueType = VerificationValueType.COVERED_PERCENTAGE
          counter = CounterType.INSTRUCTION
        }
      }
    }
  }
}
