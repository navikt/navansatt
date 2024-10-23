package no.nav.navansatt.mock

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.Routing
import io.ktor.routing.get
import kotlinx.serialization.Serializable

fun Routing.norg2Mock() {
    @Serializable
    data class EnhetDTO(
        val enhetNr: String,
        val navn: String,
        val orgNivaa: String
    )

    get("/rest/norg2/api/v1/enhet") {
        val enheter = call.request.queryParameters.getAll("enhetsnummerListe")
        if (enheter == null) {
            call.respondText(status = HttpStatusCode.BadRequest) {
                "Missing ?enhetsnummerListe in query parameters"
            }
        } else {
            call.respond(
                enheter.map {
                    EnhetDTO(
                        enhetNr = it,
                        navn = "Enhet med navn $it",
                        orgNivaa = "EN"
                    )
                }
            )
        }
    }
}
