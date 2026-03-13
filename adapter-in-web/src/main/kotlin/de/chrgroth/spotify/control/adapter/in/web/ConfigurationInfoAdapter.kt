package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.ConfigEntry
import de.chrgroth.spotify.control.domain.model.ConfigurationStats
import de.chrgroth.spotify.control.domain.port.out.ConfigurationInfoPort
import jakarta.enterprise.context.ApplicationScoped
import mu.KLogging
import org.eclipse.microprofile.config.ConfigProvider
import org.eclipse.microprofile.config.inject.ConfigProperty

private const val MASKED_VALUE = "***"
private const val ERROR_VALUE = "ERROR"

@ApplicationScoped
@Suppress("Unused")
class ConfigurationInfoAdapter(
    @ConfigProperty(name = "app.health.masked-config-keys")
    maskedConfigKeys: List<String>,
    @ConfigProperty(name = "app.health.masked-env-keys")
    maskedEnvKeys: List<String>,
) : ConfigurationInfoPort {

    private val configKeyMasks = maskedConfigKeys
    private val envKeyMasks = maskedEnvKeys

    override fun getConfigurationStats(): ConfigurationStats {
        val config = ConfigProvider.getConfig()
        val configEntries = config.propertyNames
            .sorted()
            .map { key ->
                val value = if (shouldMaskConfigKey(key)) {
                    MASKED_VALUE
                } else {
                    runCatching { config.getOptionalValue(key, String::class.java).orElse("") }
                        .getOrElse { e ->
                            logger.warn(e) { "Failed to read config value for key '$key'" }
                            ERROR_VALUE
                        }
                }
                ConfigEntry(key, value)
            }
        val envEntries = System.getenv()
            .entries
            .sortedBy { it.key }
            .map { (key, value) ->
                ConfigEntry(key, if (envKeyMasks.contains(key)) MASKED_VALUE else value)
            }
        return ConfigurationStats(configEntries, envEntries)
    }

    private fun shouldMaskConfigKey(key: String): Boolean {
        if (configKeyMasks.contains(key)) return true
        val keyWithoutProfile = if (key.startsWith("%")) key.substringAfter(".") else key
        return configKeyMasks.contains(keyWithoutProfile)
    }

    companion object : KLogging()
}
