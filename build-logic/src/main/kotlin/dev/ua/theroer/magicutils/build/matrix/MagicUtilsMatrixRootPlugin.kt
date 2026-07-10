package dev.ua.theroer.magicutils.build.matrix

import dev.ua.theroer.magicutils.build.publish.*
import dev.ua.theroer.magicutils.build.release.*
import dev.ua.theroer.magicutils.build.smoke.*
import dev.ua.theroer.magicutils.build.target.*

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.external.javadoc.StandardJavadocDocletOptions
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainService
import dev.ua.theroer.magicutils.build.module.MAGICUTILS_BUILD_JDK

class MagicUtilsMatrixRootPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        require(project == project.rootProject) {
            "magicutils.matrix-root must be applied only to the root project."
        }

        val resolvedContext = project.gradle.extensions.extraProperties.properties["magicutilsMatrixResolved"]
            as? MagicUtilsMatrixResolvedContext
            ?: throw GradleException(
                "MagicUtils matrix context is missing. Apply magicutils.matrix-settings in settings.gradle."
            )

        val publishingSpec = project.gradle.extensions.extraProperties.properties["magicutilsPublishingSpec"]
            as? MagicUtilsPublishingSpec
            ?: throw GradleException(
                "MagicUtils publishing spec is missing. Apply magicutils.matrix-settings in settings.gradle."
            )

        registerMagicUtilsHelpTask(project, resolvedContext)
        registerListBuildMatrixTask(project, resolvedContext)
        registerMatrixJsonTasks(project, resolvedContext)
        registerScenarioAggregateTasks(project, resolvedContext)
        registerSelectedScenarioTasks(project, resolvedContext)

        val allTargetsSpec = project.gradle.extensions.extraProperties.properties["magicutilsAllTargetsSpec"]
            as? MagicUtilsAllTargetsSpec ?: MagicUtilsAllTargetsSpec(emptyList(), null, MagicUtilsAllTargetsTaskType.BUILD)
        registerAllTargetsTask(project, resolvedContext, allTargetsSpec)
        registerAggregatedJavadocTask(project, resolvedContext)
        registerDevServerAggregateTasks(project)
        registerPublishCategoryTasks(project)
        val defaultTargetJava = resolveMagicUtilsTargetSpec(
            targetsFile = project.rootProject.file(resolvedContext.definition.targetsFile),
            defaultTarget = resolvedContext.definition.defaultTarget,
            explicitTarget = resolvedContext.definition.defaultTarget,
        ).java
        val modrinthSpec = project.gradle.extensions.extraProperties.properties["magicutilsModrinthSpec"]
            as? ModrinthReleaseSpec
        registerReleaseTasks(project, publishingSpec, defaultTargetJava, modrinthSpec?.projectId)

        @Suppress("UNCHECKED_CAST")
        val smokeSpecs = project.gradle.extensions.extraProperties.properties["magicutilsSmokeSpecs"]
            as? List<SmokePlatformSpec> ?: emptyList()
        val defaultGate = project.gradle.extensions.extraProperties.properties["magicutilsSmokeGate"]
            as? SmokeGate ?: SmokeGate.STRICT
        registerSmokeTasks(
            project,
            smokeSpecs.toSmokeCases(resolvedContext.definition.defaultTarget),
            defaultGate,
        )
        registerReleaseMatrixTask(project, resolvedContext, smokeSpecs)

        val targetsFile = project.rootProject.file(resolvedContext.definition.targetsFile)
        registerModrinthTasks(project, modrinthSpec, smokeSpecs, resolvedContext.definition.defaultTarget, targetsFile)
    }
}

/**
 * Machine-readable matrix outputs consumed by CI (`fromJson` in a GitHub
 * Actions `strategy.matrix`). Both print a single JSON line to stdout so
 * they can be captured with `./gradlew -q print...`.
 *
 * - `printBuildMatrix`: every declared target, for build+check fan-out.
 * - `printPublishMatrix`: every target with its publish tasks + classifier
 *   suffix flag, for the publish fan-out.
 *
 * The target list and per-target platform availability come from
 * `targets.properties` via [loadAllTargetNames]/[MagicUtilsMatrixDefinition.publishUnits],
 * so workflows never hardcode a target list.
 */
