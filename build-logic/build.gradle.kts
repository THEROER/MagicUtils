import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import java.net.HttpURLConnection
import java.net.URI

plugins {
    kotlin("jvm") version "2.2.0"
    `java-gradle-plugin`
    `maven-publish`
}

// build-logic is published as FOUR artifacts, not one:
//
//   magicutils-build-logic           - platform-neutral (this project)
//   magicutils-build-logic-fabric    - adds Fabric Loom
//   magicutils-build-logic-neoforge  - adds ModDevGradle
//   magicutils-build-logic-jvm       - adds the jpenilla run-* runners
//
// Every platform plugin is applied *from code* (`pluginManager.apply(id)`), which
// resolves through the applying plugin's own runtime classpath — `compileOnly` compiles
// but then fails at apply time with "Plugin with id '...' not found", and neither
// `pluginManagement` versions nor `plugins { id(...) apply false }` reach that
// classloader. So a toolchain has to be a real `implementation` dependency of whichever
// artifact needs it, and the only way to keep it off everyone else is to split the
// artifact. A consumer asking for `magicutils.consumer-neoforge` now resolves ModDev
// alone and never Fabric Loom, which pins a far newer Gradle than a 1.21.1 NeoForge mod
// can build with.
allprojects {
    if (project != rootProject) {
        apply(plugin = "org.jetbrains.kotlin.jvm")
        apply(plugin = "java-gradle-plugin")
        apply(plugin = "maven-publish")
        // Without this the artifact id is the bare subproject name ("fabric"), which is
        // both meaningless in a Maven repo and liable to collide.
        base {
            archivesName.set("${rootProject.name}-${project.name}")
        }
    }

    // Version and group are independent of the MagicUtils library (which lives in
    // the root gradle.properties). These plugins are their own releasable tool.
    group = providers.gradleProperty("pluginsGroup").getOrElse("dev.ua.theroer.magicutils.build")
    version = providers.gradleProperty("pluginsVersion").getOrElse("0.1.0")

    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven { url = uri("https://maven.fabricmc.net/") }
        maven { url = uri("https://maven.neoforged.net/releases") }
    }

    extensions.configure<PublishingExtension>("publishing") {
        // java-gradle-plugin's own publication defaults its artifactId to the bare
        // project name ("fabric"). Rename just that one; the per-plugin marker
        // publications must keep their `<plugin id>.gradle.plugin` coordinates, which is
        // how Gradle resolves `plugins { id("magicutils.consumer-fabric") }`.
        if (project != rootProject) {
            publications.withType(MavenPublication::class.java).configureEach {
                if (name == "pluginMaven") {
                    artifactId = "${rootProject.name}-${project.name}"
                }
            }
        }

        // Publish to the same Reposilite Maven repo as the library when -Ppublish_repo
        // is set (CI); always available via mavenLocal for dogfooding/consumers.
        // This build script can't use the magicUtilsPublishRepository helper (that's
        // part of the plugins it builds), so it mirrors the same credential handling:
        // user/password from PUBLISH_USER/PUBLISH_TOKEN, applied only when supplied.
        if (project.hasProperty("publish_repo")) {
            val publishUser = (findProperty("publish_user") as? String)?.takeIf { it.isNotBlank() }
                ?: System.getenv("PUBLISH_USER")?.takeIf { it.isNotBlank() }
            val publishPassword = (findProperty("publish_password") as? String)?.takeIf { it.isNotBlank() }
                ?: System.getenv("PUBLISH_TOKEN")?.takeIf { it.isNotBlank() }
            repositories {
                maven {
                    name = "magicutilsPublish"
                    url = uri(project.property("publish_repo") as String)
                    if (publishUser != null && publishPassword != null) {
                        credentials {
                            username = publishUser
                            password = publishPassword
                        }
                    }
                }
            }
        }
    }

    // -Pskip_existing makes publishing idempotent: skip a PublishToMavenRepository
    // task whose POM is already in the (immutable) repo, so a resumed release does
    // not 409 on the plugins that a prior run already uploaded. HEAD is cheap and
    // runs at execution time (skip decided per task), not at configuration.
    if (project.hasProperty("skip_existing") && project.hasProperty("publish_repo")) {
        val repoBase = (project.property("publish_repo") as String).trimEnd('/')
        tasks.withType<PublishToMavenRepository>().configureEach {
            onlyIf {
                val pub = publication as org.gradle.api.publish.maven.MavenPublication
                val path = "${pub.groupId.replace('.', '/')}/${pub.artifactId}/${pub.version}/" +
                    "${pub.artifactId}-${pub.version}.pom"
                val url = URI("$repoBase/$path").toURL()
                val exists = runCatching {
                    (url.openConnection() as HttpURLConnection).run {
                        requestMethod = "HEAD"; connectTimeout = 10000; readTimeout = 10000
                        val code = responseCode; disconnect(); code == 200
                    }
                }.getOrDefault(false)
                if (exists) logger.lifecycle("skip_existing: ${pub.artifactId}:${pub.version} already published, skipping.")
                !exists
            }
        }
    }

    dependencies {
        "implementation"(gradleApi())
        "implementation"(kotlin("stdlib"))
        // Every platform subproject builds on the neutral plugins (target conventions,
        // publishing, module naming), and `api` so their consumers get those types too.
        if (project != rootProject) {
            "api"(rootProject)
        }
    }

    java {
        toolchain.languageVersion.set(JavaLanguageVersion.of(21))
    }

    kotlin {
        jvmToolchain(21)
    }

    tasks.withType<JavaCompile>().configureEach {
        options.release.set(21)
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

dependencies {
    implementation("com.gradleup.shadow:shadow-gradle-plugin:${project.property("shadowVersion")}")

    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// The platform plugin versions live only in this project's gradle.properties, but
// matrix-settings needs them at *runtime* to declare each platform plugin's version in
// the consumer's pluginManagement (see MagicUtilsMatrixSettingsPlugin), so bake them
// into a resource — one source of truth, no version duplicated into every consumer's
// settings.gradle. This is what lets a consumer write `plugins { id("fabric-loom") }`
// with no version.
val platformPluginVersions = tasks.register("platformPluginVersions", WriteProperties::class) {
    destinationFile.set(
        layout.buildDirectory.file("generated/platform-plugins/magicutils/platform-plugins.properties")
    )
    property("fabricLoomVersion", project.property("fabricLoomVersion") as String)
    property("neoForgeModdevVersion", project.property("neoForgeModdevVersion") as String)
    property("runPaperVersion", project.property("runPaperVersion") as String)
    property("runProxyVersion", project.property("runProxyVersion") as String)
}

// The resource must land under `magicutils/` in the jar, so the source-set dir is
// the parent of that package directory, not of the file itself.
sourceSets.main {
    output.dir(platformPluginVersions.map { it.destinationFile.get().asFile.parentFile.parentFile })
}

// `publish`/`publishToMavenLocal` are per-project tasks, so the documented release
// command (`./gradlew -p build-logic publish`) would push the neutral artifact alone and
// silently leave the per-platform plugins unpublished — a consumer asking for
// `magicutils.consumer-fabric` would then get an unresolvable plugin. Aggregate the
// subprojects into the root tasks so one command still publishes the whole set.
listOf("publish", "publishToMavenLocal").forEach { taskName ->
    tasks.named(taskName) {
        dependsOn(subprojects.map { "${it.path}:$taskName" })
    }
}

gradlePlugin {
    plugins {
        register("magicutilsTarget") {
            id = "magicutils.target"
            implementationClass = "dev.ua.theroer.magicutils.build.matrix.MagicUtilsTargetPlugin"
        }
        register("magicutilsMatrixSettings") {
            id = "magicutils.matrix-settings"
            implementationClass = "dev.ua.theroer.magicutils.build.matrix.MagicUtilsMatrixSettingsPlugin"
        }
        register("magicutilsMatrixRoot") {
            id = "magicutils.matrix-root"
            implementationClass = "dev.ua.theroer.magicutils.build.matrix.MagicUtilsMatrixRootPlugin"
        }
        register("magicutilsRepositories") {
            id = "magicutils.repositories"
            implementationClass = "dev.ua.theroer.magicutils.build.support.MagicUtilsRepositoriesPlugin"
        }
        register("magicutilsCommon") {
            id = "magicutils.common"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsCommonPlugin"
        }
        register("magicutilsJavaLibrary") {
            id = "magicutils.java-library"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsJavaLibraryPlugin"
        }
        register("magicutilsAnnotationProcessing") {
            id = "magicutils.annotation-processing"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsAnnotationProcessingPlugin"
        }
        register("magicutilsShadow") {
            id = "magicutils.shadow"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsShadowPlugin"
        }
        register("magicutilsShadedModule") {
            id = "magicutils.shaded-module"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsShadedModulePlugin"
        }
        register("magicutilsPublishing") {
            id = "magicutils.publishing"
            implementationClass = "dev.ua.theroer.magicutils.build.publish.MagicUtilsPublishingPlugin"
        }
        // The JVM bundle plugins only inline jars — no platform toolchain involved, so
        // they stay in the neutral artifact alongside the module plugins.
        register("magicutilsBukkitBundle") {
            id = "magicutils.bukkit-bundle"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsBukkitBundlePlugin"
        }
        register("magicutilsVelocityBundle") {
            id = "magicutils.velocity-bundle"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsVelocityBundlePlugin"
        }
        register("magicutilsBungeeBundle") {
            id = "magicutils.bungee-bundle"
            implementationClass = "dev.ua.theroer.magicutils.build.module.MagicUtilsBungeeBundlePlugin"
        }
        register("magicutilsConsumerCommon") {
            id = "magicutils.consumer-common"
            implementationClass = "dev.ua.theroer.magicutils.build.consumer.MagicUtilsConsumerCommonPlugin"
        }
    }
}
