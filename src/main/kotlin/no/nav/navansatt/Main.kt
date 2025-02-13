package no.nav.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.plugins.cache.HttpCache
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.engine.embeddedServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.http.ssl.SSLContexts

@Serializable
data class ApiError(
    val message: String
)

fun main() {
    val config = if (System.getenv("NAIS_APP_NAME") != null) appConfigNais() else appConfigLocal()
    val activeDirectoryClient = ActiveDirectoryClient(
        url = config.adUrl,
        base = config.adBase,
        username = config.adUsername,
        password = config.adPassword
    )
    val httpClient = HttpClient(Apache) {
        engine {
            sslContext = SSLContexts.createSystemDefault()
            connectTimeout = 2
            customizeClient {
                useSystemProperties()
            }
        }
        install(HttpCache)
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        install(ClientCallLogging)
    }
    embeddedServer(io.ktor.server.netty.Netty, port = 7000) {
        mainModule(
            config = config,
            httpClient = httpClient,
            activeDirectoryClient = activeDirectoryClient
        )
    }.start(wait = true)
}