private fun registerMatrixJsonTasks(
    project: Project,
    resolvedContext: MagicUtilsMatrixResolvedContext,
) {
    val definition = resolvedContext.definition
    val targetsFile = project.rootProject.file(definition.targetsFile)

    project.tasks.register("printPublishMatrix") { task ->
        task.group = "help"
        task.description = "Print the publish matrix (target + publish tasks) as JSON for CI."
        task.doLast {
            val units = definition.publishUnits(loadAllTargetNames(targetsFile)) { target ->
                resolveMagicUtilsTargetSpec(
                    targetsFile = targetsFile,
                    defaultTarget = definition.defaultTarget,
                    explicitTarget = target,
                ).java
            }
            println(units.toMatrixJson())
        }
    }

    project.tasks.register("printBuildMatrix") { task ->
        task.group = "help"
        task.description = "Print the build matrix (list of targets) as JSON for CI."
        task.doLast {
            val targets = loadAllTargetNames(targetsFile)
            println(targets.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" })
        }
    }
}

/**
 * `printReleaseMatrix`: one JSON row per (platform × smoke entry), for a
 * consumer's release fan-out. Each row carries the platform, the build target,
 * its published-artifact classifier, and the entry's declared Minecraft version
 * ranges (the Modrinth game-version source). This is the reusable, tool-side
 * half of what the former Python `release_support.py release-json` produced;
 * the consumer's workflow turns each row into a build command + dist file name
 * (those names are consumer-specific and intentionally not encoded here).
 *
 * Target per entry comes from `gradleProperties["target"]`, falling back to the
 * matrix default — the same resolution the smoke run uses, so the release build
 * and the smoke gate always agree on the target.
 */
private fun registerReleaseMatrixTask(
    project: Project,
    resolvedContext: MagicUtilsMatrixResolvedContext,
    smokeSpecs: List<SmokePlatformSpec>,
) {
    val definition = resolvedContext.definition
    val targetsFile = project.rootProject.file(definition.targetsFile)

    project.tasks.register("printReleaseMatrix") { task ->
        task.group = "help"
        task.description = "Print the release matrix (platform, target, classifier, game versions) as JSON for CI."
        task.doLast {
            fun jsonArray(values: List<String>): String =
                values.joinToString(prefix = "[", postfix = "]", separator = ",") { "\"$it\"" }

            val rows = smokeSpecs.flatMap { platform ->
                val primaries = platform.versionMatrix.count { it.primary }
                if (primaries > 1) {
                    throw GradleException(
                        "Smoke platform '${platform.name}' marks $primaries entries primary; at most one allowed."
                    )
                }
                platform.versionMatrix.map { entry ->
                    val spec = resolveMagicUtilsTargetSpec(
                        targetsFile = targetsFile,
                        defaultTarget = definition.defaultTarget,
                        explicitTarget = entry.resolvedTarget(definition.defaultTarget),
                    )
                    """{"platform":"${platform.name}","entry":"${entry.id}",""" +
                        """"target":"${spec.name}","classifier":"${spec.mcClassifier()}",""" +
                        """"primary":${entry.primary},""" +
                        """"gameVersions":${jsonArray(entry.versions.expandVersionsFull())}}"""
                }
            }
            println(rows.joinToString(prefix = "[", postfix = "]", separator = ","))
        }
    }
}

private fun registerPublishCategoryTasks(project: Project) {
    fun aggregate(taskName: String, description: String, categories: Set<MagicUtilsPublishCategory>) {
        project.tasks.register(taskName) { task ->
            task.group = "publishing"
            task.description = description
            task.dependsOn(project.provider {
                project.rootProject.subprojects
                    .filter { it.magicUtilsPublishCategory() in categories }
                    .map { "${it.path}:publish" }
            })
        }
    }

    aggregate(
        taskName = "publishDefaultMatrix",
        description = "Publish every publishable module on the default Minecraft target.",
        categories = setOf(
            MagicUtilsPublishCategory.DEFAULT_ONLY,
            MagicUtilsPublishCategory.COMMON_MATRIX,
            MagicUtilsPublishCategory.FABRIC_MATRIX,
        ),
    )
    aggregate(
        taskName = "publishCommonMatrix",
        description = "Publish modules whose category is COMMON_MATRIX (use with -Ptarget=mcXXXX).",
        categories = setOf(MagicUtilsPublishCategory.COMMON_MATRIX),
    )
    aggregate(
        taskName = "publishFabricMatrix",
        description = "Publish modules whose category is FABRIC_MATRIX (use with -Ptarget=mcXXXX).",
        categories = setOf(MagicUtilsPublishCategory.FABRIC_MATRIX),
    )
}

private fun registerListBuildMatrixTask(
    project: Project,
    resolvedContext: MagicUtilsMatrixResolvedContext,
) {
    project.tasks.register("listBuildMatrix") { task ->
        task.group = "help"
        task.description = "Print the resolved MagicUtils target, platforms, scenarios, and included projects."
        task.doLast {
            println("MagicUtils build matrix")
            println("  target: ${resolvedContext.target.name}")
            println("  minecraft: ${resolvedContext.target.minecraft}")
            if (resolvedContext.target.libraryMinecraft != resolvedContext.target.minecraft) {
                println("  library minecraft: ${resolvedContext.target.libraryMinecraft}")
            }
            println("  java: ${resolvedContext.target.java}")
            println("  available platforms: ${resolvedContext.availablePlatforms.joinToString(", ")}")
            println("  selected scenario: ${resolvedContext.selectedScenario ?: "workspace"}")
            println("  selected platforms: ${resolvedContext.selectedPlatforms.joinToString(", ")}")
            println("  included projects: ${resolvedContext.includedProjects.joinToString(", ")}")
            println("  scenarios:")
            for (scenario in resolvedContext.definition.scenarios.values) {
                val descriptionSuffix = scenario.description.takeIf(String::isNotBlank)?.let { " - $it" } ?: ""
                println("    ${scenario.name}: ${scenario.platforms.joinToString(", ")}$descriptionSuffix")
            }
        }
    }
}

/**
 * `buildAllTargets`: the "build everything, for every advertised version" fan-out.
 *
 * A single Gradle build graph is pinned to one Minecraft target (resolved in
 * `settings.gradle`), so covering every target is inherently N separate Gradle
 * invocations. This registers one [org.gradle.api.tasks.Exec] per target — each
 * shells out to the project's own `gradlew` wrapper with `-Ptarget=mcXXXX` — and
 * an aggregate `buildAllTargets` that depends on them all. The default
 * `./gradlew build` is left untouched (single default target); this is the opt-in
 * "all versions" task.
 *
 * A nested `GradleBuild` task can't be used here: Gradle forbids a nested build
 * over a root that itself `includeBuild`s another project (build-logic), which is
 * exactly this layout. Shelling out to `gradlew` is also what CI does per target,
 * so the two paths stay behaviourally identical.
 *
 * Shaped by `magicMatrix { allTargets { ... } }` (targets subset, scenario, task
 * type) and overridable per-invocation with:
 *   -PallTargets.targets=mc12110,mc262   (comma list; overrides the DSL subset)
 *   -PallTargets.scenario=fabric         (overrides the DSL/default scenario)
 *   -PallTargets.taskType=publishToMavenLocal
 */
private fun registerAllTargetsTask(
    project: Project,
    resolvedContext: MagicUtilsMatrixResolvedContext,
    spec: MagicUtilsAllTargetsSpec,
) {
    val definition = resolvedContext.definition
    val targetsFile = project.rootProject.file(definition.targetsFile)

    fun stringProperty(name: String): String? =
        (project.findProperty(name) as? String)?.trim()?.takeIf { it.isNotEmpty() }

    // CLI overrides win over the DSL config, so a consumer can retarget a one-off
    // run without editing settings.gradle.
    val overrideTargets = stringProperty("allTargets.targets")
        ?.split(',')
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.map(::normalizeTargetName)
        ?: emptyList()
    val effectiveSpec = spec.copy(
        targets = overrideTargets.ifEmpty { spec.targets },
        scenario = stringProperty("allTargets.scenario")?.lowercase() ?: spec.scenario,
        taskType = stringProperty("allTargets.taskType")
            ?.let(MagicUtilsAllTargetsTaskType::fromToken)
            ?: spec.taskType,
    )

    val resolvedTargets = runCatching { effectiveSpec.resolveTargets(targetsFile) }.getOrElse { error ->
        // Defer the failure to task execution so plain configuration (IDE sync,
        // `tasks`) never breaks just because a subset typo exists in the DSL.
        project.tasks.register("buildAllTargets") { task ->
            task.group = "matrix"
            task.description = "Build every declared target (misconfigured — see error on run)."
            task.doFirst { throw GradleException(error.message ?: "Invalid allTargets configuration.") }
        }
        return
    }

    val rootDir = project.rootProject.projectDir
    val isWindows = System.getProperty("os.name").orEmpty().lowercase().contains("win")
    val wrapper = if (isWindows) "gradlew.bat" else "./gradlew"
    // Child builds run against a dedicated Gradle user home so their daemon
    // registry, caches and lock files never collide with the parent build that
    // spawned them (the parent is still holding the root project's locks while
    // this task executes). Without this the child intermittently fails its cold
    // compile racing the parent for the same daemon/locks.
    val childGradleHome = project.layout.buildDirectory.dir("all-targets-gradle-home").get().asFile

    val perTargetTasks = resolvedTargets.map { targetName ->
        project.tasks.register(
            "buildTarget${targetName.replaceFirstChar(Char::titlecase)}",
            org.gradle.api.tasks.Exec::class.java,
        ) { task ->
            task.group = "matrix"
            task.description = "Run '${effectiveSpec.taskType.gradleTask}' for target $targetName."
            task.workingDir = rootDir
            // Each target is a fresh Gradle invocation; -Ptarget pins its whole
            // include graph in settings.gradle. Scenario limits which platforms
            // that run builds (defaults to the matrix default scenario when null).
            val args = mutableListOf(
                wrapper,
                effectiveSpec.taskType.gradleTask,
                "-Ptarget=$targetName",
                "--gradle-user-home=${childGradleHome.absolutePath}",
            )
            effectiveSpec.scenario?.let { args += "-Pscenario=$it" }
            task.commandLine(args)
        }
    }

    project.tasks.register("buildAllTargets") { task ->
        task.group = "matrix"
        task.description = "Run '${effectiveSpec.taskType.gradleTask}' for every declared target " +
            "(${resolvedTargets.joinToString(", ")})."
        task.dependsOn(perTargetTasks)
        task.doLast {
            println(
                "buildAllTargets: '${effectiveSpec.taskType.gradleTask}' completed for " +
                    "${resolvedTargets.size} targets (${resolvedTargets.joinToString(", ")})" +
                    (effectiveSpec.scenario?.let { ", scenario=$it" } ?: "")
            )
        }
    }
}

private fun registerScenarioAggregateTasks(
    project: Project,
    resolvedContext: MagicUtilsMatrixResolvedContext,
) {
    for (scenario in resolvedContext.definition.scenarios.values) {
        val scenarioPlatforms = scenario.platforms.intersect(resolvedContext.availablePlatforms)
        if (scenarioPlatforms.isEmpty()) {
            continue
        }

        val scenarioProjects = scenarioPlatforms
            .flatMap { platformName -> resolvedContext.definition.platforms.getValue(platformName).projects }
            .mapNotNull(project.rootProject::findProject)
            .distinctBy(Project::getPath)
        if (scenarioProjects.isEmpty()) {
            continue
        }

        val taskSuffix = capitalizeScenarioToken(scenario.name)
        registerAggregateTask(
            project = project,
            taskName = "build$taskSuffix",
            description = "Build the ${scenario.name} scenario modules.",
            targetTaskName = "build",
            scenarioProjects = scenarioProjects,
        )
        registerAggregateTask(
            project = project,
            taskName = "check$taskSuffix",
            description = "Run checks for the ${scenario.name} scenario modules.",
            targetTaskName = "check",
            scenarioProjects = scenarioProjects,
        )
        registerAggregateTask(
            project = project,
            taskName = "publishToMavenLocal$taskSuffix",
            description = "Publish the ${scenario.name} scenario modules to Maven Local.",
            targetTaskName = "publishToMavenLocal",
            scenarioProjects = scenarioProjects,
        )
    }
}

private fun registerSelectedScenarioTasks(
    project: Project,
    resolvedContext: MagicUtilsMatrixResolvedContext,
) {
    val selectedScenarioProjects = resolvedContext.selectedPlatforms
        .flatMap { platformName -> resolvedContext.definition.platforms.getValue(platformName).projects }
        .mapNotNull(project.rootProject::findProject)
        .distinctBy(Project::getPath)

    registerAggregateTask(
        project = project,
        taskName = "buildScenario",
        description = "Build the modules resolved for the current MagicUtils scenario selection.",
        targetTaskName = "build",
        scenarioProjects = selectedScenarioProjects,
    )
    registerAggregateTask(
        project = project,
        taskName = "checkScenario",
        description = "Run checks for the modules resolved for the current MagicUtils scenario selection.",
        targetTaskName = "check",
        scenarioProjects = selectedScenarioProjects,
    )
    registerAggregateTask(
        project = project,
        taskName = "publishScenarioToMavenLocal",
        description = "Publish the modules resolved for the current MagicUtils scenario selection to Maven Local.",
        targetTaskName = "publishToMavenLocal",
        scenarioProjects = selectedScenarioProjects,
    )
}

/**
 * Registers root-level convenience run tasks that delegate into the platform
 * subproject's runner: `runPaper`/`runFolia` -> `:bukkit:runServer`/`:runFolia`,
 * `runFabric` -> `:fabric:runServer`, `runVelocity` -> `:velocity:runVelocity`.
 *
 * The root task is registered whenever the conventional subproject exists; the
 * delegate is referenced lazily via a `TaskProvider` obtained from the
 * subproject, so wiring does not depend on whether the delegate has been created
 * yet at root-configuration time (it is created by the consumer-* plugin only on
 * `devServer {}` opt-in, in the subproject's own configuration). If the opt-in
 * never happens the delegate provider stays unrealised and the root task simply
 * has nothing to run. Consumers using non-conventional subproject names keep
 * wiring their own aggregate tasks.
 */
private fun registerDevServerAggregateTasks(project: Project) {
    data class RootRunner(
        val rootTaskName: String,
        val subprojectName: String,
        val delegateTaskName: String,
        val description: String,
    )

    val runners = listOf(
        RootRunner("runPaper", "bukkit", "runServer", "Run a Paper dev server with this plugin."),
        RootRunner("runFolia", "bukkit", "runFolia", "Run a Folia dev server with this plugin."),
        RootRunner("runFabric", "fabric", "runServer", "Run a Fabric dev server with this mod."),
        RootRunner("runVelocity", "velocity", "runVelocity", "Run a Velocity proxy with this plugin."),
    )

    for (runner in runners) {
        val subproject = project.rootProject.findProject(runner.subprojectName) ?: continue
        if (project.tasks.findByName(runner.rootTaskName) != null) continue
        project.tasks.register(runner.rootTaskName) { task ->
            task.group = "run"
            task.description = runner.description
            // Resolve the delegate lazily: the provider is evaluated during task
            // graph construction, after every subproject's afterEvaluate has run,
            // so it does not matter whether the delegate exists yet here. If the
            // module never opted into devServer the delegate is absent and the
            // root task fails only if actually invoked.
            task.dependsOn(
                project.provider {
                    val delegate = subproject.tasks.findByName(runner.delegateTaskName)
                        ?: error(
                            "Task '${runner.rootTaskName}' requires ${subproject.path}:${runner.delegateTaskName}, " +
                                "which is created by `magicutilsConsumer { devServer { } }`. Add that to " +
                                "${subproject.path}'s build script.",
                        )
                    delegate
                },
            )
        }
    }
}

/**
 * `aggregatedJavadoc`: one Javadoc HTML tree covering the common (platform-neutral)
 * modules on the resolved default target, themed for the MagicUtils docs site.
 *
 * The common modules all live under the single `dev.ua.theroer` package tree and
 * compile against one Minecraft target, so a single Javadoc invocation over their
 * combined `main` source sets produces a clean, dedup-free reference. Platform
 * adapters are intentionally excluded: they exist per Minecraft version and would
 * introduce duplicate classes across targets.
 *
 * `processor` (annotation processor internals) and `diagnostics-testkit` (test
 * helpers) are excluded — they are not part of the consumer-facing API.
 *
 * Output: `build/docs/aggregatedJavadoc/`. Sources/classpaths are wired lazily so
 * the task is created at root-configuration time regardless of subproject eval
 * order, and the JDK 25 toolchain is used to match the build toolchain.
 */
private fun registerAggregatedJavadocTask(
    project: Project,
    resolvedContext: MagicUtilsMatrixResolvedContext,
) {
    // commonProjects stores normalized `:name` paths (see normalizeProjectPath).
    val excluded = setOf(":processor", ":diagnostics-testkit")
    val javadocProjectNames = resolvedContext.definition.commonProjects - excluded

    // The root project only applies `base`, so the toolchain service (normally
    // contributed by the `java` plugin) is not present. Apply the lightweight
    // `jvm-toolchains` plugin to get it without pulling in a full java setup.
    project.pluginManager.apply("jvm-toolchains")
    val toolchains = project.extensions.getByType(JavaToolchainService::class.java)
    val javadocTool = toolchains.javadocToolFor { spec ->
        spec.languageVersion.set(JavaLanguageVersion.of(MAGICUTILS_BUILD_JDK))
    }

    val themeDir = project.rootProject.file("gradle/javadoc")

    project.tasks.register("aggregatedJavadoc", Javadoc::class.java) { task ->
        task.group = "documentation"
        task.description =
            "Generate one themed Javadoc tree for the common MagicUtils modules (magicutils.theroer.dev/javadoc)."

        task.javadocTool.set(javadocTool)
        task.setDestinationDir(project.layout.buildDirectory.dir("docs/aggregatedJavadoc").get().asFile)

        // Resolve the common subprojects' main source sets + compile classpaths
        // lazily, after every subproject has been evaluated.
        val javadocProjects = javadocProjectNames.mapNotNull(project.rootProject::findProject)
        task.dependsOn(project.provider {
            javadocProjects.mapNotNull { it.tasks.findByName("compileJava") }
        })

        task.source(project.provider {
            javadocProjects.mapNotNull { subproject ->
                val sourceSets = subproject.extensions.findByType(SourceSetContainer::class.java) ?: return@mapNotNull null
                sourceSets.findByName("main")?.allJava
            }
        })

        task.classpath = project.files(project.provider {
            javadocProjects.mapNotNull { subproject ->
                val sourceSets = subproject.extensions.findByType(SourceSetContainer::class.java) ?: return@mapNotNull null
                val main = sourceSets.findByName("main") ?: return@mapNotNull null
                main.compileClasspath + main.output
            }
        })

        val options = task.options as StandardJavadocDocletOptions
        options.encoding = "UTF-8"
        options.docEncoding = "UTF-8"
        options.charSet = "UTF-8"
        options.windowTitle = "MagicUtils API"
        options.docTitle = "MagicUtils API"
        options.author(false)
        options.use(true)
        options.splitIndex(true)
        // Reference-only: do not fail the build on missing @param/@return etc.
        options.addStringOption("Xdoclint:none", "-quiet")
        // Brand skin + persistent "back to docs" banner on every page.
        // --add-stylesheet APPENDS our brand skin after Javadoc's default
        // stylesheet (which keeps all the base layout), so our overrides win by
        // cascade order without discarding the default. -stylesheetfile would
        // REPLACE the default and break the layout, so it is deliberately avoided.
        options.addStringOption("-add-stylesheet", themeDir.resolve("theme.css").absolutePath)
        // Single-dash standard options: addStringOption(key) emits "-key". The
        // -top HTML is injected at the top of every generated page.
        options.addStringOption("top", themeDir.resolve("top.html").readTextOrEmpty())
        // No external -links: a fetch failure (offline CI, moved package-list)
        // is a hard Javadoc error. Cross-links to Adventure are not worth
        // making generation network-dependent.
    }

    // Package the generated tree as a single zip for delivery. CI publishes this
    // to Reposilite; the docs Docker build downloads and unzips it into
    // `public/javadoc/` (the docs image is bun-only and cannot run Javadoc).
    project.tasks.register("aggregatedJavadocZip", Zip::class.java) { task ->
        task.group = "documentation"
        task.description = "Package the aggregated Javadoc tree as a zip for delivery to the docs site."
        task.dependsOn("aggregatedJavadoc")
        task.from(project.layout.buildDirectory.dir("docs/aggregatedJavadoc"))
        task.archiveFileName.set("magicutils-javadoc.zip")
        task.destinationDirectory.set(project.layout.buildDirectory.dir("docs"))
    }
}

private fun java.io.File.readTextOrEmpty(): String =
    if (exists()) readText(Charsets.UTF_8) else ""

private fun registerAggregateTask(
    project: Project,
    taskName: String,
    description: String,
    targetTaskName: String,
    scenarioProjects: List<Project>,
) {
    project.tasks.register(taskName) { task ->
        task.group = "matrix"
        task.description = description
        task.dependsOn(scenarioProjects.map { scenarioProject -> "${scenarioProject.path}:$targetTaskName" })
    }
}
