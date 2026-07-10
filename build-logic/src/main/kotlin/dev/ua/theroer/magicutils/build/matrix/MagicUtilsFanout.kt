package dev.ua.theroer.magicutils.build.matrix

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.TaskProvider

/**
 * Shared per-target Gradle fan-out.
 *
 * A single Gradle build graph is pinned to one Minecraft target (resolved in
 * `settings.gradle`), so covering every target is inherently N separate Gradle
 * invocations. Every feature that must run "the same task, once per target"
 * (`buildAllTargets`, the local Maven release, the Modrinth bundle build) shells
 * out to the project's own `gradlew` wrapper with `-Ptarget=mcXXXX`. This is the
 * single place that mechanism lives, so no caller re-derives the wrapper name,
 * the child Gradle home, or the dry-run printing.
 *
 * A nested `GradleBuild` task can't be used: Gradle forbids a nested build over a
 * root that itself `includeBuild`s another project (build-logic), which is
 * exactly this layout. Shelling out to `gradlew` is also what CI does per target,
 * so the two paths stay behaviourally identical.
 */

/** The project's Gradle wrapper script name for the current OS. */
internal fun magicUtilsGradleWrapperName(): String =
    if (System.getProperty("os.name").orEmpty().lowercase().contains("win")) "gradlew.bat" else "./gradlew"

/** One per-target invocation: the extra `gradlew` args to run for [target]. */
data class MagicUtilsFanoutInvocation(
    val target: String,
    /** Gradle arguments after the wrapper (tasks + `-P` flags), excluding `-Ptarget`. */
    val args: List<String>,
)

/**
 * Registers one task per [invocations] entry (named `<taskPrefix><Target>`) and
 * returns the providers so the caller can build an aggregate that depends on
 * them.
 *
 * A real run is an [Exec] shelling out to the wrapper; each child runs against a
 * dedicated Gradle user home (`build/<childHomeSubdir>`) so its daemon registry,
 * caches and lock files never collide with the parent build that spawned it (the
 * parent still holds the root project's locks while this runs). Without this the
 * child intermittently fails its cold compile racing the parent for the daemon.
 *
 * When [dryRun] is true each entry is a plain task that just prints the command
 * it would run, so a release can be previewed without spawning any child build.
 */
internal fun registerMagicUtilsFanout(
    project: Project,
    taskPrefix: String,
    taskGroup: String,
    invocations: List<MagicUtilsFanoutInvocation>,
    childHomeSubdir: String,
    dryRun: Boolean,
    describe: (MagicUtilsFanoutInvocation) -> String,
): List<TaskProvider<out Task>> {
    val rootDir = project.rootProject.projectDir
    val wrapper = magicUtilsGradleWrapperName()
    val childGradleHome = project.layout.buildDirectory.dir(childHomeSubdir).get().asFile

    fun commandLine(invocation: MagicUtilsFanoutInvocation): List<String> = buildList {
        add(wrapper)
        addAll(invocation.args)
        // -Ptarget pins the whole include graph in settings.gradle; the child
        // Gradle home isolates the nested invocation from the parent's locks.
        add("-Ptarget=${invocation.target}")
        add("--gradle-user-home=${childGradleHome.absolutePath}")
    }

    return invocations.map { invocation ->
        val taskName = "$taskPrefix${invocation.target.replaceFirstChar(Char::titlecase)}"
        if (dryRun) {
            project.tasks.register(taskName) { task ->
                task.group = taskGroup
                task.description = "[dry-run] ${describe(invocation)}"
                task.doLast { task.logger.lifecycle("[dry-run] ${commandLine(invocation).joinToString(" ")}") }
            }
        } else {
            project.tasks.register(taskName, Exec::class.java) { task ->
                task.group = taskGroup
                task.description = describe(invocation)
                task.workingDir = rootDir
                task.commandLine(commandLine(invocation))
            }
        }
    }
}
