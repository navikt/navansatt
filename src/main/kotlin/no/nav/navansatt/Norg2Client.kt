package no.nav.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.http.URLBuilder
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory

@Serializable
data class Norg2Enhet(
    val enhetNr: String,
    val navn: String,
    val orgNivaa: String
)

class Norg2Client(val httpClient: HttpClient, val norg2Url: String) {
    companion object {
        private val LOG = LoggerFactory.getLogger(Norg2Client::class.java)
    }

    suspend fun hentEnheter(nummer: List<String>): List<Norg2Enhet> {
        val response = httpClient.get<List<Norg2Enhet>> {
            url(
                URLBuilder(norg2Url + "/api/v1/enhet").apply {
                    parameters.appendAll("enhetsnummerListe", nummer)
                }.buildString()
            )
        }
        return response
    }
}
