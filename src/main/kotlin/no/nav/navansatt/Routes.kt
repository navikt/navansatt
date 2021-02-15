package no.nav.navansatt

import io.ktor.application.call
import io.ktor.auth.authenticate
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.micrometer.prometheus.PrometheusMeterRegistry
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
    val navn: String,
    val nivaa: String
)

@Serializable
data class Fagomrade(
    val kode: String
)

fun Routing.Routes(
    metricsRegistry: PrometheusMeterRegistry,
    activeDirectoryClient: ActiveDirectoryClient,
    axsysClient: AxsysClient,
    norg2Client: Norg2Client
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
    simpleGet("/") {
        call.respondText(
            "<!doctype html><html><head><title>NAV-ansatt REST API</title></head><body>Her kjører <a href=\"https://github.com/navikt/navansatt\">NAV-ansatt-API-et</a>.</body></html>",
            ContentType.parse("text/html")
        )
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
            try {
                val result = axsysClient.hentTilganger(location.ident)
                val response: List<Fagomrade> = result.enheter.flatMap { it.fagomrader }.distinct().map {
                    Fagomrade(kode = it)
                }
                call.respond(response)
            } catch (error: NAVAnsattNotFoundError) {
                call.response.status(HttpStatusCode.NotFound)
                call.respond(
                    ApiError(
                        message = "Fant ikke NAV-ansatt med id ${location.ident}"
                    )
                )
            }
        }

        @Location("/navansatt/{ident}/enheter")
        data class GetNAVAnsattEnheterLocation(val ident: String)
        get<GetNAVAnsattEnheterLocation> { location ->
            try {
                val result = axsysClient.hentTilganger(location.ident)
                val enheter = norg2Client.hentEnheter(result.enheter.map { it.enhetId })
                call.respond(
                    enheter.map {
                        NAVEnhetResult(
                            id = it.enhetNr,
                            navn = it.navn,
                            nivaa = it.orgNivaa
                        )
                    }
                )
            } catch (error: NAVAnsattNotFoundError) {
                call.response.status(HttpStatusCode.NotFound)
                call.respond(
                    ApiError(
                        message = "Fant ikke NAV-ansatt med id ${location.ident}"
                    )
                )
            }
        }

        @Location("/enhet/{enhetId}/navansatte")
        data class GetEnhetAnsatte(val enhetId: String)
        get<GetEnhetAnsatte> { location ->
            try {
                val result = axsysClient.hentAnsattIdenter(location.enhetId)

                val allUsers = activeDirectoryClient.getUsers(result.map { it.appIdent })

                val navAnsattData: List<NavAnsattResult> = allUsers.map {
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
