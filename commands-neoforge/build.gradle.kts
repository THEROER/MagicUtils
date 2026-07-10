import dev.ua.theroer.magicutils.build.target.*
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    id("magicutils.java-library")
    id("magicutils.annotation-processing")
    id("magicutils.publishing")
    id("net.neoforged.moddev")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.COMMON_MATRIX
}

val target = project.extensions.getByType(MagicUtilsTargetExtension::class.java)

neoForge {
    version = target.neoforge.get()
}

val shadowRuntimeClasspath by configurations.creating {
    isCanBeResolved = true
    isCanBeConsumed = false
    extendsFrom(configurations["runtimeClasspath"])
    exclude(group = "net.neoforged")
}

tasks.named<ShadowJar>("shadowJar") {
    configurations = listOf(shadowRuntimeClasspath)
}

dependencies {
    api(project(":commands-brigadier"))
    api(project(":diagnostics"))
    api(project(":messaging"))
    api(project(":platform-neoforge"))
    implementation(libs.kyori.adventure.text.serializer.plain)
    compileOnly(libs.brigadier)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.projectlombok.lombok)
    annotationProcessor(libs.projectlombok.lombok)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.neoforge) {
        version {
            strictly(target.neoforge.get())
        }
    }
}
