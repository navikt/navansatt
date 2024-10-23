package no.nav.navansatt.mock

import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import kotlinx.serialization.Serializable
import no.nav.navansatt.AxsysEnhet
import no.nav.navansatt.TilgangResponse

fun Routing.axsysMock() {
    @Serializable
    data class Ident(
        val appIdent: String,
        val historiskIdent: Long
    )
    get("/rest/axsys/api/v1/enhet/123/brukere") {
        val result: List<Ident> = listOf(Users.prinleia, Users.klageb, Users.lukesky)
            .mapIndexed { i, el ->
                Ident(
                    appIdent = el.samaccountname,
                    historiskIdent = 1000 + i.toLong()
                )
            }
        call.respond(result)
    }

    get("/rest/axsys/api/v1/tilgang/lukesky") {
        call.respond(TilgangResponse(enheter = listOf(AxsysEnhet(enhetId = "1234", navn = "Testenhet", fagomrader = listOf("PEN")))))
    }
}
