import dev.ua.theroer.magicutils.build.target.*

plugins {
    id("magicutils.fabric-module")
    id("magicutils.annotation-processing")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.FABRIC_MATRIX
}

dependencies {
    api(project(":logger"))
    api(project(":platform-fabric"))
    api(project(":placeholders-fabric"))

    implementation(libs.kyori.adventure.text.minimessage)
    implementation(libs.kyori.adventure.text.serializer.ansi)
    implementation(libs.kyori.adventure.text.serializer.plain)

    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.projectlombok.lombok)
    annotationProcessor(libs.projectlombok.lombok)
}