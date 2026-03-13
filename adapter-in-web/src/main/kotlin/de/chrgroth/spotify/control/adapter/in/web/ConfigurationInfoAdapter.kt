package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.model.ConfigEntry
import de.chrgroth.spotify.control.domain.model.ConfigurationStats
import de.chrgroth.spotify.control.domain.port.out.ConfigurationInfoPort
import jakarta.enterprise.context.ApplicationScoped
import org.eclipse.microprofile.config.ConfigProvider
import org.eclipse.microprofile.config.inject.ConfigProperty

private const val MASKED_VALUE = "***"

@ApplicationScoped
@Suppress("Unused")
class ConfigurationInfoAdapter(
    @ConfigProperty(name = "app.health.masked-config-keys")
    maskedConfigKeysConfig: List<String>,
    @ConfigProperty(name = "app.health.masked-env-keys")
    maskedEnvKeysConfig: List<String>,
) : ConfigurationInfoPort {

    private val maskedConfigKeys = maskedConfigKeysConfig
    private val maskedEnvKeys = maskedEnvKeysConfig

    override fun getConfigurationStats(): ConfigurationStats {
        val config = ConfigProvider.getConfig()
        val configEntries = config.propertyNames
            .sorted()
            .map { key ->
                val value = if (shouldMaskConfigKey(key)) {
                    MASKED_VALUE
                } else {
                    runCatching { config.getOptionalValue(key, String::class.java).orElse("") }.getOrElse { MASKED_VALUE }
                }
                ConfigEntry(key, value)
            }
        val envEntries = System.getenv()
            .entries
            .sortedBy { it.key }
            .map { (key, value) ->
                ConfigEntry(key, if (maskedEnvKeys.contains(key)) MASKED_VALUE else value)
            }
        return ConfigurationStats(configEntries, envEntries)
    }

    private fun shouldMaskConfigKey(key: String): Boolean {
        if (maskedConfigKeys.contains(key)) return true
        val strippedKey = if (key.startsWith("%")) key.substringAfter(".") else key
        return maskedConfigKeys.contains(strippedKey)
    }
}
