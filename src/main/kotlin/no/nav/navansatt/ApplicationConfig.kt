package no.nav.navansatt

import java.io.File
import java.lang.RuntimeException

data class ApplicationConfig(
    val adUrl: String,
    val adBase: String,
    val adUsername: String,
    val adPassword: String,
    val azureWellKnown: String,
    val openamWellKnown: String,
    val axsysUrl: String
)

val vtp = "http://localhost:8060"
fun appConfigLocal() = ApplicationConfig(
    adUrl = "ldap://localhost:8389",
    adBase = "DC=test,DC=local",
    adUsername = "",
    adPassword = "",
    azureWellKnown = "$vtp/rest/AzureAd/123456/v2.0/.well-known/openid-configuration",
    openamWellKnown = "$vtp/rest/isso/oauth2/.well-known/openid-configuration",
    axsysUrl = "$vtp/rest/axsys"
)

fun readEnv(name: String): String =
    System.getenv(name) ?: throw RuntimeException("Missing $name environment variable.")


fun appConfigNais() = ApplicationConfig(
    adUrl = readEnv("LDAP_URL"),
    adBase = readEnv("LDAP_BASE"),
    adUsername = File("/secrets/ldap/username").readText(),
    adPassword = File("/secrets/ldap/password").readText(),
    azureWellKnown = readEnv("AZURE_APP_WELL_KNOWN_URL"),
    openamWellKnown = readEnv("OPENAM_WELL_KNOWN_URL"),
    axsysUrl = readEnv("AXSYS_URL")
)
