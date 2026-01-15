package no.nav.navansatt

import java.lang.RuntimeException

data class ApplicationConfig(

    val azureClientId: String,
    val azureClientSecret: String,
    val azureTokenEndpoint: String,
    val azureWellKnown: String,
    val norg2Url: String,
    val msGraphApiUrl: String
)

fun readEnv(name: String): String =
    System.getenv(name) ?: throw RuntimeException("Missing $name environment variable.")

fun appConfigNais() = ApplicationConfig(
    azureClientId = readEnv("AZURE_APP_CLIENT_ID"),
    azureClientSecret = readEnv("AZURE_APP_CLIENT_SECRET"),
    azureTokenEndpoint = readEnv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    azureWellKnown = readEnv("AZURE_APP_WELL_KNOWN_URL"),
    norg2Url = readEnv("NORG2_URL"),
    msGraphApiUrl = readEnv("MS_GRAPH_API_URL")
)
