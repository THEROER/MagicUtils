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
    api(libs.kyori.adventure.api)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.projectlombok.lombok)
    annotationProcessor(libs.projectlombok.lombok)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
