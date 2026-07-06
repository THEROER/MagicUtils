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
    compileOnly(libs.velocity.api)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.projectlombok.lombok)
    annotationProcessor(libs.projectlombok.lombok)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.velocity.api)
    testRuntimeOnly(libs.junit.platform.launcher)
}
