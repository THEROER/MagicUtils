import org.gradle.api.Plugin
import org.gradle.api.Project

class MagicUtilsRepositoriesPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        project.repositories.apply {
            mavenCentral()
            maven {
                it.name = "PaperMC"
                it.setUrl(project.uri("https://repo.papermc.io/repository/maven-public/"))
                it.content { content ->
                    content.includeGroupByRegex("io\\.papermc(\\..*)?")
                    content.includeGroup("com.destroystokyo.paper")
                    content.includeGroup("com.mojang")
                    content.includeGroup("net.md-5")
                    content.includeGroupByRegex("com\\.velocitypowered(\\..*)?")
                }
            }
            maven {
                it.name = "PlaceholderAPI"
                it.setUrl(project.uri("https://repo.extendedclip.com/content/repositories/placeholderapi/"))
                it.content { content ->
                    content.includeGroup("me.clip")
                }
            }
            maven {
                it.name = "Nucleoid"
                it.setUrl(project.uri("https://maven.nucleoid.xyz"))
                it.content { content ->
                    content.includeGroup("eu.pb4")
                    content.includeGroup("xyz.nucleoid")
                }
            }
            maven {
                it.name = "NeoForged"
                it.setUrl(project.uri("https://maven.neoforged.net/releases"))
                it.content { content ->
                    content.includeGroup("net.neoforged")
                }
            }
            maven {
                it.name = "FabricMC"
                it.setUrl(project.uri("https://maven.fabricmc.net/"))
                it.content { content ->
                    content.includeGroup("net.fabricmc")
                    content.includeGroup("net.minecraft")
                    content.includeGroup("com.mojang")
                }
            }
        }
    }
}
