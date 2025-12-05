package no.nav.navansatt

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode.Companion.NotFound
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.resources.*
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class RoutesTest {
    @Test
    fun `Ping with authentication`() {
        withMockApp(
            entraproxyClient = mockk(),
            norg2Client = mockk(),
        ) {
            val response = client.get("/ping-authenticated")
            assertEquals(OK, response.status)
        }
    }

    @Test
    fun `Hent NAV-ansatt`() {
        val entraproxyClient: EntraproxyClient = mockk()

        coEvery { entraproxyClient.hentNavAnsatt("lukesky") } returns NavAnsatt(
            navIdent = "lukesky",
            visningNavn = "Luke Skywalker",
            fornavn = "Luke",
            etternavn = "Skywalker",
            epost = "luke.skywalker@example.com",
            enhet = Enhet("2980", "NAV Dummynavn")
        )
        coEvery { entraproxyClient.hentGrupperForAnsatt("lukesky") } returns
                listOf("0000-GA_dummy-group", "0000-GA_another-group")

        withMockApp(
            entraproxyClient = entraproxyClient,
            norg2Client = mockk(),
        ) {
            val response = client.get("/navansatt/lukesky")
            assertEquals(OK, response.status)
            assertEquals(
                NavAnsattResult(
                    ident = "lukesky",
                    navn = "Luke Skywalker",
                    fornavn = "Luke",
                    etternavn = "Skywalker",
                    epost = "luke.skywalker@example.com",
                    enhet = "2980",
                    groups = listOf("0000-GA_dummy-group", "0000-GA_another-group")
                ),
                Json.decodeFromString(response.bodyAsText()),
            )
        }
    }

    @Test
    fun `Handle NAV-ansatt not found`() {
        val entraproxyClient: EntraproxyClient = mockk()

        coEvery { entraproxyClient.hentNavAnsatt("nobody") } throws NAVAnsattNotFoundError("Fant ikke nobody")

        withMockApp(
            entraproxyClient = entraproxyClient,
            norg2Client = mockk(),
        ) {
            val response = client.get("/navansatt/nobody")
            assertEquals(NotFound, response.status)
            assertEquals(
                ApiError(
                    message = "Fant ikke NAV-ansatt med id nobody",
                ),
                Json.decodeFromString(response.bodyAsText()),
            )
        }
    }

    @Test
    fun `Get fagomrader`() {
        val entraproxyClient: EntraproxyClient = mockk()

        coEvery { entraproxyClient.hentTema("lukesky") } returns listOf("PEN", "UFO", "PEPPERKAKE", "SJAKK")

        withMockApp(
            entraproxyClient = entraproxyClient,
            norg2Client = mockk(),
        ) {
            val response = client.get("/navansatt/lukesky/fagomrader")
            assertEquals(OK, response.status)
            assertEquals(
                listOf(
                    Fagomrade(kode = "PEN"),
                    Fagomrade(kode = "UFO"),
                    Fagomrade(kode = "PEPPERKAKE"),
                    Fagomrade(kode = "SJAKK"),
                ),
                Json.decodeFromString(response.bodyAsText()),
            )
        }
    }

    @Test
    fun `Get fagomrader - handle NAV-ansatt not found`() {
        val entraproxyClient: EntraproxyClient = mockk()

        coEvery { entraproxyClient.hentTema("nobody") } throws NAVAnsattNotFoundError("not found")

        withMockApp(
            entraproxyClient = entraproxyClient,
            norg2Client = mockk(),
        ) {
            val response = client.get("/navansatt/nobody/fagomrader")
            assertEquals(NotFound, response.status)
            assertEquals(
                ApiError(
                    message = "Fant ikke NAV-ansatt med id nobody",
                ),
                Json.decodeFromString(response.bodyAsText()),
            )
        }
    }


    @Test
    fun `Get NAV-enheter for user`() {
        val entraproxyClient: EntraproxyClient = mockk()
        val norg2Client: Norg2Client = mockk()

        coEvery { entraproxyClient.hentEnheter("lukesky") } returns listOf(
            Enhet(
                enhetnummer = "123",
                navn = "enhet 123"
            ),
            Enhet(
                enhetnummer = "456",
                navn = "enhet456"
            ),
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
            entraproxyClient = entraproxyClient,
            norg2Client = norg2Client,
        ) {
            val response = client.get("/navansatt/lukesky/enheter")
            assertEquals(OK, response.status)
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
                Json.decodeFromString(response.bodyAsText()),
            )
        }
    }


    @Test
    fun `Get NAV-enheter for user - handle NAV-ansatt not found`() {
        val entraproxyClient: EntraproxyClient = mockk()

        coEvery { entraproxyClient.hentEnheter("nobody") } throws NAVAnsattNotFoundError("oops")

        withMockApp(
            entraproxyClient = entraproxyClient,
            norg2Client = mockk(),
        ) {
            val response = client.get("/navansatt/nobody/enheter")
            assertEquals(NotFound, response.status)
            assertEquals(
                ApiError(
                    message = "Fant ikke NAV-ansatt med id nobody"
                ),
                Json.decodeFromString(response.bodyAsText()),
            )
        }
    }
