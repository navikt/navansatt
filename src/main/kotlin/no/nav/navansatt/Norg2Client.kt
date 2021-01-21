package no.nav.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.cache.HttpCache
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.http.URLBuilder
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.http.ssl.SSLContexts
import org.slf4j.LoggerFactory

@Serializable
data class Norg2Enhet(
    val enhetNr: String,
    val navn: String,
    val orgNivaa: String
)

class Norg2Client(val norg2Url: String) {
    companion object {
        private val LOG = LoggerFactory.getLogger(Norg2Client::class.java)
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
                Json {
                    ignoreUnknownKeys = true
                }
            )
        }
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
