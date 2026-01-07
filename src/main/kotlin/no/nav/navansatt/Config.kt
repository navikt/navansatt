package no.nav.navansatt

import java.lang.RuntimeException

data class ApplicationConfig(

    val azureClientId: String,
    val azureClientSecret: String,
    val azureEndpoint: String,
    val azureWellKnown: String,
    val azureTenant: String,
    val norg2Url: String
)

fun readEnv(name: String): String =
    System.getenv(name) ?: throw RuntimeException("Missing $name environment variable.")

fun appConfigNais() = ApplicationConfig(
    azureClientId = readEnv("AZURE_APP_CLIENT_ID"),
    azureClientSecret = readEnv("AZURE_APP_CLIENT_SECRET"),
    azureEndpoint = readEnv("AZURE_OPENID_CONFIG_TOKEN_ENDPOINT"),
    azureWellKnown = readEnv("AZURE_APP_WELL_KNOWN_URL"),
    azureTenant = readEnv("AZURE_APP_TENANT_ID"),
    norg2Url = readEnv("NORG2_URL")
)