/*
    @Test
    fun `Get NAV-ansatte for enhet`() {
        val entraproxyClient: EntraproxyClient = mockk()

        coEvery { entraproxyClient.hentAnsattIdenter("123") } returns listOf(
            NavAnsatt(
                navIdent = "lukesky",
                navn = "Luke Skywalker",
                fornavn = "Luke",
                etternavn = "Skywalker",
                epost = "luke.skywalker@example.com",
                enhet = "2980"
            ),
            NavAnsatt(
                navIdent = "darthvad",
                navn = "Darth Vader",
                fornavn = "Darth",
                etternavn = "Vader",
                epost = "darth.vader@example.com",
                enhet = "2980"
            ),
            NavAnsatt(
                navIdent = "prinleia",
                navn = "Prinsesse Leia Organa",
                fornavn = "Leia",
                etternavn = "Organa",
                epost = "prinsesse.leia.organa@example.com",
                enhet = "2980"
            )
        )

        withMockApp(
            entraproxyClient = entraproxyClient,
            norg2Client = mockk(),
        ) {
            val response = client.get("/enhet/123/navansatte")
            assertEquals(OK, response.status)
            assertEquals(
                listOf(
                    NavAnsattResult(
                        ident = "lukesky",
                        navn = "Luke Skywalker",
                        fornavn = "Luke",
                        etternavn = "Skywalker",
                        epost = "luke.skywalker@example.com",
                        enhet = "2980",
                        groups = listOf("groups"),
                    ),
                    NavAnsattResult(
                        ident = "darthvad",
                        navn = "Darth Vader",
                        fornavn = "Darth",
                        etternavn = "Vader",
                        epost = "darth.vader@example.com",
                        enhet = "2980",
                        groups = listOf("groups"),
                    ),
                    NavAnsattResult(
                        ident = "prinleia",
                        navn = "Prinsesse Leia Organa",
                        fornavn = "Leia",
                        etternavn = "Organa",
                        epost = "prinsesse.leia.organa@example.com",
                        enhet = "2980",
                        groups = listOf("groups"),
                    ),
                ),
                Json.decodeFromString(response.bodyAsText())
            )
        }
    }
*/
    @Test
    fun `Get NAV-ansatte for enhet - handle NAV-enhet not found`() {
        val entraproxyClient: EntraproxyClient = mockk()

        coEvery { entraproxyClient.hentAnsattIdenter("4444") } throws EnhetNotFoundError("oops")

        withMockApp(
            entraproxyClient = entraproxyClient,
            norg2Client = mockk(),
        ) {
            val response = client.get("/enhet/4444/navansatte")
            assertEquals(NotFound, response.status)
            assertEquals(
                ApiError(
                    message = "Fant ikke NAV-enhet med id 4444"
                ),
                Json.decodeFromString(response.bodyAsText())
            )
        }
    }

    private fun withMockApp(
        entraproxyClient: EntraproxyClient,
        norg2Client: Norg2Client,
        testCode: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        install(Resources)
        install(ContentNegotiation) {
            json()
        }
        routing {
            authenticatedRoutes(
                entraproxyClient = entraproxyClient,
                norg2Client = norg2Client
            )
        }

        testCode()
    }
}
