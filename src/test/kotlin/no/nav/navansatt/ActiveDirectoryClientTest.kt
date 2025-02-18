package no.nav.navansatt

import kotlinx.coroutines.runBlocking
import no.nav.navansatt.mock.LdapServer
import org.junit.jupiter.api.Test
import java.net.ServerSocket
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ActiveDirectoryClientTest {
    /*
    This will test lots of stuff (many assertions in a single test),
    which is not ideal, but it reuses a single LDAP mock server for
    better performance.
    */
    @Test
    fun `Interactions with AD`() {
        runBlocking {
            val freePort = findFreePort()
            val ldapServer = thread(isDaemon = true) {
                println("Starting thread")
                LdapServer(freePort).apply {
                    println("Now starting to listen on port $freePort")
                    listen()
                    println("Done listening")
                }
            }

            // Wait for the LDAP server to boot
            Thread.sleep(1500)

            val activeDirectoryClient = DefaultActiveDirectoryClient(
                url = "ldap://localhost:$freePort",
                base = "DC=test,DC=local",
                username = "",
                password = ""
            )

            // Test: get a single user
            val user = activeDirectoryClient.getUser("lukesky")
            assertEquals(
                User(
                    ident = "lukesky",
                    displayName = "Luke Skywalker",
                    firstName = "Luke",
                    lastName = "Skywalker",
                    email = "luke.skywalker@example.com",
                    groups = listOf(
                        "0000-GA-Pensjon",
                        "0000-GA-PENSJON_SAKSBEHANDLER"
                    )
                ),
                user
            )

            // Test: ask for non-existing user
            val nonExistingUser = activeDirectoryClient.getUser("nobody")
            assertNull(nonExistingUser)

            // Get multiple users, where one user doesn't exist
            val multipleUsers = activeDirectoryClient.getUsers(listOf("lukesky", "prinleia", "nobody"))
            assertEquals(
                listOf(
                    User(
                        ident = "lukesky",
                        displayName = "Luke Skywalker",
                        firstName = "Luke",
                        lastName = "Skywalker",
                        email = "luke.skywalker@example.com",
                        groups = listOf(
                            "0000-GA-Pensjon",
                            "0000-GA-PENSJON_SAKSBEHANDLER"
                        )
                    ),
                    User(
                        ident = "prinleia",
                        displayName = "Prinsesse Leia Organa",
                        firstName = "Prinsesse Leia",
                        lastName = "Organa",
                        email = "prinsesse.leia.organa@example.com",
                        groups = listOf(
                            "0000-GA-Pensjon",
                            "0000-GA-PENSJON_SAKSBEHANDLER"
                        )
                    )
                ),
                multipleUsers
            )

            ldapServer.interrupt()
        }
    }

    private fun findFreePort(): Int {
        val socket = ServerSocket(0)
        return socket.localPort.also {
            socket.close()
        }
    }
}
