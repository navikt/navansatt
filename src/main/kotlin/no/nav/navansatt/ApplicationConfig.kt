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

val vtp = "http://localhost:8061"
fun appConfigLocal() = ApplicationConfig(
    adUrl = "ldap://localhost:8389",
    adBase = "DC=test,DC=local",
    adUsername = "",
    adPassword = "",
    azureWellKnown = "$vtp/rest/AzureAd/123456/v2.0/.well-known/openid-configuration",
    openamWellKnown = "$vtp/rest/isso/oauth2/.well-known/openid-configuration",
    axsysUrl = "$vtp/rest/axsys"
)

fun appConfigNais() = ApplicationConfig(
    adUrl = System.getenv("LDAP_URL") ?: throw RuntimeException("Missing LDAP_URL environment variable."),
    adBase = System.getenv("LDAP_BASE") ?: throw RuntimeException("Missing LDAP_BASE environment variable."),
    adUsername = File("/secrets/ldap/username").readText(),
    adPassword = File("/secrets/ldap/password").readText(),
    azureWellKnown = System.getenv("AZURE_APP_WELL_KNOWN_URL") ?: throw RuntimeException("Missing AZURE_APP_WELL_KNOWN_URL environment variable."),
    openamWellKnown = System.getenv("OPENAM_WELL_KNOWN_URL") ?: throw RuntimeException("Missing OPENAM_WELL_KNOWN_URL environment variable."),
    axsysUrl = System.getenv("AXSYS_URL") ?: throw RuntimeException("Missing AXSYS_URL environment variable."),
)
