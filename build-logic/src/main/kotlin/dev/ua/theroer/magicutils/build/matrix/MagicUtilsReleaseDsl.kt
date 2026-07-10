package dev.ua.theroer.magicutils.build.matrix

import dev.ua.theroer.magicutils.build.release.MagicUtilsReleaseSpec

/**
 * Consumer-facing DSL for the `release` orchestrator, e.g.:
 *
 *     magicMatrix {
 *         release {
 *             // publishMaven = true     // default
 *             publishModrinth = false    // this plugin has no Modrinth page
 *             push = true                // push the tag to origin as part of release
 *         }
 *     }
 *
 * Every axis is optional; omitting the block entirely means a full library
 * release (validate, bump, tag locally, publish Maven + Modrinth + Javadoc,
 * verify). Each step is also overridable per invocation with
 * `-Prelease.<step>=true|false` without editing settings.gradle.
 */
open class MagicUtilsReleaseDsl {
    var validateVersion: Boolean = true
    var validateBuild: Boolean = true
    var bump: Boolean = true
    var tag: Boolean = true
    var push: Boolean = false
    var publishMaven: Boolean = true
    var publishModrinth: Boolean = true
    var publishJavadoc: Boolean = true
    var verify: Boolean = true

    internal fun toSpec(): MagicUtilsReleaseSpec = MagicUtilsReleaseSpec(
        validateVersion = validateVersion,
        validateBuild = validateBuild,
        bump = bump,
        tag = tag,
        push = push,
        publishMaven = publishMaven,
        publishModrinth = publishModrinth,
        publishJavadoc = publishJavadoc,
        verify = verify,
    )
}
