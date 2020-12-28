package no.nav.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.cache.HttpCache
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.client.request.url
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.http.ssl.SSLContexts
import org.slf4j.LoggerFactory

@Serializable
data class OIDCMetadata(
    val issuer: String,
    val jwks_uri: String
)

private val LOG = LoggerFactory.getLogger(OIDCMetadata::class.java)

suspend fun oidcDiscovery(wellknownUrl: String): OIDCMetadata {
    val httpClient = HttpClient(Apache) {
        engine {
            sslContext = SSLContexts.createSystemDefault()
            connectTimeout = 2
        }
        install(HttpCache)
        install(JsonFeature) {
            serializer = KotlinxSerializer(
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }
    }

    try {
        val meta = httpClient.get<OIDCMetadata> {
            url(wellknownUrl)
        }
        return meta
    } catch (err: Exception) {
        LOG.error("Could not discover OIDC metadata at $wellknownUrl", err)
        throw err
    }
}
