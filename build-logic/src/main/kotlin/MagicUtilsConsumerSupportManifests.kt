internal fun magicUtilsOrderedMap(vararg entries: Pair<String, Any?>): LinkedHashMap<String, Any> =
    linkedMapOf<String, Any>().apply {
        for ((key, value) in entries) {
            if (value != null) {
                put(key, value)
            }
        }
    }

private fun magicUtilsGradleProperties(vararg entries: Pair<String, String>): Map<String, String> =
    linkedMapOf<String, String>().apply {
        for ((key, value) in entries) {
            put(key, value)
        }
    }

private fun magicUtilsDefaults(vararg entries: Pair<String, String>): Map<String, Any> =
    magicUtilsOrderedMap("gradle_properties" to magicUtilsGradleProperties(*entries))

private fun magicUtilsVersionMatrixEntry(
    id: String,
    versions: List<String>,
    smokeValues: List<String>,
    gradleProperties: Map<String, String> = emptyMap(),
    smokeGradleProperties: Map<String, Map<String, String>> = emptyMap(),
    releaseArtifactId: String? = null,
    successPattern: String? = null,
): Map<String, Any> = magicUtilsOrderedMap(
    "id" to id,
    "versions" to versions,
    "smoke_values" to smokeValues,
    "gradle_properties" to gradleProperties.takeIf { it.isNotEmpty() },
    "smoke_gradle_properties" to smokeGradleProperties.takeIf { it.isNotEmpty() },
    "release_artifact" to releaseArtifactId?.let { magicUtilsOrderedMap("id" to it) },
    "success_pattern" to successPattern,
)

