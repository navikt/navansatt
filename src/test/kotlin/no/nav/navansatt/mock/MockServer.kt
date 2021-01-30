package no.nav.navansatt.mock

import io.ktor.application.install
import io.ktor.features.ContentNegotiation
import io.ktor.routing.routing
import io.ktor.serialization.json
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import org.slf4j.LoggerFactory

val test = io.ktor.client.engine.mock.MockEngine

class MockServer(val port: Int) {
    companion object {
        val LOG = LoggerFactory.getLogger(MockServer::class.java)
    }

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
        LOG.info("Starting Mock server on port $port")
        server.start(wait = true)
    }
}
