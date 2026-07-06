import dev.ua.theroer.magicutils.build.target.*

plugins {
    id("magicutils.java-library")
    id("magicutils.annotation-processing")
    id("magicutils.publishing")
    id("magicutils.target")
}

magicutilsPublish {
    category = MagicUtilsPublishCategory.COMMON_MATRIX
}

val target = project.extensions.getByType(MagicUtilsTargetExtension::class.java)

dependencies {
    api(project(":core"))
    api(project(":diagnostics"))
    compileOnly("io.papermc.paper:paper-api:${target.paper.get()}")
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.projectlombok.lombok)
    compileOnly(libs.clip.placeholderapi)

    annotationProcessor(libs.projectlombok.lombok)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.mockito.core)
    testImplementation("io.papermc.paper:paper-api:${target.paper.get()}")
    testRuntimeOnly(libs.junit.platform.launcher)
}
