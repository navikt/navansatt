package no.nav.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

import org.slf4j.LoggerFactory

@Serializable
data class Enhet(
    val enhetnummer: String,
    val navn: String
)

@Serializable
data class Ident(
    val navIdent: String,
    val visningNavn: String,
    val fornavn: String,
    val etternavn: String
)

@Serializable
data class AnsattIGruppe(
    val navIdent: String,
    val visningNavn: String,
    val fornavn: String,
    val etternavn: String
)

@Serializable
data class NavAnsatt(
    val navIdent: String,
    val visningNavn: String,
    val fornavn: String,
    val etternavn: String,
    val epost: String = "",
    val enhet: Enhet = Enhet("", ""),
)

@Serializable
data class Rolle(
    val rolle: String
)

class EnhetNotFoundError(message: String) : Exception(message)
class NAVAnsattNotFoundError(message: String) : Exception(message)
class GroupNotFoundError(message: String) : Exception(message)

fun HttpRequestBuilder.endtraproxyHeaders(token: String?, callId: String?) {
    header("Nav-Call-Id", callId ?: "unknown")
    header("Nav-Consumer-Id", "navansatt")
    header("Accept", "application/json")
    header("Authorization", "Bearer " + token)
}

class EntraproxyClient(val httpClient: HttpClient, val entraproxyUrl: String, val entraIdClient: EntraIdClient, val entraproxyScope: String) {
    companion object {
        private val LOG = LoggerFactory.getLogger(EntraproxyClient::class.java)
    }

    suspend fun hentTema(ident: String, correlationId: String? = null): List<String> {
        val httpResponse = httpClient.get("$entraproxyUrl/api/v1/tema/ansatt/$ident") {
            endtraproxyHeaders(entraIdClient.retrieveClientCredentialsToken(listOf(entraproxyScope)), correlationId)
        }

        if (httpResponse.status.isSuccess()) {
            return httpResponse.body<List<String>>()
        } else if (httpResponse.status == HttpStatusCode.NotFound) {
            throw NAVAnsattNotFoundError("Fant ikke NAV-ansatt med id $ident")
        } else {
            val clientRequestException = ClientRequestException(httpResponse, "Kunne ikke hente tema for NAV-ansatt $ident")
            LOG.error("Kunne ikke hente tilganger for NAV-ansatt $ident", clientRequestException)
            throw clientRequestException
        }
    }

    suspend fun hentEnheter(ident: String, correlationId: String? = null): List<Enhet> {
        val httpResponse = httpClient.get("$entraproxyUrl/api/v1/enhet/ansatt/$ident") {
            endtraproxyHeaders(entraIdClient.retrieveClientCredentialsToken(listOf(entraproxyScope)), correlationId)
        }

        if (httpResponse.status.isSuccess()) {
            return httpResponse.body<List<Enhet>>()
        } else if (httpResponse.status == HttpStatusCode.NotFound) {
            throw NAVAnsattNotFoundError("Fant ikke NAV-ansatt med id $ident")
        } else {
            val clientRequestException = ClientRequestException(httpResponse, "Kunne ikke hente enheter for NAV-ansatt $ident")
            LOG.error("Kunne ikke hente tilganger for NAV-ansatt $ident", clientRequestException)
            throw clientRequestException
        }
    }

    suspend fun hentAnsattIdenter(enhetId: String, correlationId: String? = null): List<Ident> {
        val httpResponse = httpClient.get("$entraproxyUrl/api/v1/enhet/$enhetId") {
            endtraproxyHeaders(entraIdClient.retrieveClientCredentialsToken(listOf(entraproxyScope)), correlationId)
        }

        if (httpResponse.status.isSuccess()) {
            return httpResponse.body<List<Ident>>()
        } else if (httpResponse.status == HttpStatusCode.NotFound) {
            throw EnhetNotFoundError("Fant ikke NAV-enhet med id $enhetId")
        } else {
            val exception = ClientRequestException(httpResponse, "Kunne ikke hente identer for NAV-enhet $enhetId")
            LOG.error("Kunne ikke hente identer for NAV-enhet $enhetId", exception)
            throw exception
        }
    }

    suspend fun hentAnsatteIGruppe(gruppe: String, correlationId: String? = null): List<AnsattIGruppe> {
        val httpResponse = httpClient.get("$entraproxyUrl/api/v1/gruppe/medlemmer?gruppeNavn=$gruppe") {
            endtraproxyHeaders(entraIdClient.retrieveClientCredentialsToken(listOf(entraproxyScope)), correlationId)
        }

        if (httpResponse.status.isSuccess()) {
            return httpResponse.body<List<AnsattIGruppe>>()
        } else if (httpResponse.status == HttpStatusCode.NotFound) {
            throw GroupNotFoundError("Fant ikke $gruppe")
        } else {
            val exception = ClientRequestException(httpResponse, "Kunne ikke hente identer i $gruppe")
            LOG.error("Kunne ikke hente identer i $gruppe", exception)
            throw exception
        }
    }

    suspend fun hentNavAnsatt(ident: String, correlationId: String? = null): NavAnsatt? {
        val httpResponse = httpClient.get("$entraproxyUrl/api/v1/ansatt/$ident") {
            endtraproxyHeaders(entraIdClient.retrieveClientCredentialsToken(listOf(entraproxyScope)), correlationId)
        }

        if (httpResponse.status.isSuccess()) {
            return httpResponse.body()
        } else if (httpResponse.status == HttpStatusCode.NotFound) {
            throw NAVAnsattNotFoundError("Fant ikke $ident")
        } else {
            val exception = ClientRequestException(httpResponse, "Kunne ikke hente data knyttet til ident $ident")
            LOG.error("Kunne ikke hente data knyttet til ident $ident", exception)
            throw exception
        }
    }
    suspend fun hentGrupperForAnsatt(ident: String, correlationId: String? = null): List<String> {
        val httpResponse = httpClient.get("$entraproxyUrl/api/v1/ansatt/tilganger/$ident") {
            endtraproxyHeaders(entraIdClient.retrieveClientCredentialsToken(listOf(entraproxyScope)), correlationId)
        }

        if (httpResponse.status.isSuccess()) {
            return httpResponse.body<List<Rolle>>().map { it.rolle }
        } else if (httpResponse.status == HttpStatusCode.NotFound) {
            throw NAVAnsattNotFoundError("Fant ikke $ident")
        } else {
            val exception = ClientRequestException(httpResponse, "Kunne ikke hente data knyttet til ident $ident")
            LOG.error("Kunne ikke hente data knyttet til ident $ident", exception)
            throw exception
        }
    }

}
