package no.nav.navansatt

import io.ktor.client.*
import io.ktor.client.engine.apache5.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.cache.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.engine.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.http.ssl.SSLContexts


@Serializable
data class ApiError(
    val message: String
)

fun main() {
    // Enable native access for Netty to suppress module warnings
    // Netty requires native library access for optimal performance
    System.setProperty("io.netty.tryReflectionSetAccessible", "true")

    val config = appConfigNais()
    val httpClient = HttpClient(Apache5) {
        engine {
            sslContext = SSLContexts.createSystemDefault()
            connectTimeout = 10_000  // 10 seconds for establishing connection
            socketTimeout = 30_000   // 30 seconds for socket operations
            connectionRequestTimeout = 30_000  // 30 seconds for getting connection from pool
            customizeClient {
                useSystemProperties()
            }
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 30_000  // 30 seconds for entire request
            connectTimeoutMillis = 10_000  // 10 seconds to establish connection
            socketTimeoutMillis = 30_000   // 30 seconds for socket operations
        }
        install(HttpCache)
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
            })
        }
        install(ClientCallLogging)
    }
    val graphService  = GraphClient(httpClient, config.azureClientId, config.azureClientSecret, config.azureTokenEndpoint, config.msGraphApiUrl)
    embeddedServer(io.ktor.server.netty.Netty, port = 7000) {
        mainModule(
            config = config,
            httpClient = httpClient,
            graphService = graphService
        )
    }.start(wait = true)
}
