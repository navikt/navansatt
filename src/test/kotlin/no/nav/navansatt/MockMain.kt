package no.nav.navansatt.mock

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

val LOG: Logger = LoggerFactory.getLogger("no.nav.navansatt.mock.MockMain")

fun main() {
    LOG.info("Running mock backends")
    thread {
        LdapServer(8390).listen()
    }
    thread {
        MockServer(8066).listen()
    }
}
