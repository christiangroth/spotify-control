package de.chrgroth.spotify.control.adapter.`in`.web

import de.chrgroth.spotify.control.domain.port.`in`.RuntimeConfigPort
import de.chrgroth.spotify.control.domain.port.out.ConfigurationInfoPort
import io.quarkus.qute.Location
import io.quarkus.qute.Template
import io.quarkus.qute.TemplateInstance
import io.quarkus.security.Authenticated
import jakarta.enterprise.context.ApplicationScoped
import jakarta.inject.Inject
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import jakarta.ws.rs.core.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

@Path("/config")
@ApplicationScoped
@Suppress("Unused")
class ConfigResource {

    @Inject
    @Location("config.html")
    private lateinit var configTemplate: Template

    @Inject
    private lateinit var configurationInfo: ConfigurationInfoPort

    @Inject
    private lateinit var runtimeConfig: RuntimeConfigPort

    @GET
    @Authenticated
    @Produces(MediaType.TEXT_HTML)
    fun config(): TemplateInstance = runBlocking {
        val statsAsync = async(Dispatchers.IO) { configurationInfo.getConfigurationStats() }
        val runtimeConfigAsync = async(Dispatchers.IO) { runtimeConfig.getRuntimeConfig() }
        configTemplate
            .data("stats", statsAsync.await())
            .data("runtimeConfig", runtimeConfigAsync.await())
    }
}
