package no.nav.navansatt

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
import org.slf4j.LoggerFactory

@Serializable
data class NavAnsattResult(
    val ident: String,
    val navn: String,
    val fornavn: String,
    val etternavn: String,
    val epost: String,
    val enhet: String,
)
@Serializable
data class NavAnsattResultUtvidet(
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
    norg2Client: Norg2Client,
    graphClient: GraphClient
) {

    val log = LoggerFactory.getLogger(Route::class.java)
    simpleGet("/ping-authenticated") {
        call.respond("OK")
    }

    @Resource("/navansatt/{ident}")
    data class GetNAVAnsattLocation(val ident: String)
    get<GetNAVAnsattLocation> { location ->
        try {
            val result = graphClient.getUserByNavIdent(location.ident, call.callId)
            val id = result!!.id
            val response = NavAnsattResultUtvidet(
                ident = result.onPremisesSamAccountName,
                navn = result.displayName,
                fornavn = result.givenName,
                etternavn = result.surname,
                epost = result.userPrincipalName,
                enhet = result.streetAddress,
                groups = graphClient.getGroupsForUser(id, call.callId)
            )
            call.respond(response)

        } catch (exception: UserNotFoundException) {
            log.info("Fant ikke NAV-ansatt med id ${location.ident}")
            call.response.status(HttpStatusCode.NotFound)
            call.respond(
                ApiError(
                    message = exception.message!!,
                ),
            )
        } catch (exception: Exception) {
            log.error("Feil ved henting av NAV-ansatt", exception)
            call.response.status(HttpStatusCode.InternalServerError)
            call.respond(
                ApiError(
                    message = "Feil ved henting av NAV-ansatt med id ${location.ident} feil: ${exception.message}",
                ),
            )
        }
    }

    @Resource("/navansatt/{ident}/fagomrader")
    data class GetNAVAnsattFagomraderLocation(val ident: String)
    get<GetNAVAnsattFagomraderLocation> { location ->
        try {
            val result = graphClient.getTemaForUser(location.ident, call.callId)
            val response = result.map {
                Fagomrade(kode = it)
            }
            call.respond(response)
        } catch (exception: UserNotFoundException) {
            log.info("Fant ikke NAV-ansatt med id ${location.ident}")
            call.response.status(HttpStatusCode.NotFound)
            call.respond(
                ApiError(
                    message = exception.message!!,
                ),
            )
        } catch (exception: Exception) {
            log.error("Feil ved henting av fagområder for NAV-ansatt", exception)
            call.response.status(HttpStatusCode.InternalServerError)
            call.respond(
                ApiError(
                    message = "Fant ikke NAV-ansatt med id ${location.ident} feil: ${exception.message}",
                ),
            )
        }
    }

    @Resource("/navansatt/{ident}/enheter")
    data class GetNAVAnsattEnheterLocation(val ident: String)
    get<GetNAVAnsattEnheterLocation> { location ->
        try {
            val result = graphClient.getEnheterForUser (location.ident, call.callId)
            if (result.isNotEmpty()) {
                val enheter = norg2Client.hentEnheter(result.map { it })
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
                log.error("Feil ved henting av enheter for NAV-ansatt ${location.ident}: Ingen enheter funnet")
                call.response.status(HttpStatusCode.NotFound)
                call.respond(
                    ApiError(
                        message = "Fant ingen enheter for ident ${location.ident}",
                    )
                )
            }
        } catch (exception: UserNotFoundException) {
            log.info("Fant ikke NAV-ansatt med id ${location.ident}")
            call.response.status(HttpStatusCode.NotFound)
            call.respond(
                ApiError(
                    message = exception.message!!,
                ),
            )
        } catch (exception: Exception) {
            log.error("Feil ved henting av enheter for NAV-ansatt", exception)
            call.response.status(HttpStatusCode.InternalServerError)
            call.respond(
                ApiError(
                    message = "Fant ikke NAV-ansatt med id ${location.ident} feil: ${exception.message}",
                )
            )
        }
    }

    @Resource("/enhet/{enhetId}/navansatte")
    data class GetEnhetAnsatte(val enhetId: String)
    get<GetEnhetAnsatte> { location ->
        try {
            val groupId = graphClient.getGroupIdByName(graphClient.enhetIdToGroupName(enhetId = location.enhetId),call.callId)
            log.info("Ser etter NAV-ansatte i gruppe med id: $groupId")

            val res = groupId?.let {
                graphClient.getGroupMembersById(it,call.callId)
            } ?: throw RuntimeException("Enhet ${location.enhetId} er ikke funnet")
            val navAnsatte = res
                .map {
                    NavAnsattResult(
                        ident = it.onPremisesSamAccountName,
                        navn = it.displayName,
                        fornavn = it.givenName,
                        etternavn = it.surname,
                        epost = it.userPrincipalName,
                        enhet = it.streetAddress
                    )
                }
            call.respond(navAnsatte)

        } catch (exception: GroupNotFoundException) {
            log.info("Fant ikke Enhet med id ${location.enhetId}")
            call.response.status(HttpStatusCode.NotFound)
            call.respond(
                ApiError(
                    message = "Enhet med id ${location.enhetId} finnes ikke: ${exception.message}",
                ),
            )
        } catch (exception: Exception) {
            log.error("Feil ved henting av NAV-ansatte i enhet", exception)
            call.response.status(HttpStatusCode.InternalServerError)
            call.respond(
                ApiError(
                    message = "Fant ikke NAV-enhet med id ${location.enhetId}, feil: ${exception.message}",
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
                val result = graphClient.getUsersInGroup(groupName, call.callId)
                allResults.addAll(result.map {
                    NavAnsattSearchResult(
                        ident = it.onPremisesSamAccountName,
                        navn = it.displayName,
                        fornavn = it.givenName,
                        etternavn = it.surname
                    )
                })
            }
        } catch (exception:  Exception){
            log.error("Feil ved søk etter NAV-ansatte i grupper", exception)
            call.response.status(HttpStatusCode.InternalServerError)
            call.respond(
                ApiError(
                    message = "Fant ikke aktuell gruppe, feil: ${exception.message}",
                )
            )
        }
        call.respond(NavAnsattSearchResultList(navAnsatte = allResults.distinctBy { it.ident }))
    }
}

fun Routing.routes(
    metricsRegistry: PrometheusMeterRegistry,
    norg2Client: Norg2Client,
    graphService: GraphClient
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
            norg2Client = norg2Client,
            graphClient = graphService
        )
    }
}
