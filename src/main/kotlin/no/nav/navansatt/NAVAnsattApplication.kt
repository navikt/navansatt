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
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.ktor.server.engine.embeddedServer
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
import java.io.File
import java.lang.RuntimeException
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

data class ApplicationConfig(
    val adUrl: String,
    val adBase: String,
    val adUsername: String,
    val adPassword: String,
    val axsysUrl: String
)

fun appConfigLocal() = ApplicationConfig(
    adUrl = "ldap://localhost:8389",
    adBase = "DC=test,DC=local",
    adUsername = File("secrets/ldap/username").readText(),
    adPassword = File("secrets/ldap/password").readText(),
    axsysUrl = "https://axsys.dev.adeo.no"
)

fun appConfigNais() = ApplicationConfig(
    adUrl = System.getenv("LDAP_URL") ?: throw RuntimeException("Missing LDAP_URL environment variable."),
    adBase = System.getenv("LDAP_BASE") ?: throw RuntimeException("Missing LDAP_BASE environment variable."),
    adUsername = File("/secrets/ldap/username").readText(),
    adPassword = File("/secrets/ldap/password").readText(),
    axsysUrl = System.getenv("AXSYS_URL") ?: throw RuntimeException("Missing AXSYS_URL environment variable."),
)

@KtorExperimentalLocationsAPI
fun main() {
    val config = if (System.getenv("NAIS_APP_NAME") != null) appConfigNais() else appConfigLocal()

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
        val appMicrometerRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        install(MicrometerMetrics) {
            registry = appMicrometerRegistry
        }
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
            simpleGet("/internal/metrics") {
                call.respond(appMicrometerRegistry.scrape())
            }
            simpleGet("/internal/isalive") {
                call.respond("OK")
            }
            simpleGet("/internal/isready") {
                call.respond("OK")
            }

            @Location("/navansatt/{ident}")
            data class GetNAVAnsattLocation(val ident: String)
            get<GetNAVAnsattLocation> { location ->
                throw RuntimeException("TODO: dette endpointet må ha autentisering")
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
                throw RuntimeException("TODO: dette endpointet må ha autentisering")
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
