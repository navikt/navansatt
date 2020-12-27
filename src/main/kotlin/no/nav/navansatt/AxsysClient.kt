package no.nav.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.cache.HttpCache
import io.ktor.client.features.json.JsonFeature
import io.ktor.client.features.json.serializer.KotlinxSerializer
import io.ktor.client.request.HttpRequestPipeline
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import kotlinx.serialization.Serializable
import org.apache.http.ssl.SSLContexts

@Serializable
data class TilgangResponse(
    val enheter: List<Enhet>
)

@Serializable
data class Enhet(
    val enhetId: String,
    val navn: String,
    val fagomrader: List<String>
)

@Serializable
data class Ident(
    val appIdent: String
)

class AxsysClient(val axsysUrl: String) {
    val httpClient = HttpClient(Apache) {
        engine {
            sslContext = SSLContexts.createSystemDefault()
            connectTimeout = 2
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
        val response = httpClient.get<TilgangResponse> {
            url(axsysUrl + "/api/v1/tilgang/" + ident + "?inkluderAlleEnheter=false")
        }
        return response
    }

    suspend fun hentAnsattIdenter(enhetId: String): List<Ident> {
        val response = httpClient.get<List<Ident>> {
            url(axsysUrl + "/api/v1/enhet/$enhetId/brukere")
        }
        return response
    }
}
