package dev.ua.theroer.magicutils.build.support

/**
 * Default server-API coordinates for the platforms whose API version is decoupled from
 * Minecraft (the proxies). Overridable per project via the `bungeeApiVersion` /
 * `velocityApiVersion` Gradle properties.
 *
 * These live in the platform-neutral artifact because both sides need them: the consumer
 * plugins in :jvm, and MagicUtilsJvmBundlePlugin here, which derives each bundle's
 * compileOnly API coordinate. Before build-logic was split per platform they were
 * constants on the consumer plugins themselves; that would now be a :jvm dependency for
 * a project that only builds bundles.
 */
object MagicUtilsPlatformApiDefaults {
    const val BUNGEE_API = "1.20-R0.1"
    const val VELOCITY_API = "3.1.1"
}
