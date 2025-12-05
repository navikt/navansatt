package no.nav.navansatt

import java.lang.RuntimeException

data class ApplicationConfig(

    val azureClientId: String,
    val azureClientSecret: String,
    val azureEndpoint: String,
    val azureWellKnown: String,
    val entraproxyUrl: String,
    val entraproxyScope: String,
    val norg2Url: String
)

fun readEnv(name: String): String =
    System.getenv(name) ?: throw RuntimeException("Missing $name environment variable.")

fun appConfigNais() = ApplicationConfig(
    azureClientId = readEnv("AZURE_APP_CLIENT_ID"),
    azureClientSecret = readEnv("AZURE_APP_CLIENT_SECRET"),
    azureWellKnown = readEnv("AZURE_APP_WELL_KNOWN_URL"),
    azureEndpoint = readEnv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    entraproxyUrl = readEnv("ENTRAPROXY_URL"),
    entraproxyScope = readEnv("ENTRAPROXY_SCOPE"),
    norg2Url = readEnv("NORG2_URL")
)
