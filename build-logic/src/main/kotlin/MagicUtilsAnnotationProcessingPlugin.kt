import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.*

class MagicUtilsAnnotationProcessingPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        if (project.name != "processor") {
            project.dependencies.add("annotationProcessor", project.project(":processor")) 

            project.tasks.withType(JavaCompile::class.java).configureEach { javaCompileTask ->
                javaCompileTask.options.compilerArgs.addAll(
                    listOf(
                        "-processor",
                        "dev.ua.theroer.magicutils.processor.LogMethodsProcessor," +
                                "dev.ua.theroer.magicutils.processor.NoOpProcessor," +
                                "lombok.launch.AnnotationProcessorHider\$AnnotationProcessor," +
                                "lombok.launch.AnnotationProcessorHider\$ClaimingProcessor"
                    )
                )
            }
        }
    }
}
