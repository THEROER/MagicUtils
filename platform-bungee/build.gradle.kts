import dev.ua.theroer.magicutils.build.target.*

plugins {
    id("magicutils.java-library")
    id("magicutils.annotation-processing")
    id("magicutils.publishing")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.COMMON_MATRIX
}

dependencies {
    api(project(":core"))
    api(project(":diagnostics"))
    api(project(":messaging"))
    compileOnly(libs.bungeecord.api)
    api(libs.kyori.adventure.text.serializer.legacy)
    implementation(libs.kyori.adventure.text.serializer.plain)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.projectlombok.lombok)
    annotationProcessor(libs.projectlombok.lombok)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.bungeecord.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}
