package no.nav.navansatt.mock

import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

class MockServer(val port: Int) {
    val server = run {
        embeddedServer(Netty, port = port) {
            install(ContentNegotiation) {
                json()
            }
            routing {
                oidcMocks()
                axsysMock()
                norg2Mock()
            }
        }
    }

    fun listen() {
        println("Starting Mock server on port $port")
        server.start(wait = true)
    }
}
