package no.nav.navansatt.mock

import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class MockServer(private val port: Int) {
    companion object {
        val LOG: Logger = LoggerFactory.getLogger(MockServer::class.java)
    }

    val server = run {
        embeddedServer(Netty, port = port) {
            install(ContentNegotiation)
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
