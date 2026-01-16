import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.MavenArtifactRepository

class MagicUtilsRepositoriesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.repositories.apply {
            mavenCentral()
            maven { it.setUrl(project.uri("https://oss.sonatype.org/content/groups/public/")) }
            maven { it.setUrl(project.uri("https://repo.papermc.io/repository/maven-public/")) }
            maven { it.setUrl(project.uri("https://repo.extendedclip.com/content/repositories/placeholderapi/")) }
            maven { it.setUrl(project.uri("https://maven.nucleoid.xyz")) }
            maven { it.setUrl(project.uri("https://maven.neoforged.net/releases")) }
            maven { it.setUrl(project.uri("https://repo.velocitypowered.com/releases/")) }
            maven { it.setUrl(project.uri("https://maven.fabricmc.net/")) }
        }
    }
}
