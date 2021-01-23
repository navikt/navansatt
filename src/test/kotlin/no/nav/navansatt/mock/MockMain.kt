package no.nav.navansatt.mock

import org.slf4j.LoggerFactory
import kotlin.concurrent.thread

val LOG = LoggerFactory.getLogger("no.nav.navansatt.mock.MockMain")
fun main() {
    LOG.info("Running mock backends")
    val ldapServer = thread {
        LdapServer(8389).listen()
    }
    val mockServer = thread {
        MockServer(8060).listen()
    }
}
