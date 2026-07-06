import dev.ua.theroer.magicutils.build.target.*

plugins {
    id("magicutils.java-library")
    id("magicutils.annotation-processing")
    id("magicutils.publishing")
    id("magicutils.shaded-module")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.COMMON_MATRIX
}

dependencies {
    api(project(":config"))
    implementation(libs.jackson.databind)
    implementation(libs.kyori.adventure.text.minimessage)
    compileOnly(libs.projectlombok.lombok)
    annotationProcessor(libs.projectlombok.lombok)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
