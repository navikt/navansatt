package no.nav.navansatt.mock

import kotlin.concurrent.thread

fun main() {
    println("Running mock backends")
    val ldapServer = thread {
        LdapServer(8389).listen()
    }
    val mockServer = thread {
        MockServer(8060).listen()
    }
}
