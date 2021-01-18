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
data class NavAnsattResult(
    val ident: String,
    val navn: String,
    val fornavn: String,
    val etternavn: String,
    val epost: String
)

@Serializable
data class NAVEnhetResult(
    val id: String,
    val navn: String
)

@Serializable
data class Fagomrade(
    val kode: String
)

fun Routing.AppRoutes(
    metricsRegistry: PrometheusMeterRegistry,
    activeDirectoryClient: ActiveDirectoryClient,
    axsysClient: AxsysClient
) {
    simpleGet("/ping") {
        call.respond("OK")
    }
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
        simpleGet("/ping-authenticated") {
            call.respond("OK")
        }

        @Location("/navansatt/{ident}")
        data class GetNAVAnsattLocation(val ident: String)
        get<GetNAVAnsattLocation> { location ->
            val result = activeDirectoryClient.getUser(location.ident)
            result?.let {
                call.respond(
                    NavAnsattResult(
                        ident = location.ident,
                        navn = it.displayName,
                        fornavn = it.firstName,
                        etternavn = it.lastName,
                        epost = it.email
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
            val response: List<Fagomrade> = result.enheter.flatMap { it.fagomrader }.distinct().map {
                Fagomrade(kode = it)
            }
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
                val navAnsattData: List<NavAnsattResult> = deferreds.awaitAll().filterNotNull().map {
                    NavAnsattResult(
                        ident = it.ident,
                        navn = it.displayName,
                        fornavn = it.firstName,
                        etternavn = it.lastName,
                        epost = it.email
                    )
                }
                call.respond(navAnsattData)
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
