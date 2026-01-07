package no.nav.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import io.ktor.client.request.parameter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import org.slf4j.LoggerFactory
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Serializable
data class ClientCredentialsTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String,
    @SerialName("expires_in") val expiresIn: Int
)
@Serializable
data class GroupMembersResponse(
    val value: List<User>
)

@Serializable
data class User(
    val id: String = "",
    val onPremisesSamAccountName: String,
    val displayName: String = "",
    val givenName: String = "",
    val surname: String = "",
    val userPrincipalName: String = "",
    val streetAddress: String = ""
)

@Serializable
data class MemberOfResponse(
    val value: List<Group>
)
@Serializable
data class Group (
    val displayName: String,
    val securityEnabled: Boolean
)
class GraphClient(
    private val httpClient: HttpClient,
    private val azureClientId: String,
    private val azureClientSecret: String,
    private val azureTenant: String ) {

    private var cachedToken: String? = null
    private var tokenExpiryTime: Long = 0
    private val tokenExpiryBufferMillis: Long = 3000

    private val tokenMutex = Mutex()
    private var inFlightToken: kotlinx.coroutines.Deferred<String?>? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val log = LoggerFactory.getLogger(GraphClient::class.java)

    companion object {
        const val NAV_TEMA_PREFIX = "0000-GA-TEMA_"
        const val NAV_ENHET_PREFIX = "0000-GA-ENHET_"
        const val TOP_MAX = 999
    }

    suspend fun getAccessToken(tenantId: String, clientId: String, clientSecret: String): String? {
        val now = System.currentTimeMillis()

        val deferredToAwait = tokenMutex.withLock {
            val stillValid = cachedToken != null && now < (tokenExpiryTime - tokenExpiryBufferMillis)
            if (stillValid) return cachedToken

            inFlightToken?.let { return@withLock it }

            scope.async {
                try {
                    val response = httpClient.post("https://login.microsoftonline.com/$tenantId/oauth2/v2.0/token") {
                        contentType(ContentType.Application.FormUrlEncoded)
                        setBody(
                            Parameters.build {
                                append("grant_type", "client_credentials")
                                append("client_id", clientId)
                                append("client_secret", clientSecret)
                                append("scope", "https://graph.microsoft.com/.default")
                            }.formUrlEncode()
                        )
                    }

                    val body = response.bodyAsText()
                    val json = Json { ignoreUnknownKeys = true }
                    val tokenResponse = json.decodeFromString(ClientCredentialsTokenResponse.serializer(), body)

                    val expiry = System.currentTimeMillis() + tokenResponse.expiresIn * 1000L

                    tokenMutex.withLock {
                        cachedToken = tokenResponse.accessToken
                        tokenExpiryTime = expiry
                        inFlightToken = null
                    }

                    tokenResponse.accessToken
                } catch (e: Exception) {
                    log.error("Error fetching access token: ${e.message}")
                    tokenMutex.withLock { inFlightToken = null }
                    throw e
                }
            }.also { inFlightToken = it }
        }

        return deferredToAwait.await()
    }

    suspend fun getGroupIdByName(groupName: String, correlationId: String?): String? {
        try {
            val accessToken = getAccessToken(azureTenant, azureClientId, azureClientSecret)
            val response: HttpResponse = httpClient.get("https://graph.microsoft.com/v1.0/groups") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header("Nav-Call-Id", correlationId)
                parameter("\$filter", "displayName eq '$groupName'")
                parameter("\$select", "id,displayName,onPremisesSamAccountName")
            }
            val body = response.bodyAsText()
            val json = Json.parseToJsonElement(body).jsonObject
            val groups = json["value"]?.jsonArray
            return groups?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
        } catch (e: Exception) {
            log.error("Error fetching group by name $groupName: ${e.message}")
            throw e
        }
    }
    suspend fun getGroupMembersById(id: String, correlationId: String?): List<User> {
        try {
            val accessToken = getAccessToken(azureTenant, azureClientId, azureClientSecret)
            val response: HttpResponse = httpClient.get("https://graph.microsoft.com/v1.0/groups/$id/members") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header("Nav-Call-Id", correlationId)
                parameter("\$select", "id,onPremisesSamAccountName,displayName,givenName,surname,userPrincipalName,streetAddress")
                parameter("\$top", TOP_MAX)
            }
            val body = response.bodyAsText()
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val membersResponse = json.decodeFromString<GroupMembersResponse>(body)
            return membersResponse.value
        } catch (e: Exception) {
            log.error("Error fetching group members for group id $id: ${e.message}")
            throw e
        }
    }

    suspend fun getUserByNavIdent(navIdent: String, correlationId: String?): User? {
        try {
            val accessToken = getAccessToken(azureTenant, azureClientId, azureClientSecret)
            val response: HttpResponse = httpClient.get("https://graph.microsoft.com/v1.0/users") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header("ConsistencyLevel", "eventual")
                header("Nav-Call-Id", correlationId)
                parameter("\$count", "true")
                parameter("\$filter", "onPremisesSamAccountName eq '$navIdent'")
                parameter("\$select", "id,onPremisesSamAccountName,displayName,givenName,surname,userPrincipalName,streetAddress")
            }
            val body = response.bodyAsText()
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val usersResponse = json.decodeFromString<GroupMembersResponse>(body)
            return usersResponse.value.firstOrNull()
        } catch (e: Exception) {
            log.error("Error fetching user by navIdent $navIdent: ${e.message}")
            throw e
        }
    }

    suspend fun getGroupsForUser(entraIdUUID: String, correlationId: String?): List<String> {
        try {
            val accessToken = getAccessToken(azureTenant, azureClientId, azureClientSecret)
            val response: HttpResponse = httpClient.get("https://graph.microsoft.com/v1.0/users/$entraIdUUID/memberOf") {
                header(HttpHeaders.Authorization, "Bearer $accessToken")
                header("Nav-Call-Id", correlationId)
                parameter("\$select", "displayName,securityEnabled")
                parameter("\$top", TOP_MAX)
            }
            val body = response.bodyAsText()
            val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
            val groupsResponse = json.decodeFromString<MemberOfResponse>(body)
            return groupsResponse.value.filter { it.securityEnabled }.map { it.displayName }
        } catch (e: Exception) {
            log.error("Error fetching groups for user $entraIdUUID: ${e.message}")
            throw e
        }
    }

    suspend fun getTemaForUser(navIdent: String, correlationId: String?): List<String> {
        val user = getUserByNavIdent(navIdent, correlationId) ?: return emptyList()
        val allGroups = getGroupsForUser(user.id, correlationId)
        return allGroups.filter { it.startsWith(NAV_TEMA_PREFIX) }
            .map { groupNameToTema(it) }
    }

    suspend fun getEnheterForUser(navIdent: String, correlationId: String?): List<String> {
        val user = getUserByNavIdent(navIdent, correlationId) ?: return emptyList()
        val allGroups = getGroupsForUser(user.id, correlationId)
        return allGroups.filter { it.startsWith(NAV_ENHET_PREFIX) }
            .map { groupNameToEnhetId(it)}
    }

    suspend fun getUsersInGroup(groupName: String, correlationId: String?): List<User>? {
        val groupId = getGroupIdByName(groupName, correlationId) ?: return null
        return getGroupMembersById(groupId, correlationId)
    }

    fun enhetIdToGroupName(enhetId: String): String {
        return "$NAV_ENHET_PREFIX$enhetId"
    }
    fun groupNameToEnhetId(groupName: String): String {
        return groupName.substringAfter(NAV_ENHET_PREFIX)
    }
    fun groupNameToTema(groupName: String): String {
        return groupName.substringAfter(NAV_TEMA_PREFIX)
    }
}