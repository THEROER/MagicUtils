package dev.ua.theroer.magicutils.build.release

import org.gradle.api.Project
import java.io.File

/**
 * Generates a Markdown changelog from git history for a Modrinth release, porting
 * the reusable part of verified-plugin's `publish_release.py`: conventional-commit
 * subjects are grouped into human sections. No network, no AI — just git + this.
 *
 * Conventional-commit type → section title:
 *  feat → Added, fix → Fixed, perf → Improved, refactor → Changed, everything
 *  else → Maintenance. Merge commits, release-bump commits and fixup!/squash!
 *  are dropped. Commits are read `<previousRef>..HEAD` when a previous ref is
 *  given, else the most recent [INITIAL_COMMIT_LIMIT] first-parent commits.
 */
private const val INITIAL_COMMIT_LIMIT = 30

private val CONVENTIONAL = Regex("""^(?<type>[a-z]+)(?:\([^)]+\))?!?:\s*(?<summary>.+)$""", RegexOption.IGNORE_CASE)

/** Bump-the-version commits (any historical phrasing) — dropped as non-user-facing. */
private val VERSION_BUMP = Regex("""^(update|increment|bump)\s+(the\s+)?version""")

private val SECTION_TITLES = linkedMapOf(
    "feat" to "Added",
    "fix" to "Fixed",
    "perf" to "Improved",
    "refactor" to "Changed",
    "other" to "Maintenance",
)

private data class ChangelogCommit(val category: String, val summary: String)

/**
 * Builds the changelog Markdown for [version]. [previousRef] is a git ref (tag)
 * to diff from; when null, the latest [INITIAL_COMMIT_LIMIT] commits are used.
 * Returns an empty string only if git is unavailable.
 */
fun magicUtilsGenerateChangelog(project: Project, version: String, previousRef: String?): String {
    val heading = "## MagicUtils $version"
    val commits = readCommitSummaries(project.rootDir, previousRef)
    if (commits.isEmpty()) {
        return "$heading\n\n- Maintenance release."
    }

    val grouped = SECTION_TITLES.keys.associateWith { mutableListOf<String>() }
    for (commit in commits) grouped.getValue(commit.category).add(commit.summary)

    return buildString {
        append(heading)
        for ((category, title) in SECTION_TITLES) {
            val entries = grouped.getValue(category)
            if (entries.isEmpty()) continue
            append("\n\n### ").append(title)
            entries.forEach { append("\n- ").append(it) }
        }
    }
}

/** Reads + normalises commit subjects from git; empty list if git fails. */
private fun readCommitSummaries(rootDir: File, previousRef: String?): List<ChangelogCommit> {
    // With no explicit base, diff from the previous tag (the most recent tag that
    // is an ancestor of HEAD but not HEAD itself) so a release lists only its own
    // commits. Fall back to the last N commits when there is no such tag.
    val base = previousRef?.takeIf { it.isNotBlank() } ?: latestPreviousTag(rootDir)
    val range = if (base == null) emptyList() else listOf("$base..HEAD")
    val limit = if (base == null) listOf("--max-count=$INITIAL_COMMIT_LIMIT") else emptyList()
    val output = runGit(
        rootDir,
        listOf("log", "--first-parent", "--no-merges", "--format=%s") + limit + range,
    ) ?: return emptyList()

    return output.lineSequence()
        .map(String::trim)
        .filter(String::isNotEmpty)
        .mapNotNull(::normalizeSubject)
        .toList()
}

/** Classifies + tidies one commit subject, or null if it should be dropped. */
private fun normalizeSubject(subject: String): ChangelogCommit? {
    val cleaned = subject.split(Regex("\\s+")).joinToString(" ").trim()
    if (cleaned.isEmpty()) return null
    val lower = cleaned.lowercase()
    if (lower.startsWith("merge ") ||
        lower.startsWith("fixup!") ||
        lower.startsWith("squash!") ||
        lower.startsWith("chore(release): bump version to") ||
        // Version-bump noise in various historical phrasings — never user-facing.
        VERSION_BUMP.containsMatchIn(lower)
    ) {
        return null
    }

    val match = CONVENTIONAL.matchEntire(cleaned)
    val category: String
    var summary: String
    if (match == null) {
        category = "other"
        summary = cleaned
    } else {
        val type = match.groups["type"]!!.value.lowercase()
        category = if (type in setOf("feat", "fix", "perf", "refactor")) type else "other"
        summary = match.groups["summary"]!!.value.trim()
    }

    summary = summary.trimEnd('.')
    if (summary.isEmpty()) return null
    // Capitalise the first character for a tidy bullet.
    summary = summary.replaceFirstChar { it.titlecase() }
    return ChangelogCommit(category, summary)
}

/**
 * The most recent tag reachable from HEAD but not pointing at HEAD (the previous
 * release), or null if there is none. `git describe --tags --abbrev=0 HEAD^`
 * walks back one commit first so a tag ON HEAD (a freshly tagged release) is
 * skipped in favour of the one before it.
 */
private fun latestPreviousTag(rootDir: File): String? =
    runGit(rootDir, listOf("describe", "--tags", "--abbrev=0", "HEAD^"))
        ?.trim()?.takeIf { it.isNotEmpty() }

/** Runs a git command in [rootDir], returning stdout, or null on any failure. */
private fun runGit(rootDir: File, args: List<String>): String? = runCatching {
    val process = ProcessBuilder(listOf("git") + args)
        .directory(rootDir)
        .redirectErrorStream(false)
        .start()
    val out = process.inputStream.bufferedReader().readText()
    if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
        process.destroyForcibly()
        return null
    }
    if (process.exitValue() != 0) null else out
}.getOrNull()
