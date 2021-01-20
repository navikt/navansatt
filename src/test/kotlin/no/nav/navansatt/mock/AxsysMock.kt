package no.nav.navansatt.mock

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import kotlinx.serialization.Serializable

fun Routing.axsysMock() {
    @Serializable
    data class Ident(
        val appIdent: String,
        val historiskIdent: Long
    )
    get("/rest/axsys/api/v1/enhet/123/brukere") {
        val result: List<Ident> = listOf(Users.prinleia, Users.klageb, Users.lukesky).mapIndexed { i, el ->
            Ident(
                appIdent = el.samaccountname,
                historiskIdent = 1000 + i.toLong()
            )
        }
        call.respond(result)
    }
}
