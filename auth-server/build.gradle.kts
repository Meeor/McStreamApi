plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktor)
    application
}

application {
    mainClass.set("kr.meeor.mcstreamapi.authserver.ApplicationKt")
}

ktor {
    fatJar {
        archiveFileName.set("McStreamApi-AuthServer-${project.version}.jar")
    }
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.snakeyaml)
    implementation(libs.logback.classic)
    testImplementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.ktor.server.test.host)
}

tasks {
    build {
        dependsOn(buildFatJar)
    }
}
