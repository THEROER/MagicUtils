// Bukkit/Paper and proxy half of build-logic: the consumer plugins that drive a local
// dev server through jpenilla's run-* plugins.
//
// run-velocity and run-waterfall are applied from code (MagicUtilsConsumerVelocityPlugin
// / MagicUtilsConsumerBungeePlugin), so they must be on this artifact's runtime
// classpath. run-paper is `compileOnly` because nothing applies it from code —
// bukkit-bundle and consumers declare it in their own `plugins { }` block, which resolves
// through `pluginManagement` (magicutils.matrix-settings publishes the version).
//
// Note it buys no isolation: all three markers point at the same jar
// (`xyz.jpenilla:run-task`, which carries the run-paper/run-velocity/run-waterfall
// descriptors together), so a proxy-only consumer resolves the Paper task classes
// regardless. Verified on a real resolve of `magicutils.consumer-bukkit` from
// mavenLocal: one `xyz.jpenilla:run-task:3.0.2`, no Loom, no ModDev.
//
// The two version lines also collapse onto that single artifact, so the older
// runProxyVersion loses the conflict and run-waterfall effectively rides on
// runPaperVersion. Checked that the newer jar still ships
// `META-INF/gradle-plugins/xyz.jpenilla.run-waterfall.properties`, so the Bungee dev
// server keeps working; if a future run-paper drops Waterfall, this is where it breaks.
dependencies {
    val runPaperVersion = project.property("runPaperVersion")
    val runProxyVersion = project.property("runProxyVersion")
    compileOnly("xyz.jpenilla.run-paper:xyz.jpenilla.run-paper.gradle.plugin:$runPaperVersion")
    implementation("xyz.jpenilla.run-velocity:xyz.jpenilla.run-velocity.gradle.plugin:$runPaperVersion")
    implementation("xyz.jpenilla.run-waterfall:xyz.jpenilla.run-waterfall.gradle.plugin:$runProxyVersion")

    implementation("com.gradleup.shadow:shadow-gradle-plugin:${project.property("shadowVersion")}")
}

gradlePlugin {
    plugins {
        register("magicutilsConsumerBukkit") {
            id = "magicutils.consumer-bukkit"
            implementationClass = "dev.ua.theroer.magicutils.build.consumer.MagicUtilsConsumerBukkitPlugin"
        }
        register("magicutilsConsumerVelocity") {
            id = "magicutils.consumer-velocity"
            implementationClass = "dev.ua.theroer.magicutils.build.consumer.MagicUtilsConsumerVelocityPlugin"
        }
        register("magicutilsConsumerBungee") {
            id = "magicutils.consumer-bungee"
            implementationClass = "dev.ua.theroer.magicutils.build.consumer.MagicUtilsConsumerBungeePlugin"
        }
    }
}
