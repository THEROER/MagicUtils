package dev.ua.theroer.magicutils.build.target

import org.gradle.api.Project

/**
 * Single source of truth for the target-derived Fabric/publishing conventions.
 *
 * The obfuscation boundary (Minecraft 26 became deobfuscated), the published
 * `mc<major.minor>` classifier, and the Loom flavour selection are the same
 * facts everywhere they're needed — the MagicUtils library's own fabric
 * modules/bundle AND every consumer plugin. They live here so no build script
 * (ours or a consumer's) re-derives them by hand.
 */

/**
 * Minecraft 26+ ships a deobfuscated jar: Loom does not remap, there are no
 * mappings, and `jar` is the artifact. Older targets are obfuscated and use the
 * classic remapping Loom path. Keyed on the *library* Minecraft (the published
 * coordinate's branch), which is what determines how MagicUtils itself was
 * built — not the runtime Minecraft, which may differ.
 */
val MagicUtilsTargetExtension.isDeobfuscated: Boolean
    get() = libraryMinecraft.get().substringBefore('.').toInt() >= 26

/**
 * Whether this target's platforms are on Adventure 5.
 *
 * Deliberately NOT [isDeobfuscated]: the two cutovers are one minor apart. 26.1 is
 * deobfuscated, but paper-api 26.1.1 still imports adventure-bom 4.26.1 — Paper moved to
 * Adventure 5 in 26.2, and treating all of 26.x as Adventure 5 forced 5.2.0 onto a
 * platform whose own API is compiled against 4, which fails as soon as anything reflects
 * over a Bukkit interface (CommandSender extends Audience).
 */
val MagicUtilsTargetExtension.usesAdventure5: Boolean
    get() {
        val parts = libraryMinecraft.get().split('.')
        val major = parts.firstOrNull()?.toIntOrNull() ?: return false
        val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return major > 26 || (major == 26 && minor >= 2)
    }

/**
 * The Adventure version this target's platforms actually run.
 *
 * From 26.2 the ecosystem is on Adventure 5: paper-api 26.2 imports adventure-bom 5.2.0
 * and adventure-platform-fabric 7.x bundles Adventure 5. Everything earlier — including
 * 26.1 — is still on Adventure 4. Adventure 4 and 5 are not interchangeable: `Buildable`
 * was removed, so `ComponentFlattener.toBuilder()` changed descriptor, and
 * `Services.service(ServiceLoader, Class)` was added — shipping the wrong major next to
 * the platform's copy is what makes AdventureCommon.<clinit> die with NoSuchMethodError.
 *
 * Keyed on [usesAdventure5] so a single `-Ptarget=` selects a consistent Adventure across
 * every module and bundle of that target.
 */
fun magicUtilsAdventureVersion(project: Project, target: MagicUtilsTargetExtension): String {
    val versions = project.extensions
        .getByType(org.gradle.api.artifacts.VersionCatalogsExtension::class.java)
        .named("libs")
    val alias = if (target.usesAdventure5) "kyoriAdventure5" else "kyoriAdventure"
    return versions.findVersion(alias)
        .orElseThrow { IllegalStateException("Version catalog 'libs' has no '$alias' version") }
        .requiredVersion
}

/**
 * Moves every `net.kyori:adventure-*` dependency whose major differs from
 * [magicUtilsAdventureVersion] onto that version.
 *
 * The version catalog pins Adventure 4 (what the 1.20/1.21 platforms provide); this lifts
 * the whole graph to Adventure 5 on deobfuscated targets, including the transitive pulls
 * that the catalog never names. Applied to every module so the classes compiled into a
 * bundle and the Adventure shipped beside them are always the same major.
 *
 * Only the major is forced, never the minor. The breakage this exists to prevent is a
 * 4-vs-5 mix; within a major Adventure is compatible, and pinning the minor *down* breaks
 * the platform that asks for a newer one — paper-api 1.21.11 imports adventure-bom 4.26.1
 * and its API signatures reference `PlayerHeadObjectContents`, which 4.24.0 does not have.
 * So a request already on the target's major is left alone and Gradle's usual
 * highest-wins conflict resolution applies.
 *
 * `adventure-platform-*` is deliberately excluded: those track their own version line
 * (7.x for 26.x), unrelated to the Adventure core version.
 */
fun magicUtilsAlignAdventure(project: Project, target: MagicUtilsTargetExtension) {
    val adventureVersion = magicUtilsAdventureVersion(project, target)
    val targetMajor = adventureVersion.substringBefore('.')
    project.configurations.configureEach { configuration ->
        configuration.resolutionStrategy.eachDependency { details ->
            val requested = details.requested
            if (requested.group != "net.kyori" ||
                !requested.name.startsWith("adventure-") ||
                requested.name.startsWith("adventure-platform")
            ) {
                return@eachDependency
            }
            // A blank requested version means the version comes from a platform/BOM —
            // which this same rule has already aligned, so leave it to resolve.
            val requestedMajor = requested.version?.substringBefore('.')?.takeIf { it.isNotBlank() }
                ?: return@eachDependency
            if (requestedMajor != targetMajor) {
                details.useVersion(adventureVersion)
                details.because("MagicUtils aligns Adventure to major $targetMajor for this target")
            }
        }
    }
}

