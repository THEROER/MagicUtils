import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

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

        registerListBuildMatrixTask(project, resolvedContext)
        registerScenarioAggregateTasks(project, resolvedContext)
        registerSelectedScenarioTasks(project, resolvedContext)
        registerPublishCategoryTasks(project)
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
