package no.nav.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
import java.util.UUID

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
        val httpResponse = httpClient.get("$axsysUrl/api/v1/tilgang/$ident?inkluderAlleEnheter=false") {
            axsysHeaders()
        }

        if (httpResponse.status.isSuccess()) {
            return httpResponse.body()
        } else if (httpResponse.status == HttpStatusCode.NotFound) {
            throw NAVAnsattNotFoundError("Fant ikke NAV-ansatt med id $ident")
        } else {
            val clientRequestException = ClientRequestException(httpResponse, "Kunne ikke hente tilganger for NAV-ansatt $ident")
            LOG.error("Kunne ikke hente tilganger for NAV-ansatt $ident", clientRequestException)
            throw clientRequestException
        }
    }

    suspend fun hentAnsattIdenter(enhetId: String): List<Ident> {
        val httpResponse = httpClient.get("$axsysUrl/api/v1/enhet/$enhetId/brukere") {
            axsysHeaders()
        }

        if (httpResponse.status.isSuccess()) {
            return httpResponse.body()
        } else if (httpResponse.status == HttpStatusCode.NotFound) {
            throw EnhetNotFoundError("Fant ikke NAV-enhet med id $enhetId")
        } else {
            val exception = ClientRequestException(httpResponse, "Kunne ikke hente identer for NAV-enhet $enhetId")
            LOG.error("Kunne ikke hente identer for NAV-enhet $enhetId", exception)
            throw exception
        }
    }
}
