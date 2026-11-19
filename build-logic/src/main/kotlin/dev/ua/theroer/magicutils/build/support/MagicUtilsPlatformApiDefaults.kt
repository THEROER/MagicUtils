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

    /**
     * Velocity API for the Adventure-5 targets (26.2+). Velocity 4 moved to Adventure 5
     * (velocity-api 4.x imports adventure-bom 5.2.0) and requires Java 25, which is
     * exactly what those targets run; Velocity 3.x stays on Adventure 4 and keeps
     * serving the 1.20/1.21 builds. See `magicUtilsVelocityApiVersion`.
     */
    const val VELOCITY_API_4 = "4.3.1"
}
