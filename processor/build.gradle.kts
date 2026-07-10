import dev.ua.theroer.magicutils.build.target.*

plugins {
    id("java-library") // Keep direct application of java-library
    id("magicutils.repositories")
    id("magicutils.publishing")
    id("magicutils.shadow") // Shadow plugin is applied in this project to disable shadowJar
}

magicutilsPublish {
    // Build-only: applied as an annotationProcessor to the library modules (see
    // MagicUtilsAnnotationProcessingPlugin), its generated code is baked into
    // those jars and it never appears as a runtime dependency in any published
    // POM. Downstreams don't need it, so it is not published.
    category = MagicUtilsPublishCategory.NONE
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 11
}

dependencies {
}
