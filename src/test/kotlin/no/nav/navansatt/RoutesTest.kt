package no.nav.navansatt

import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
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
import io.ktor.http.HttpStatusCode.Companion.InternalServerError
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.contentType

class RoutesTest {
    @Test
    fun `Ping with authentication`() {
        withMockApp(
            graphClient = mockk(),
            norg2Client = mockk(),
        ) {
            val response = client.get("/ping-authenticated")
            assertEquals(OK, response.status)
        }
    }

    @Test
    fun `Hent NAV-ansatt`() {
        val graphClient: GraphClient = mockk()

        coEvery { graphClient.getUserByNavIdent("lukesky",null) } returns User(
            id = "123",
            onPremisesSamAccountName = "lukesky",
            displayName = "Luke Skywalker",
            givenName = "Luke",
            surname = "Skywalker",
            userPrincipalName = "luke.skywalker@example.com",
            streetAddress = "2980"
        )
        coEvery { graphClient.getGroupsForUser("123", null) } returns
                listOf("0000-GA_dummy-group", "0000-GA_another-group")

        withMockApp(
            graphClient = graphClient,
            norg2Client = mockk(),
        ) {
            val response = client.get("/navansatt/lukesky")
            assertEquals(OK, response.status)
            assertEquals(
                NavAnsattResultUtvidet(
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
        val graphClient: GraphClient = mockk()

        coEvery { graphClient.getUserByNavIdent("nobody",null) } throws RuntimeException("Fant ikke nobody")

        withMockApp(
            graphClient = graphClient,
            norg2Client = mockk(),
        ) {
            val response = client.get("/navansatt/nobody")
            assertEquals(InternalServerError, response.status)
            assertEquals(
                ApiError(
                    message = "Fant ikke NAV-ansatt med id nobody feil: Fant ikke nobody",
                ),
                Json.decodeFromString(response.bodyAsText()),
            )
        }
    }

    @Test
    fun `Get fagomrader`() {
        val graphClient: GraphClient = mockk()

        coEvery { graphClient.getTemaForUser("lukesky", null) } returns listOf("PEN", "UFO", "PEPPERKAKE", "SJAKK")

        withMockApp(
            graphClient = graphClient,
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
        val graphClient: GraphClient = mockk()

        coEvery { graphClient.getTemaForUser("nobody", null) } throws RuntimeException("not found")

        withMockApp(
            graphClient = graphClient,
            norg2Client = mockk(),
        ) {
            val response = client.get("/navansatt/nobody/fagomrader")
            assertEquals(InternalServerError, response.status)
            assertEquals(
                ApiError(
                    message = "Fant ikke NAV-ansatt med id nobody feil: not found",
                ),
                Json.decodeFromString(response.bodyAsText()),
            )
        }
    }


    @Test
    fun `Get NAV-enheter for user`() {
        val graphClient: GraphClient = mockk()
        val norg2Client: Norg2Client = mockk()

        coEvery { graphClient.getEnheterForUser("lukesky", null) } returns listOf(
            "123",
            "456"
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
            graphClient = graphClient,
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
        val graphClient: GraphClient = mockk()

        coEvery { graphClient.getEnheterForUser("nobody", null) } throws RuntimeException("oops")

        withMockApp(
            graphClient = graphClient,
            norg2Client = mockk(),
        ) {
            val response = client.get("/navansatt/nobody/enheter")
            assertEquals(InternalServerError, response.status)
            assertEquals(
                ApiError(
                    message = "Fant ikke NAV-ansatt med id nobody feil: oops"
                ),
                Json.decodeFromString(response.bodyAsText()),
            )
        }
    }

    @Test
    fun `Get NAV-ansatte for enhet`() {
        val graphClient: GraphClient = mockk()

        coEvery { graphClient.getGroupIdByName("0000-GA_ENHET_123", null) } returns "12345"
        coEvery { graphClient.enhetIdToGroupName("123", ) } returns "0000-GA_ENHET_123"
        coEvery { graphClient.getGroupMembersById("12345",null) } returns listOf(
            User(
                onPremisesSamAccountName = "lukesky",
                displayName = "Luke Skywalker",
                givenName = "Luke",
                surname = "Skywalker",
                userPrincipalName = "luke.skywalker@example.com",
                streetAddress = "2980"
            ),
            User(
                onPremisesSamAccountName = "darthvad",
                displayName = "Darth Vader",
                givenName = "Darth",
                surname = "Vader",
                userPrincipalName = "darth.vader@example.com",
                streetAddress = "2980"
            ),
            User(
                onPremisesSamAccountName = "prinleia",
                displayName = "Prinsesse Leia Organa",
                givenName = "Leia",
                surname = "Organa",
                userPrincipalName = "prinsesse.leia.organa@example.com",
                streetAddress = "2980"
            )
        )

        withMockApp(
            graphClient = graphClient,
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
                        enhet = "2980"
                    ),
                    NavAnsattResult(
                        ident = "darthvad",
                        navn = "Darth Vader",
                        fornavn = "Darth",
                        etternavn = "Vader",
                        epost = "darth.vader@example.com",
                        enhet = "2980"
                    ),
                    NavAnsattResult(
                        ident = "prinleia",
                        navn = "Prinsesse Leia Organa",
                        fornavn = "Leia",
                        etternavn = "Organa",
                        epost = "prinsesse.leia.organa@example.com",
                        enhet = "2980"
                    ),
                ),
                Json.decodeFromString(response.bodyAsText())
            )
        }
    }

    @Test
    fun `Get NAV-ansatte for enhet - handle NAV-enhet not found`() {
        val graphClient: GraphClient = mockk()

        coEvery { graphClient.enhetIdToGroupName("4444", ) } returns "0000-GA_ENHET_4444"
        coEvery { graphClient.getGroupIdByName("0000-GA_ENHET_4444", null) } throws RuntimeException("oops")
        withMockApp(
            graphClient = graphClient,
            norg2Client = mockk(),
        ) {
            val response = client.get("/enhet/4444/navansatte")
            assertEquals(InternalServerError, response.status)
            assertEquals(
                ApiError(
                    message = "Fant ikke NAV-enhet med id 4444, feil: oops"
                ),
                Json.decodeFromString(response.bodyAsText())
            )
        }
    }

    @Test
    fun `Post Sok Nav-ansatte i grupper`() {
        val graphClient: GraphClient = mockk()

        coEvery { graphClient.getUsersInGroup("0000-GA-Group1", null) } returns listOf(
            User(
                onPremisesSamAccountName = "hanSolo",
                displayName = "Han Solo",
                givenName = "Han",
                surname = "Solo",
                userPrincipalName = "han.solo@example.com",
                streetAddress = "2980"
            ),
            User(
                onPremisesSamAccountName = "chewbacca",
                displayName = "Chewbacca",
                givenName = "Chewbacca",
                surname = "Wookiee",
                userPrincipalName = "chewbacca@example.com",
                streetAddress = "2980"
            ),
            User(
                onPremisesSamAccountName = "lukeSky",
                displayName = "Luke Skywalker",
                givenName = "Luke",
                surname = "Skywalker",
                userPrincipalName = "luke.skywalker@example.com",
                streetAddress = "2980")
        )
        coEvery { graphClient.getUsersInGroup("0000-GA-Group2", null) } returns listOf(
            User(
                onPremisesSamAccountName = "hanSolo",
                displayName = "Han Solo",
                givenName = "Han",
                surname = "Solo",
                userPrincipalName = "han.solo@example.com",
                streetAddress = "2980"
            ),
            User(
                onPremisesSamAccountName = "chewbacca",
                displayName = "Chewbacca",
                givenName = "Chewbacca",
                surname = "Wookiee",
                userPrincipalName = "chewbacca@example.com",
                streetAddress = "2980"
            ),
            User(
                onPremisesSamAccountName = "Yoda",
                displayName = "Yoda",
                givenName = "Yoda",
                surname = "Master",
                userPrincipalName = "yoda@example.com",
                streetAddress = "9000")
        )

        withMockApp(
            graphClient = graphClient,
            norg2Client = mockk(),
        ) {
            val response = client.post("/gruppe/navansatte") {
                contentType(io.ktor.http.ContentType.Application.Json)
                setBody("""{"groupNames":["0000-GA-Group1","0000-GA-Group2"]}""")
            }
            assertEquals(OK, response.status)
            assertEquals(
                NavAnsattSearchResultList(
                    navAnsatte = listOf(
                        NavAnsattSearchResult(
                            ident = "hanSolo",
                            navn = "Han Solo",
                            fornavn = "Han",
                            etternavn = "Solo"
                        ),
                        NavAnsattSearchResult(
                            ident = "chewbacca",
                            navn = "Chewbacca",
                            fornavn = "Chewbacca",
                            etternavn = "Wookiee"
                        ),
                        NavAnsattSearchResult(
                            ident = "lukeSky",
                            navn = "Luke Skywalker",
                            fornavn = "Luke",
                            etternavn = "Skywalker"
                        ),
                        NavAnsattSearchResult(
                            ident = "Yoda",
                            navn = "Yoda",
                            fornavn = "Yoda",
                            etternavn = "Master"
                        ),
                    )
                ),
                Json.decodeFromString(response.bodyAsText())
            )
        }
    }

    private fun withMockApp(
        graphClient: GraphClient,
        norg2Client: Norg2Client,
        testCode: suspend ApplicationTestBuilder.() -> Unit,
    ) = testApplication {
        install(Resources)
        install(ContentNegotiation) {
            json()
        }
        routing {
            authenticatedRoutes(
                norg2Client = norg2Client,
                graphClient = graphClient,
            )
        }

        testCode()
    }
}
