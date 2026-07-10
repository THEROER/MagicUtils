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
    api(project(":platform-api"))
    api(project(":config"))
    implementation(libs.jackson.databind)
    // Redis is an optional transport: RedisMessageTransport is only class-loaded
    // when Redis is enabled, so Jedis stays compile-only here. Bundles that ship
    // the Redis transport add jedis to their runtime classpath (and shade it).
    compileOnly(libs.jedis)
    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.projectlombok.lombok)
    annotationProcessor(libs.projectlombok.lombok)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jedis)
    testRuntimeOnly(libs.junit.platform.launcher)
    testRuntimeOnly(project(":config-yaml"))
}
