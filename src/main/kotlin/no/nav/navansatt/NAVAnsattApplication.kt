package no.nav.navansatt

import com.auth0.jwk.UrlJwkProvider
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.HttpStatusCode
import io.ktor.locations.KtorExperimentalLocationsAPI
import io.ktor.locations.Locations
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.request.header
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.ktor.server.engine.embeddedServer
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import java.net.URL

@Serializable
data class ApiError(
    val message: String
)

@KtorExperimentalLocationsAPI
fun main() {
    val config = if (System.getenv("NAIS_APP_NAME") != null) appConfigNais() else appConfigLocal()

    val activeDirectoryClient = ActiveDirectoryClient(
        url = config.adUrl,
        base = config.adBase,
        username = config.adUsername,
        password = config.adPassword
    )
    val axsysClient = AxsysClient(
        axsysUrl = config.axsysUrl
    )
    val norg2Client = Norg2Client(
        norg2Url = config.norg2Url
    )
    val metricsRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    val azureOidc = runBlocking { discoverOidcMetadata(config.azureWellKnown) }
    val openamOidc = runBlocking { discoverOidcMetadata(config.openamWellKnown) }

    embeddedServer(io.ktor.server.netty.Netty, port = 7000) {
        install(MicrometerMetrics) {
            registry = metricsRegistry
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

            status(HttpStatusCode.Unauthorized) {
                val authorization = call.request.header("Authorization")
                if (authorization != null) {
                    if (authorization.toLowerCase().startsWith("Bearer ")) {
                        val message = "Access Denied: with 'Bearer xxxxxx...' authentication. Expected valid JWT token."
                        log.warn(message)
                        call.respond(ApiError(message = message))
                    } else if (authorization.toLowerCase().startsWith("Basic ")) {
                        val message = "Access Denied: Basic authentication is not supposed. Please use JWT authentication."
                        log.warn(message)
                        call.respond(ApiError(message = message))
                    }
                } else {
                    val message = "Access Denied: no Authorization header was found in the request."
                    log.warn(message)
                    call.respond(ApiError(message = message))
                }
            }
        }
        install(Authentication) {
            jwt("azure") {
                verifier(
                    UrlJwkProvider(URL(azureOidc.jwks_uri)),
                    azureOidc.issuer
                )
                validate { credential -> JWTPrincipal(credential.payload) }
            }

            jwt("openam") {
                verifier(
                    UrlJwkProvider(URL(openamOidc.jwks_uri)),
                    openamOidc.issuer
                )
                validate { credential -> JWTPrincipal(credential.payload) }
            }
        }

        routing {
            AppRoutes(
                metricsRegistry = metricsRegistry,
                activeDirectoryClient = activeDirectoryClient,
                axsysClient = axsysClient,
                norg2Client = norg2Client
            )
        }
    }.start(wait = true)
}
