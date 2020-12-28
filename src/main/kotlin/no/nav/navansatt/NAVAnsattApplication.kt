package no.nav.navansatt

import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Location
import io.ktor.locations.Locations
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import java.io.File
import io.ktor.routing.get as simpleGet

@Serializable
data class UserResult(
    val ident: String,
    val displayName: String,
    val firstName: String,
    val lastName: String,
    val email: String
)

@Serializable
data class ApiError(
    val message: String
)

class ApplicationConfig(
    val adUrl: String = System.getenv("LDAP_URL") ?: "ldap://localhost:8389",
    val adBase: String = System.getenv("LDAP_BASE") ?: "DC=test,DC=local",
    val adUsername: String = System.getenv("LDAP_USERNAME") ?: "",
    val adPassword: String = System.getenv("LDAP_PASSWORD") ?: "",
    val axsysUrl: String = System.getenv("AXSYS_URL") ?: "https://axsys.dev.adeo.no"
)

@KtorExperimentalLocationsAPI
fun main() {
    val config = ApplicationConfig()

    val truststorePath: String? = System.getenv("NAV_TRUSTSTORE_PATH")
    truststorePath?.let {
        System.setProperty("javax.net.ssl.trustStore", it)
        System.setProperty("javax.net.ssl.trustStorePassword", System.getenv("NAV_TRUSTSTORE_PASSWORD") ?: "")
    } ?: run {
        System.setProperty("javax.net.ssl.trustStore", "secrets/truststore/truststore.jts")
        System.setProperty("javax.net.ssl.trustStorePassword", File("secrets/truststore/password").readText())
    }

    val ad = ActiveDirectoryClient(
        url = config.adUrl,
        base = config.adBase,
        username = config.adUsername,
        password = config.adPassword
    )
    val ax = AxsysClient(
        axsysUrl = config.axsysUrl
    )

    embeddedServer(io.ktor.server.netty.Netty, port = 7000) {
        install(Locations)
        install(ContentNegotiation) {
            json()
        }
        install(StatusPages) {
            exception<Throwable> { cause ->
                log.error("Internal error", cause)
                call.response.status(HttpStatusCode.InternalServerError)
                call.respond(ApiError(message = "Internal server error (${cause::class.java.canonicalName})"))
            }
        }

        routing {
            simpleGet("/isalive") {
                call.respond("OK")
            }
            simpleGet("/isready") {
                call.respond("OK")
            }

            @Location("/navansatt/{ident}")
            data class GetNAVAnsattLocation(val ident: String)
            get<GetNAVAnsattLocation> { location ->
                val result = ad.getUser(location.ident)
                result?.let {
                    call.respond(
                        UserResult(
                            ident = location.ident,
                            displayName = it.displayName,
                            firstName = it.firstName,
                            lastName = it.lastName,
                            email = it.email
                        )
                    )
                } ?: run {
                    call.response.status(HttpStatusCode.NotFound)
                    call.respond(
                        ApiError(
                            message = "User not found"
                        )
                    )
                }
            }
            @Location("/enhet/{enhetId}/navansatte")
            data class GetEnhetAnsatte(val enhetId: String)
            get<GetEnhetAnsatte> { location ->
                try {
                    val result = ax.hentAnsattIdenter(location.enhetId)

                    val deferreds = result.map { ansatt ->
                        async {
                            ad.getUser(ansatt.appIdent)
                        }
                    }
                    val userData: List<UserResult> = deferreds.awaitAll().filterNotNull().map {
                        UserResult(
                            ident = it.ident,
                            displayName = it.displayName,
                            firstName = it.firstName,
                            lastName = it.lastName,
                            email = it.email
                        )
                    }
                    call.respond(userData)
                } catch (err: EnhetNotFoundError) {
                    call.response.status(HttpStatusCode.NotFound)
                    call.respond(
                        ApiError(
                            message = "Fant ikke NAV-enhet med id ${location.enhetId}"
                        )
                    )
                }
            }
        }
    }.apply { start(wait = true) }
}
