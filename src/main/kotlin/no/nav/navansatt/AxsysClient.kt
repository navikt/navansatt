package no.nav.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.features.ResponseException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
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

fun HttpRequestBuilder.axsysHeaders() {
    header("Nav-Call-Id", "ignore")
    header("Nav-Consumer-Id", "navansatt")
    header("Accept", "application/json")
}

class AxsysClient(val httpClient: HttpClient, val axsysUrl: String) {
    companion object {
        private val LOG = LoggerFactory.getLogger(AxsysClient::class.java)
    }

    suspend fun hentTilganger(ident: String): TilgangResponse {
        try {
            return httpClient.get {
                url(axsysUrl + "/api/v1/tilgang/" + ident + "?inkluderAlleEnheter=false")
                axsysHeaders()
            }
        } catch (e: ResponseException) {
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
            return httpClient.get {
                url(axsysUrl + "/api/v1/enhet/$enhetId/brukere")
                axsysHeaders()
            }
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.NotFound) {
                throw EnhetNotFoundError("Fant ikke NAV-enhet med id $enhetId")
            } else {
                LOG.error("Kunne ikke hente identer for NAV-enhet $enhetId", e)
                throw e
            }
        }
    }
}
