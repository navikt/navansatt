package no.nav.navansatt

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class AxsysClientTest {
    @Test
    fun `hent tilganger`() {
        val mockClient = makeMockClient { request ->
            when (request.url.toString()) {
                "http://example/api/v1/tilgang/lukesky?inkluderAlleEnheter=false" -> {
                    respond(
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type" to listOf("application/json")),
                        content = """
                                {
                                  "enheter": [{
                                    "enhetId": "123",
                                    "navn": "NAV Hakkebakkeskogen",
                                    "fagomrader": ["FOO", "BAR"]
                                  }, {
                                    "enhetId": "456",
                                    "navn": "NAV Kardemomme By",
                                    "fagomrader": ["FOTBALL", "SJAKK"]
                                  }]
                                }
                        """.trimIndent(),
                    )
                }
                else -> {
                    respond("not found")
                }
            }
        }
        val client = AxsysClient(httpClient = mockClient, axsysUrl = "http://example")
        val tilganger = runBlocking { client.hentTilganger("lukesky") }
        assertEquals(
            TilgangResponse(
                enheter = listOf(
                    AxsysEnhet(
                        enhetId = "123",
                        navn = "NAV Hakkebakkeskogen",
                        fagomrader = listOf("FOO", "BAR"),
                    ),
                    AxsysEnhet(
                        enhetId = "456",
                        navn = "NAV Kardemomme By",
                        fagomrader = listOf("FOTBALL", "SJAKK"),
                    )
                ),
            ),
            tilganger
        )
    }

    @Test
    fun `NAV-ansatt not found`() {
        assertThrows<NAVAnsattNotFoundError> {
            val mockClient = makeMockClient {
                respondError(status = HttpStatusCode.NotFound)
            }
            val client = AxsysClient(httpClient = mockClient, axsysUrl = "http://example")
            runBlocking { client.hentTilganger("nobody") }
        }
    }

    @Test
    fun `Hent ansatt-identer`() {
        val mockClient = makeMockClient { request ->
            when (request.url.toString()) {
                "http://example/api/v1/enhet/1234/brukere" -> {
                    respond(
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type" to listOf("application/json")),
                        content = """
                                [{
                                  "appIdent": "hello"
                                },{
                                  "appIdent": "world"
                                }]
                        """.trimIndent()
                    )
                }
                else -> {
                    respond("not found")
                }
            }
        }
        val client = AxsysClient(httpClient = mockClient, axsysUrl = "http://example")
        val brukere = runBlocking { client.hentAnsattIdenter("1234") }
        assertEquals(
            listOf(Ident("hello"), Ident("world")),
            brukere
        )
    }

    @Test
    fun `NAV-enhet not found`() {
        assertThrows<EnhetNotFoundError> {
            val mockClient = makeMockClient {
                respondError(status = HttpStatusCode.NotFound)
            }
            val client = AxsysClient(httpClient = mockClient, axsysUrl = "http://example")
            runBlocking { client.hentAnsattIdenter("1234") }
        }
    }

    private fun makeMockClient(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): HttpClient {
        return HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                })
            }
            engine {
                addHandler { request ->
                    assertEquals("ignore", request.headers["Nav-Call-Id"])
                    assertEquals("navansatt", request.headers["Nav-Consumer-Id"])
                    assertEquals("application/json", request.headers["Accept"])
                    handler(request)
                }
            }
        }
    }
}
