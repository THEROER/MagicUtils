import dev.ua.theroer.magicutils.build.target.*

plugins {
    id("magicutils.fabric-module")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.FABRIC_MATRIX
}

dependencies {
    api(project(":placeholders"))
    api(project(":platform-fabric"))
    api(project(":logger"))

    implementation(libs.kyori.adventure.text.minimessage)

    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.projectlombok.lombok)
    annotationProcessor(libs.projectlombok.lombok)
}