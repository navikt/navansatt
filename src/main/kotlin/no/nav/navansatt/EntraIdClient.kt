package no.nav.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.http.formUrlEncode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ClientCredentialsTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int
)

class EntraIdClient(
    private val clientId: String,
    private val clientSecret: String,
    private val endpoint: String,
    private val httpClient: HttpClient
) {

    private var cachedToken: String? = null
    private var tokenExpiryTime: Long = 0
    private val tokenExpiryBufferMillis: Long = 3000 // 3 seconds buffer

    suspend fun retrieveClientCredentialsToken(scope: List<String>): String? {
        val currentTime = System.currentTimeMillis()

        if (cachedToken != null && currentTime < (tokenExpiryTime-tokenExpiryBufferMillis)) {
            return cachedToken
        }

        val response: HttpResponse = httpClient.post(endpoint) {
            contentType(ContentType.Application.FormUrlEncoded)
            setBody(
                Parameters.build {
                    append("grant_type", "client_credentials")
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("scope", scope.joinToString(" "))
                }.formUrlEncode()
            )
        }
        val body = response.bodyAsText()
        val json = Json { ignoreUnknownKeys = true }
        val tokenResponse = json.decodeFromString<ClientCredentialsTokenResponse>(body)

        cachedToken = tokenResponse.accessToken
        tokenExpiryTime = currentTime + (tokenResponse.expiresIn * 1000L)

        return cachedToken
    }
}
