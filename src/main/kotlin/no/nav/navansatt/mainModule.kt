package no.nav.navansatt

import com.auth0.jwk.UrlJwkProvider
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.application.log
import io.ktor.auth.Authentication
import io.ktor.auth.jwt.JWTPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.client.HttpClient
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.locations.Locations
import io.ktor.metrics.micrometer.MicrometerMetrics
import io.ktor.request.*
import io.ktor.response.respond
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import org.slf4j.event.Level
import java.net.URL
import java.util.*

fun Application.mainModule(
    config: ApplicationConfig,
    httpClient: HttpClient,
    activeDirectoryClient: ActiveDirectoryClient
) {
    val axsysClient = AxsysClient(
        httpClient = httpClient,
        axsysUrl = config.axsysUrl
    )
    val norg2Client = Norg2Client(
        httpClient = httpClient,
        norg2Url = config.norg2Url
    )

    val azureOidc = runBlocking {
        discoverOidcMetadata(httpClient = httpClient, wellknownUrl = config.azureWellKnown)
    }
    val stsOidc = runBlocking {
        discoverOidcMetadata(httpClient = httpClient, wellknownUrl = config.stsWellKnown)
    }

    val metricsRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
    install(MicrometerMetrics) {
        registry = metricsRegistry
    }
    install(CallLogging) {
        level = Level.INFO
        filter { call -> !call.request.path().matches(Regex(".*/isready|.*/isalive|.*/metrics")) }
        callIdMdc("X-Correlation-ID")
    }
    install(CallId) {
        retrieveFromHeader("X-Correlation-ID")
        generate { UUID.randomUUID().toString() }
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
                if (authorization.toLowerCase().startsWith("bearer ")) {
                    val message = "Access Denied: with 'Bearer xxxxxx...' authentication. Expected valid JWT token."
                    log.warn(message)
                    call.response.status(HttpStatusCode.Unauthorized)
                    call.respond(ApiError(message = message))
                } else if (authorization.toLowerCase().startsWith("basic ")) {
                    val message = "Access Denied: Basic authentication is not supposed. Please use JWT authentication."
                    log.warn(message)
                    call.response.status(HttpStatusCode.Unauthorized)
                    call.respond(ApiError(message = message))
                } else {
                    val message = "Access Denied: Invalid Authentication header."
                    log.warn(message)
                    call.response.status(HttpStatusCode.Unauthorized)
                    call.respond(ApiError(message = message))
                }
            } else {
                val message = "Access Denied: no Authorization header was found in the request."
                log.warn(message)
                call.response.status(HttpStatusCode.Unauthorized)
                call.respond(ApiError(message = message))
            }
        }
    }
    install(Authentication) {
        jwt("azure") {
            verifier(
                UrlJwkProvider(URL(azureOidc.jwks_uri)),
                azureOidc.issuer
            ) {
                config.azureClientId?.also { withAudience(it) }
            }
            validate { credential -> JWTPrincipal(credential.payload) }
        }

        jwt("sts") {
            verifier(
                UrlJwkProvider(URL(stsOidc.jwks_uri)),
                stsOidc.issuer
            )
            validate { credential -> JWTPrincipal(credential.payload) }
        }
    }

    routing {
        Routes(
            metricsRegistry = metricsRegistry,
            activeDirectoryClient = activeDirectoryClient,
            axsysClient = axsysClient,
            norg2Client = norg2Client
        )
    }
}
