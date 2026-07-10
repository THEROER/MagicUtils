import dev.ua.theroer.magicutils.build.target.*

plugins {
    id("magicutils.fabric-module")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.FABRIC_MATRIX
}

dependencies {
    api(project(":commands-brigadier"))
    api(project(":diagnostics"))
    api(project(":messaging"))
    api(project(":platform-fabric"))
    api(project(":logger-fabric"))

    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.projectlombok.lombok)
    annotationProcessor(libs.projectlombok.lombok)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}