internal fun verifiedPluginSupportManifest(): Map<String, Any> =
    magicUtilsOrderedMap(
        "defaults" to magicUtilsDefaults(
            "javaVersion" to "21",
            "magicutilsVersion" to "1.21.3",
            "target" to "mc12110",
        ),
        "platforms" to magicUtilsOrderedMap(
            "bukkit" to magicUtilsOrderedMap(
                "defaults" to magicUtilsDefaults(
                    "paperVersion" to "1.20.6-R0.1-SNAPSHOT",
                    "bukkitApiVersion" to "1.20",
                ),
                "version_matrix" to listOf(
                    magicUtilsVersionMatrixEntry(
                        id = "paper-120x",
                        releaseArtifactId = "java17",
                        versions = listOf("1.20-1.20.4"),
                        smokeValues = listOf("1.20", "1.20.4"),
                        gradleProperties = magicUtilsGradleProperties(
                            "javaVersion" to "17",
                            "magicutilsVersion" to "1.21.3-mc1201",
                            "paperVersion" to "1.20-R0.1-SNAPSHOT",
                            "target" to "mc1201",
                        ),
                    ),
                    magicUtilsVersionMatrixEntry(
                        id = "paper-1205x",
                        releaseArtifactId = "java21-mc1201",
                        versions = listOf("1.20.5-1.20.6"),
                        smokeValues = listOf("1.20.5", "1.20.6"),
                        gradleProperties = magicUtilsGradleProperties(
                            "magicutilsVersion" to "1.21.3-mc1201",
                            "target" to "mc1201",
                        ),
                    ),
                    magicUtilsVersionMatrixEntry(
                        id = "paper-121x",
                        versions = listOf("1.21-1.21.11"),
                        smokeValues = listOf("1.21", "1.21.7", "1.21.11"),
                    ),
                    magicUtilsVersionMatrixEntry(
                        id = "paper-261x",
                        releaseArtifactId = "java25",
                        versions = listOf("26.1-26.1.2"),
                        smokeValues = listOf("26.1", "26.1.2"),
                        gradleProperties = magicUtilsGradleProperties(
                            "javaVersion" to "25",
                            "magicutilsVersion" to "1.21.3-mc2611",
                            "paperVersion" to "26.1.2.build.19-alpha",
                            "target" to "mc2611",
                        ),
                    ),
                ),
            ),
            "bungee" to magicUtilsOrderedMap(
                "version_matrix" to listOf(
                    magicUtilsVersionMatrixEntry(
                        id = "bungee-runtime",
                        versions = listOf("1.21.6-1.21.11"),
                        smokeValues = listOf("1.20-R0.1"),
                    ),
                ),
            ),
            "velocity" to magicUtilsOrderedMap(
                "version_matrix" to listOf(
                    magicUtilsVersionMatrixEntry(
                        id = "velocity-runtime",
                        versions = listOf("1.21.6-1.21.11"),
                        smokeValues = listOf("3.3.0-SNAPSHOT"),
                    ),
                ),
            ),
            "fabric" to magicUtilsOrderedMap(
                "defaults" to magicUtilsDefaults(
                    "magicutilsFabricBundleVersion" to "1.21.3",
                    "fabricLoaderVersion" to "0.18.4",
                    "fabricApiVersion" to "0.102.0+1.21.1",
                    "fabricMinecraftVersion" to "1.21.1",
                    "fabricMinecraftVersionPredicate" to "1.21.x",
                ),
                "version_matrix" to listOf(
                    magicUtilsVersionMatrixEntry(
                        id = "fabric-120x",
                        releaseArtifactId = "mc120x",
                        versions = listOf("1.20.1-1.20.6"),
                        smokeValues = listOf("1.20.1", "1.20.6"),
                        gradleProperties = magicUtilsGradleProperties(
                            "fabricLoaderVersion" to "0.16.10",
                            "fabricApiVersion" to "0.92.8+1.20.1",
                            "fabricMinecraftVersion" to "1.20.1",
                            "fabricMinecraftVersionPredicate" to "1.20.x",
                            "target" to "mc1201",
                        ),
                        smokeGradleProperties = mapOf(
                            "1.20.6" to magicUtilsGradleProperties(
                                "fabricApiVersion" to "0.100.8+1.20.6",
                                "fabricMinecraftVersion" to "1.20.6",
                            ),
                        ),
                    ),
                    magicUtilsVersionMatrixEntry(
                        id = "fabric-121x",
                        versions = listOf("1.21.1-1.21.11"),
                        smokeValues = listOf("1.21.1", "1.21.11"),
                        smokeGradleProperties = mapOf(
                            "1.21.11" to magicUtilsGradleProperties(
                                "fabricLoaderVersion" to "0.19.2",
                                "fabricApiVersion" to "0.141.3+1.21.11",
                                "fabricMinecraftVersion" to "1.21.11",
                            ),
                        ),
                    ),
                ),
            ),
            "neoforge" to magicUtilsOrderedMap(
                "defaults" to magicUtilsDefaults(
                    "neoForgeVersion" to "21.1.77",
                    "neoForgeMinecraftVersion" to "1.21.1",
                    "neoForgeMinecraftVersionRange" to "[1.21.1,1.21.12)",
                ),
                "version_matrix" to listOf(
                    magicUtilsVersionMatrixEntry(
                        id = "neoforge-120x",
                        releaseArtifactId = "mc1206",
                        versions = listOf("1.20.6"),
                        smokeValues = listOf("1.20.6"),
                        gradleProperties = magicUtilsGradleProperties(
                            "neoForgeVersion" to "20.6.137",
                            "neoForgeMinecraftVersion" to "1.20.6",
                            "neoForgeMinecraftVersionRange" to "[1.20.6,1.20.7)",
                        ),
                        successPattern = "Done (",
                    ),
                    magicUtilsVersionMatrixEntry(
                        id = "neoforge-121x",
                        versions = listOf("1.21.1-1.21.11"),
                        smokeValues = listOf("1.21.1", "1.21.11"),
                        smokeGradleProperties = mapOf(
                            "1.21.11" to magicUtilsGradleProperties(
                                "neoForgeVersion" to "21.11.42",
                                "neoForgeMinecraftVersion" to "1.21.11",
                            ),
                        ),
                        successPattern = "Done (",
                    ),
                ),
            ),
        ),
    )
