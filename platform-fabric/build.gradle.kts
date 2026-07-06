import dev.ua.theroer.magicutils.build.target.*

plugins {
    id("magicutils.fabric-module")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.FABRIC_MATRIX
}

dependencies {
    api(project(":core"))

    implementation(libs.kyori.adventure.text.minimessage)
    implementation(libs.kyori.adventure.text.serializer.gson)
    implementation(libs.kyori.adventure.text.serializer.plain)

    compileOnly(libs.slf4j.api)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.projectlombok.lombok)
    annotationProcessor(libs.projectlombok.lombok)
}