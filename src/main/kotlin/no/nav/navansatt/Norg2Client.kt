package no.nav.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import kotlinx.serialization.Serializable

@Serializable
data class Norg2Enhet(
    val enhetNr: String,
    val navn: String,
    val orgNivaa: String
)

class Norg2Client(val httpClient: HttpClient, val norg2Url: String) {

    suspend fun hentEnheter(nummer: List<String>): List<Norg2Enhet> {
        val response = httpClient.get("$norg2Url/api/v1/enhet") {
            nummer.forEach { parameter("enhetsnummerListe", it) }
        }
        return response.body()
    }
}
