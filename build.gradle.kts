import org.gradle.api.tasks.bundling.Zip
import org.gradle.jvm.tasks.Jar

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktor) apply false
    alias(libs.plugins.shadow) apply false
}

allprojects {
    group = "kr.meeor.mcstreamapi"
    version = providers.gradleProperty("releaseVersion").getOrElse("0.1.0-SNAPSHOT")
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension> {
            jvmToolchain(21)
        }

        dependencies {
            add("testImplementation", kotlin("test"))
        }

        tasks.withType<Test>().configureEach {
            useJUnitPlatform()
        }

        tasks.withType<Jar>().configureEach {
            isPreserveFileTimestamps = false
            isReproducibleFileOrder = true
            manifest {
                attributes(
                    "Implementation-Title" to project.name,
                    "Implementation-Version" to project.version.toString(),
                    "Implementation-Vendor" to "Meeor",
                    "Built-Jdk-Spec" to "21",
                )
            }
        }
    }
}

tasks.register<Zip>("releaseBundle") {
    group = "distribution"
    description = "Builds the public McStreamApi release bundle."

    dependsOn(":plugin:shadowJar", ":auth-server:buildFatJar")

    archiveFileName.set("McStreamApi-${project.version}-release.zip")
    destinationDirectory.set(layout.buildDirectory.dir("release"))

    from("plugin/build/libs/McStreamApi-${project.version}.jar") {
        into("jars")
    }
    from("auth-server/build/libs/McStreamApi-AuthServer-${project.version}.jar") {
        into("jars")
    }
    from("config.example.yml")
    from("Api.example.yml")
    from("random.example.yml")
    from("auth-server.config.example.yml")
    from("ASstart.sh")
    from("ASstop.sh")
    from("README.md")
    from("CHANGELOG.md")
    from("docs") {
        into("docs")
        exclude("local-auth-server.config.yml")
    }
}
