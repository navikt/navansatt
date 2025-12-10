package no.nav.navansatt

import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.resources.*
import io.ktor.server.resources.*
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.callid.callId
import io.ktor.server.request.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.*
import io.ktor.utils.io.*
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import kotlinx.serialization.Serializable
import io.ktor.server.routing.get as simpleGet

@Serializable
data class NavAnsattResult(
    val ident: String,
    val navn: String,
    val fornavn: String,
    val etternavn: String,
    val epost: String,
    val enhet: String,
    val groups: List<String>,
)

@Serializable
data class NAVEnhetResult(
    val id: String,
    val navn: String,
    val nivaa: String,
)

@Serializable
data class Fagomrade(
    val kode: String,
)

@Serializable
data class NavAnsattSearchResult(
    val ident: String,
    val navn: String,
    val fornavn: String,
    val etternavn: String,
)

@Serializable
data class NavAnsattSearchResultList(
    val navAnsatte: List<NavAnsattSearchResult>,
)

@OptIn(InternalAPI::class)
fun Route.authenticatedRoutes(
    entraproxyClient: EntraproxyClient,
    norg2Client: Norg2Client,
) {
    simpleGet("/ping-authenticated") {
        call.respond("OK")
    }

    @Resource("/navansatt/{ident}")
    data class GetNAVAnsattLocation(val ident: String)
    get<GetNAVAnsattLocation> { location ->
        try {
            val result = entraproxyClient.hentNavAnsatt(location.ident, call.callId)
            val response = NavAnsattResult(
                ident = result!!.navIdent,
                navn = result.visningNavn,
                fornavn = result.fornavn,
                etternavn = result.etternavn,
                epost = result.epost,
                enhet = result.enhet.enhetnummer,
                groups = entraproxyClient.hentGrupperForAnsatt(location.ident, call.callId)
            )
            call.respond(response)
        } catch (error: NAVAnsattNotFoundError) {
            call.response.status(HttpStatusCode.NotFound)
            call.respond(
                ApiError(
                    message = "Fant ikke NAV-ansatt med id ${location.ident}",
                ),
            )
        }
    }

    @Resource("/navansatt/{ident}/fagomrader")
    data class GetNAVAnsattFagomraderLocation(val ident: String)
    get<GetNAVAnsattFagomraderLocation> { location ->
        try {
            val result = entraproxyClient.hentTema(location.ident, call.callId)
            val response = result.map {
                Fagomrade(kode = it)
            }
            call.respond(response)
        } catch (error: NAVAnsattNotFoundError) {
            call.response.status(HttpStatusCode.NotFound)
            call.respond(
                ApiError(
                    message = "Fant ikke NAV-ansatt med id ${location.ident}",
                ),
            )
        }
    }

    @Resource("/navansatt/{ident}/enheter")
    data class GetNAVAnsattEnheterLocation(val ident: String)
    get<GetNAVAnsattEnheterLocation> { location ->
        try {
            val result = entraproxyClient.hentEnheter(location.ident, call.callId)
            if (result.isNotEmpty()) {
                val enheter = norg2Client.hentEnheter(result.map { it.enhetnummer })
                call.respond(
                    enheter.map {
                        NAVEnhetResult(
                            id = it.enhetNr,
                            navn = it.navn,
                            nivaa = it.orgNivaa,
                        )
                    }
                )
            } else {
                call.response.status(HttpStatusCode.NotFound)
                call.respond(
                    ApiError(
                        message = "Fant ingen enheter for ident ${location.ident}",
                    )
                )
            }
        } catch (error: NAVAnsattNotFoundError) {
            call.response.status(HttpStatusCode.NotFound)
            call.respond(
                ApiError(
                    message = "Fant ikke NAV-ansatt med id ${location.ident}",
                )
            )
        } catch (error: ClientRequestException) {
            call.response.status(HttpStatusCode.InternalServerError)
            call.respond(
                ApiError(
                    message = "Feil i request for ${location.ident} melding ${error.response.rawContent} status ${error.response}",
                )
            )
        }
    }

    @Resource("/enhet/{enhetId}/navansatte")
    data class GetEnhetAnsatte(val enhetId: String)
    get<GetEnhetAnsatte> { location ->
        try {
            val result = entraproxyClient.hentAnsattIdenter(location.enhetId, call.callId)
            val navAnsatte = result
                .map {
                val navAnsatt = entraproxyClient.hentNavAnsatt(it.navIdent, call.callId)
                NavAnsattResult(
                    ident = navAnsatt!!.navIdent,
                    navn = navAnsatt.visningNavn,
                    fornavn = navAnsatt.fornavn,
                    etternavn = navAnsatt.etternavn,
                    epost = navAnsatt.epost,
                    enhet = navAnsatt.enhet.enhetnummer,
                    groups = entraproxyClient.hentGrupperForAnsatt(it.navIdent, call.callId)
                )
            }
            call.respond(navAnsatte)
        } catch (error: EnhetNotFoundError) {
            call.response.status(HttpStatusCode.NotFound)
            call.respond(
                ApiError(
                    message = "Fant ikke NAV-enhet med id ${location.enhetId}",
                )
            )
        }
    }

    @Resource("/gruppe/navansatte")
    data class SearchAnsatteMedGruppe(val groupNames: List<String>)

    post("/gruppe/navansatte") {
        val search = call.receive<SearchAnsatteMedGruppe>()
        val allResults = mutableListOf<NavAnsattSearchResult>()

        try {
            search.groupNames.forEach { groupName ->
                val result = entraproxyClient.hentAnsatteIGruppe(groupName, call.callId)
                allResults.addAll(result.map {
                    NavAnsattSearchResult(
                        ident = it.navIdent,
                        navn = it.visningNavn,
                        fornavn = it.fornavn,
                        etternavn = it.etternavn,
                    )
                })
            }
        } catch (error:  GroupNotFoundError){
            call.response.status(HttpStatusCode.NotFound)
            call.respond(
                ApiError(
                    message = "Fant ikke gruppe ${error.message}",
                )
            )
        }
        call.respond(NavAnsattSearchResultList(navAnsatte = allResults.distinctBy { it.ident }))
    }
}

fun Routing.routes(
    metricsRegistry: PrometheusMeterRegistry,
    entraproxyClient: EntraproxyClient,
    norg2Client: Norg2Client,
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
            ContentType.parse("text/html"),
        )
    }

    authenticate("azure") {
        authenticatedRoutes(
            entraproxyClient = entraproxyClient,
            norg2Client = norg2Client,
        )
    }
}
