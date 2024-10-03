package no.nav.navansatt

import io.ktor.http.HttpMethod.Companion.Get
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.locations.KtorExperimentalLocationsAPI
import io.ktor.server.locations.Locations
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationEngine
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.withTestApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RoutesTest {
    @Test
    fun `Ping with authentication`() {
        withMockApp(
            activeDirectoryClient = mockk(),
            axsysClient = mockk(),
            norg2Client = mockk(),
        ) {
            with(
                handleRequest(
                    method = Get,
                    uri = "/ping-authenticated",
                )
            ) {
                assertEquals(OK, response.status())
            }
        }
    }

    @Test
    fun `Get NAV-ansatt`() {
        val activeDirectoryClient: ActiveDirectoryClient = mockk()

        coEvery { activeDirectoryClient.getUser("lukesky") } returns User(
            ident = "lukesky",
            displayName = "Luke Skywalker",
            firstName = "Luke",
            lastName = "Skywalker",
            email = "luke.skywalker@example.com",
            groups = emptyList(),
        )

        withMockApp(
            activeDirectoryClient = activeDirectoryClient,
            axsysClient = mockk(),
            norg2Client = mockk(),
        ) {
            with(
                handleRequest(
                    method = Get,
                    uri = "/navansatt/lukesky",
                )
            ) {
                assertEquals(OK, response.status())
                assertEquals(
                    NavAnsattResult(
                        ident = "lukesky",
                        navn = "Luke Skywalker",
                        fornavn = "Luke",
                        etternavn = "Skywalker",
                        epost = "luke.skywalker@example.com",
                        groups = emptyList(),
                    ),
                    Json.decodeFromString(response.content ?: ""),
                )
            }
        }
    }

    @Test
    fun `Handle NAV-ansatt not found`() {
        val activeDirectoryClient: ActiveDirectoryClient = mockk()

        coEvery { activeDirectoryClient.getUser("nobody") } returns null

        withMockApp(
            activeDirectoryClient = activeDirectoryClient,
            axsysClient = mockk(),
            norg2Client = mockk(),
        ) {
            with(
                handleRequest(
                    method = Get,
                    uri = "/navansatt/nobody",
                )
            ) {
                assertEquals(NotFound, response.status())
                assertEquals(
                    ApiError(
                        message = "User not found",
                    ),
                    Json.decodeFromString(response.content ?: ""),
                )
            }
        }
    }

    @Test
    fun `Get fagomrader`() {
        val axsysClient: AxsysClient = mockk()

        coEvery { axsysClient.hentTilganger("lukesky") } returns TilgangResponse(
            enheter = listOf(
                AxsysEnhet(
                    enhetId = "123",
                    navn = "NAV Kardemomme By",
                    fagomrader = listOf("PEN", "UFO", "PEPPERKAKE"),
                ),
                AxsysEnhet(
                    enhetId = "456",
                    navn = "NAV Andeby",
                    fagomrader = listOf("SJAKK", "PEN"),
                )
            )
        )

        withMockApp(
            activeDirectoryClient = mockk(),
            axsysClient = axsysClient,
            norg2Client = mockk(),
        ) {
            with(
                handleRequest(
                    method = Get,
                    uri = "/navansatt/lukesky/fagomrader"
                )
            ) {
                assertEquals(OK, response.status())
                assertEquals(
                    listOf(
                        Fagomrade(kode = "PEN"),
                        Fagomrade(kode = "UFO"),
                        Fagomrade(kode = "PEPPERKAKE"),
                        Fagomrade(kode = "SJAKK"),
                    ),
                    Json.decodeFromString(response.content ?: ""),
                )
            }
        }
    }

    @Test
    fun `Get fagomrader - handle NAV-ansatt not found`() {
        val axsysClient: AxsysClient = mockk()

        coEvery { axsysClient.hentTilganger("nobody") } throws NAVAnsattNotFoundError("not found")

        withMockApp(
            activeDirectoryClient = mockk(),
            axsysClient = axsysClient,
            norg2Client = mockk(),
        ) {
            with(
                handleRequest(
                    method = Get,
                    uri = "/navansatt/nobody/fagomrader",
                )
            ) {
                assertEquals(NotFound, response.status())
                assertEquals(
                    ApiError(
                        message = "Fant ikke NAV-ansatt med id nobody",
                    ),
                    Json.decodeFromString(response.content ?: ""),
                )
            }
        }
    }

    @Test
    fun `Get NAV-enheter for user`() {
        val axsysClient: AxsysClient = mockk()
        val norg2Client: Norg2Client = mockk()

        coEvery { axsysClient.hentTilganger("lukesky") } returns TilgangResponse(
            enheter = listOf(
                AxsysEnhet(
                    enhetId = "123",
                    navn = "NAV Kardemomme By",
                    fagomrader = listOf("PEN", "UFO", "PEPPERKAKE"),
                ),
                AxsysEnhet(
                    enhetId = "456",
                    navn = "NAV Andeby",
                    fagomrader = listOf("SJAKK", "PEN"),
                )
            )
        )
        coEvery { norg2Client.hentEnheter(listOf("123", "456")) } returns listOf(
            Norg2Enhet(
                enhetNr = "123",
                navn = "NAV Kardemomme By",
                orgNivaa = "ABC",
            ),
            Norg2Enhet(
                enhetNr = "456",
                navn = "NAV Andeby",
                orgNivaa = "DEF",
            )
        )

        withMockApp(
            activeDirectoryClient = mockk(),
            axsysClient = axsysClient,
            norg2Client = norg2Client,
        ) {
            with(
                handleRequest(
                    method = Get,
                    uri = "/navansatt/lukesky/enheter",
                )
            ) {
                assertEquals(OK, response.status())
                assertEquals(
                    listOf(
                        NAVEnhetResult(
                            id = "123",
                            navn = "NAV Kardemomme By",
                            nivaa = "ABC",
                        ),
                        NAVEnhetResult(
                            id = "456",
                            navn = "NAV Andeby",
                            nivaa = "DEF",
                        )
                    ),
                    Json.decodeFromString(response.content ?: ""),
                )
            }
        }
    }

    @Test
    fun `Get NAV-enheter for user - handle NAV-ansatt not found`() {
        val axsysClient: AxsysClient = mockk()

        coEvery { axsysClient.hentTilganger("nobody") } throws NAVAnsattNotFoundError("oops")
        withMockApp(
            activeDirectoryClient = mockk(),
            axsysClient = axsysClient,
            norg2Client = mockk(),
        ) {
            with(
                handleRequest(
                    method = Get,
                    uri = "/navansatt/nobody/enheter",
                )
            ) {
                assertEquals(NotFound, response.status())
                assertEquals(
                    ApiError(
                        message = "Fant ikke NAV-ansatt med id nobody"
                    ),
                    Json.decodeFromString(response.content ?: ""),
                )
            }
        }
    }

    @Test
    fun `Get NAV-ansatte for enhet`() {
        val activeDirectoryClient: ActiveDirectoryClient = mockk()
        val axsysClient: AxsysClient = mockk()

        coEvery { axsysClient.hentAnsattIdenter("123") } returns listOf(
            Ident("lukesky"),
            Ident("darthvad"),
            Ident("prinleia"),
        )
        coEvery { activeDirectoryClient.getUsers(listOf("lukesky", "darthvad", "prinleia")) } returns listOf(
            User(
                ident = "lukesky",
                displayName = "Luke Skywalker",
                firstName = "Luke",
                lastName = "Skywalker",
                email = "luke.skywalker@example.com",
                groups = emptyList(),
            ),
            User(
                ident = "darthvad",
                displayName = "Darth Vader",
                firstName = "Darth",
                lastName = "Vader",
                email = "darth.vader@example.com",
                groups = emptyList(),
            ),
            User(
                ident = "prinleia",
                displayName = "Prinsesse Leia Organa",
                firstName = "Leia",
                lastName = "Organa",
                email = "prinsesse.leia.organa@example.com",
                groups = emptyList(),
            )
        )

        withMockApp(
            activeDirectoryClient = activeDirectoryClient,
            axsysClient = axsysClient,
            norg2Client = mockk(),
        ) {
            with(
                handleRequest(
                    method = Get,
                    uri = "/enhet/123/navansatte",
                )
            ) {
                assertEquals(OK, response.status())
                assertEquals(
                    listOf(
                        NavAnsattResult(
                            ident = "lukesky",
                            navn = "Luke Skywalker",
                            fornavn = "Luke",
                            etternavn = "Skywalker",
                            epost = "luke.skywalker@example.com",
                            groups = emptyList()
                        ),
                        NavAnsattResult(
                            ident = "darthvad",
                            navn = "Darth Vader",
                            fornavn = "Darth",
                            etternavn = "Vader",
                            epost = "darth.vader@example.com",
                            groups = emptyList()
                        ),
                        NavAnsattResult(
                            ident = "prinleia",
                            navn = "Prinsesse Leia Organa",
                            fornavn = "Leia",
                            etternavn = "Organa",
                            epost = "prinsesse.leia.organa@example.com",
                            groups = emptyList()
                        ),
                    ),
                    Json.decodeFromString(response.content ?: "")
                )
            }
        }
    }

    @Test
    fun `Get NAV-ansatte for enhet - handle NAV-enhet not found`() {
        val axsysClient: AxsysClient = mockk()

        coEvery { axsysClient.hentAnsattIdenter("4444") } throws EnhetNotFoundError("oops")

        withMockApp(
            activeDirectoryClient = mockk(),
            axsysClient = axsysClient,
            norg2Client = mockk()
        ) {
            with(
                handleRequest(
                    method = Get,
                    uri = "/enhet/4444/navansatte"
                )
            ) {
                assertEquals(NotFound, response.status())
                assertEquals(
                    ApiError(
                        message = "Fant ikke NAV-enhet med id 4444"
                    ),
                    Json.decodeFromString(response.content ?: "")
                )
            }
        }
    }

    @OptIn(KtorExperimentalLocationsAPI::class)
    private fun withMockApp(
        activeDirectoryClient: ActiveDirectoryClient,
        axsysClient: AxsysClient,
        norg2Client: Norg2Client,
        testCode: TestApplicationEngine.() -> Unit,
    ) {
        withTestApplication(
            {
                install(Locations)
                install(ContentNegotiation) {
                    json()
                }
                routing {
                    authenticatedRoutes(
                        activeDirectoryClient = activeDirectoryClient,
                        axsysClient = axsysClient,
                        norg2Client = norg2Client
                    )
                }
            },
            testCode,
        )
    }
}
