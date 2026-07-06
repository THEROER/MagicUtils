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
    api(project(":config"))
    api(project(":lang"))
    api(project(":placeholders"))
    api(libs.kyori.adventure.text.minimessage)
    implementation(libs.kyori.adventure.text.serializer.plain)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.projectlombok.lombok)
    annotationProcessor(libs.projectlombok.lombok)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
