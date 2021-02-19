package no.nav.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.url
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class OIDCMetadata(
    val issuer: String,
    val jwks_uri: String
)

private val LOG = LoggerFactory.getLogger(OIDCMetadata::class.java)

suspend fun discoverOidcMetadata(httpClient: HttpClient, wellknownUrl: String): OIDCMetadata {
    try {
        val meta = httpClient.get<OIDCMetadata> {
            url(wellknownUrl)
        }
        LOG.info("Discovered issuer ${meta.issuer} with JWKS URI ${meta.jwks_uri} (from OIDC endpoint $wellknownUrl)")
        return meta
    } catch (err: Exception) {
        LOG.error("Could not discover OIDC metadata at $wellknownUrl", err)
        throw err
    }
}
