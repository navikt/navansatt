package no.nav.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.ClientRequestException
import io.ktor.client.features.cache.HttpCache
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import org.apache.http.ssl.SSLContexts
import org.slf4j.LoggerFactory

@Serializable
data class TilgangResponse(
    val enheter: List<AxsysEnhet>
)

@Serializable
data class AxsysEnhet(
    val enhetId: String,
    val navn: String,
    val fagomrader: List<String>
)

@Serializable
data class Ident(
    val appIdent: String
)

class EnhetNotFoundError(message: String) : Exception(message)
class NAVAnsattNotFoundError(message: String) : Exception(message)

class AxsysClient(val axsysUrl: String) {
    companion object {
        private val LOG = LoggerFactory.getLogger(AxsysClient::class.java)
    }

    val httpClient = HttpClient(Apache) {
        engine {
            sslContext = SSLContexts.createSystemDefault()
            connectTimeout = 2
            customizeClient {
                useSystemProperties()
            }
        }
        install(HttpCache)
        install(JsonFeature) {
            serializer = KotlinxSerializer(
                kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }
            )
        }
    }.also {
        it.requestPipeline.intercept(HttpRequestPipeline.State) {
            context.header("Nav-Call-Id", "ignore")
            context.header("Nav-Consumer-Id", "navansatt")
            context.header("Accept", "application/json")
        }
    }

    suspend fun hentTilganger(ident: String): TilgangResponse {
        try {
            val response = httpClient.get<TilgangResponse> {
                url(axsysUrl + "/api/v1/tilgang/" + ident + "?inkluderAlleEnheter=false")
            }
            return response
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                throw NAVAnsattNotFoundError("Fant ikke NAV-ansatt med id $ident")
            } else {
                LOG.error("Kunne ikke hente tilganger for NAV-ansatt $ident", e)
                throw e
            }
        }
    }

    suspend fun hentAnsattIdenter(enhetId: String): List<Ident> {
        try {
            val response = httpClient.get<List<Ident>> {
                url(axsysUrl + "/api/v1/enhet/$enhetId/brukere")
            }
            return response
        } catch (e: ClientRequestException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                throw EnhetNotFoundError("Fant ikke NAV-enhet med id $enhetId")
            } else {
                LOG.error("Kunne ikke hente identer for NAV-enhet $enhetId", e)
                throw e
            }
        }
    }
}