/**
 * Published artifact classifier for the target, e.g. `mc1.21`, `mc26`. Derived
 * from the *library* Minecraft, so a target whose runtime Minecraft differs
 * (e.g. Paper 1.20.6 on the `+1.20.1` library) still resolves the right jar.
 */
val MagicUtilsTargetExtension.mcClassifier: String
    get() = "mc${libraryMinecraft.get().substringBeforeLast('.')}"

/**
 * Bundle artifact ids end in `-bundle` (`magicutils-fabric-bundle`,
 * `magicutils-bukkit-bundle`, ...). Only the five bundle plugins produce them,
 * so this suffix is the reliable discriminator — on both the publish side (the
 * project's artifact id) and the consumer side (the requested module name) —
 * between a bundle and a plain library module, without either side importing the
 * publish-category extension.
 */
fun magicUtilsModuleIsBundle(moduleName: String): Boolean = moduleName.endsWith("-bundle")

/**
 * Published MagicUtils coordinate version for [moduleName] built at [javaLevel].
 *
 * Two kinds of module, two coordinate shapes:
 *  - **Bundles** (`*-bundle`) are fat jars whose shaded dependency set genuinely
 *    differs per Java level / Minecraft branch (the 1.20.x, 1.21.x and 26.x
 *    bundles are not byte-identical), so they keep the `<base>+java<N>` coordinate
 *    — one real variant per Java level.
 *  - **Plain library modules** (core/config/commands/lang, the platform and
 *    fabric modules) are byte-identical across Java levels once the class-file
 *    version word is normalized: the per-level diffs are pure javac codegen
 *    artifacts, not behaviour. Publishing three near-duplicate `+java17/21/25`
 *    copies was redundant, so they now publish once under the **bare** base
 *    version. A `+java17`-compiled class loads fine on any JRE >= 17, so a
 *    consumer on Java 21/25 resolving the bare coordinate runs it unchanged.
 *
 * Consumers pass the bare base version (`magicutils_version=1.27.1`); this is the
 * single place that decides whether the resolved coordinate carries `+java<N>`,
 * so the publish side and every consumer plugin agree by construction.
 */
fun magicUtilsPublishedModuleVersion(moduleName: String, baseVersion: String, javaLevel: Int): String =
    if (magicUtilsModuleIsBundle(moduleName)) javaSuffixedCoordinate(baseVersion, javaLevel) else baseVersion

/**
 * Published MagicUtils version for a [moduleName] on this target. Thin wrapper
 * over [magicUtilsPublishedModuleVersion] that supplies the target's Java level;
 * consumer plugins call this so the module-vs-bundle rule lives in one place.
 */
fun MagicUtilsTargetExtension.publishedVersion(moduleName: String, baseVersion: String): String =
    magicUtilsPublishedModuleVersion(moduleName, baseVersion, java.get())

/**
 * Pure formatter for the `<base>+java<N>` published coordinate. The `+java<N>`
 * suffix format lives here as one function so no caller (the module-version rule,
 * the Modrinth bundle file name, the release smoke URL) re-spells it by hand.
 */
fun javaSuffixedCoordinate(baseVersion: String, javaLevel: Int): String =
    "$baseVersion+java$javaLevel"

/** Loom plugin id for the target: no-remap on deobfuscated, remapping otherwise. */
val MagicUtilsTargetExtension.loomPluginId: String
    get() = if (isDeobfuscated) "net.fabricmc.fabric-loom" else "fabric-loom"

/** Gradle dependency configuration for compile-only deps (mod-aware on remap). */
val MagicUtilsTargetExtension.compileOnlyConfiguration: String
    get() = if (isDeobfuscated) "compileOnly" else "modCompileOnly"

/** Gradle dependency configuration for implementation deps (mod-aware on remap). */
val MagicUtilsTargetExtension.implementationConfiguration: String
    get() = if (isDeobfuscated) "implementation" else "modImplementation"

/** Gradle dependency configuration for runtime-only deps (mod-aware on remap). */
val MagicUtilsTargetExtension.runtimeOnlyConfiguration: String
    get() = if (isDeobfuscated) "runtimeOnly" else "modRuntimeOnly"

/** Task producing the primary (published, jar-in-jar) artifact for the target. */
val MagicUtilsTargetExtension.mainJarTaskName: String
    get() = if (isDeobfuscated) "jar" else "remapJar"

// `applyMinecraftAndMappings` lives in the :fabric subproject
// (MagicUtilsFabricLoomConventions.kt): it needs Loom's API on the compile classpath,
// and this file is part of the platform-neutral artifact that NeoForge/Bukkit/proxy
// consumers resolve.
