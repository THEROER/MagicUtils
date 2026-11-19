import dev.ua.theroer.magicutils.build.target.*

plugins {
    id("magicutils.java-library")
    id("magicutils.annotation-processing")
    id("magicutils.publishing")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.COMMON_MATRIX
}

// velocity-api 3.x (Adventure 4) on the 1.20/1.21/26.1 targets, 4.x (Adventure 5,
// Java 25) on 26.2+ — see magicUtilsVelocityApiVersion.
val velocityApi = "com.velocitypowered:velocity-api:" +
    magicUtilsVelocityApiVersion(project, extensions.getByType<MagicUtilsTargetExtension>())

dependencies {
    api(project(":core"))
    api(project(":diagnostics"))
    api(project(":messaging"))
    compileOnly(velocityApi)
    compileOnly(libs.slf4j.api)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.projectlombok.lombok)
    annotationProcessor(libs.projectlombok.lombok)
    testImplementation(libs.junit.jupiter)
    testImplementation(velocityApi)
    testRuntimeOnly(libs.junit.platform.launcher)
}
