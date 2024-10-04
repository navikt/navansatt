package no.nav.navansatt

import com.auth0.jwk.GuavaCachedJwkProvider
import com.auth0.jwk.UrlJwkProvider
import io.ktor.client.HttpClient
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.locations.KtorExperimentalLocationsAPI
import io.ktor.server.locations.Locations
import io.ktor.server.metrics.micrometer.MicrometerMetrics
import io.ktor.server.plugins.callid.CallId
import io.ktor.server.plugins.callid.callIdMdc
import io.ktor.server.plugins.callloging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.coroutines.runBlocking
import org.slf4j.event.Level
import java.net.URL
import java.util.Locale
import java.util.UUID

@OptIn(KtorExperimentalLocationsAPI::class)
fun Application.mainModule(
    config: ApplicationConfig,
    httpClient: HttpClient,
    activeDirectoryClient: ActiveDirectoryClient,
) {
    val axsysClient = AxsysClient(
        httpClient = httpClient,
        axsysUrl = config.axsysUrl,
    )
    val norg2Client = Norg2Client(
        httpClient = httpClient,
        norg2Url = config.norg2Url,
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
        callIdMdc("correlationId")
    }
    install(CallId) {
        retrieveFromHeader("correlationId")
        generate { UUID.randomUUID().toString() }
    }
    install(Locations)
    install(ContentNegotiation) {
        json()
    }
    install(StatusPages) {
        exception<Throwable> { call, cause ->
            call.application.log.error("Internal error", cause)
            call.response.status(HttpStatusCode.InternalServerError)
            call.respond(ApiError(message = "Internal server error (${cause::class.java.canonicalName})"))
        }

        status(HttpStatusCode.Unauthorized) { call, _ ->
            val authorization = call.request.header("Authorization")
            if (authorization != null) {
                if (authorization.lowercase(Locale.getDefault()).startsWith("bearer ")) {
                    val message = "Access Denied: with 'Bearer xxxxxx...' authentication. Expected valid JWT token."
                    call.application.log.warn(message)
                    call.response.status(HttpStatusCode.Unauthorized)
                    call.respond(ApiError(message = message))
                } else if (authorization.lowercase(Locale.getDefault()).startsWith("basic ")) {
                    val message = "Access Denied: Basic authentication is not supposed. Please use JWT authentication."
                    call.application.log.warn(message)
                    call.response.status(HttpStatusCode.Unauthorized)
                    call.respond(ApiError(message = message))
                } else {
                    val message = "Access Denied: Invalid Authentication header."
                    call.application.log.warn(message)
                    call.response.status(HttpStatusCode.Unauthorized)
                    call.respond(ApiError(message = message))
                }
            } else {
                val message = "Access Denied: no Authorization header was found in the request."
                call.application.log.warn(message)
                call.response.status(HttpStatusCode.Unauthorized)
                call.respond(ApiError(message = message))
            }
        }
    }
    install(Authentication) {
        jwt("azure") {
            verifier(
                GuavaCachedJwkProvider(UrlJwkProvider(URL(azureOidc.jwks_uri))),
                azureOidc.issuer,
            ) {
                config.azureClientId?.also { withAudience(it) }
            }
            validate { credential -> JWTPrincipal(credential.payload) }
        }

        jwt("sts") {
            verifier(
                GuavaCachedJwkProvider(UrlJwkProvider(URL(stsOidc.jwks_uri))),
                stsOidc.issuer,
            )
            validate { credential -> JWTPrincipal(credential.payload) }
        }
    }

    routing {
        routes(
            metricsRegistry = metricsRegistry,
            activeDirectoryClient = activeDirectoryClient,
            axsysClient = axsysClient,
            norg2Client = norg2Client,
        )
    }
}
