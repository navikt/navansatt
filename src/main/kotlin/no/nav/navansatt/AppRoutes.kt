package no.nav.navansatt

import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.serialization.Serializable
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
data class NAVEnhetResult(
    val id: String,
    val navn: String
)

fun Routing.AppRoutes(
    metricsRegistry: PrometheusMeterRegistry,
    activeDirectoryClient: ActiveDirectoryClient,
    axsysClient: AxsysClient
) {
    simpleGet("/internal/metrics") {
        call.respond(metricsRegistry.scrape())
    }
    simpleGet("/internal/isalive") {
        call.respond("OK")
    }
    simpleGet("/internal/isready") {
        call.respond("OK")
    }

    authenticate("azure", "openam") {
        @Location("/navansatt/{ident}")
        data class GetNAVAnsattLocation(val ident: String)
        get<GetNAVAnsattLocation> { location ->
            val result = activeDirectoryClient.getUser(location.ident)
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

        @Location("/navansatt/{ident}/fagomrader")
        data class GetNAVAnsattFagomraderLocation(val ident: String)
        get<GetNAVAnsattFagomraderLocation> { location ->
            val result = axsysClient.hentTilganger(location.ident)
            val response: List<String> = result.enheter.flatMap { it.fagomrader }.distinct()
            call.respond(response)
        }

        @Location("/navansatt/{ident}/enheter")
        data class GetNAVAnsattEnheterLocation(val ident: String)
        get<GetNAVAnsattEnheterLocation> { location ->
            val result = axsysClient.hentTilganger(location.ident)
            call.respond(
                result.enheter.map {
                    NAVEnhetResult(
                        id = it.enhetId,
                        navn = it.navn
                    )
                }
            )
        }

        @Location("/enhet/{enhetId}/navansatte")
        data class GetEnhetAnsatte(val enhetId: String)
        get<GetEnhetAnsatte> { location ->
            try {
                val result = axsysClient.hentAnsattIdenter(location.enhetId)

                val deferreds = result.map { ansatt ->
                    async {
                        activeDirectoryClient.getUser(ansatt.appIdent)
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
}
