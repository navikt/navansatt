package no.nav.navansatt.mock

data class LdapUser(
    val cn: String,
    val samaccountname: String,
    val displayName: String,
    val givenname: String,
    val sn: String,
    val mail: String,
    val memberOf: List<String>
)

object Users {
    val saksbeh = LdapUser(
        cn = "saksbeh",
        samaccountname = "saksbeh",
        givenname = "Sara",
        sn = "Saksbehandler",
        displayName = "Sara Saksbehandler",
        mail = "sara.saksbehandler@example.com",
        memberOf = listOf(
            "0000-GA-INNTK_8-30",
            "0000-GA-INNTK_8-28",
            "0000-GA-fpsak-saksbehandler",
            "0000-GA-INNTK_FORELDRE",
            "0000-GA-INNTK_PENSJONSGIVENDE",
            "0000-GA-INNTK",
            "0000-GA-PENSJON_SAKSBEHANDLER"
        )
    )

    val klageb = LdapUser(
        cn = "klageb",
        samaccountname = "klageb",
        givenname = "Klara",
        sn = "Klagebehandler",
        displayName = "Klara Klagebehandler",
        mail = "klara.klagebehandler@example.com",
        memberOf = listOf(
            "0000-GA-PENSJON_KLAGEBEH",
            "0000-GA-INNTK_FORELDRE",
            "0000-GA-INNTK_PENSJONSGIVENDE",
            "0000-GA-INNTK"
        )
    )

    val veil = LdapUser(
        cn = "veil",
        samaccountname = "veil",
        givenname = "Vegard",
        sn = "Veileder",
        displayName = "Vegard Veileder",
        mail = "vegard.veileder@example.com",
        memberOf = listOf(
            "0000-GA-PENSJON_KLAGEBEH",
            "0000-GA-INNTK_FORELDRE",
            "0000-GA-INNTK_PENSJONSGIVENDE",
            "0000-GA-INNTK"
        )
    )

    val lukesky = LdapUser(
        cn = "lukesky",
        samaccountname = "lukesky",
        givenname = "Luke",
        sn = "Skywalker",
        displayName = "Luke Skywalker",
        mail = "luke.skywalker@example.com",
        memberOf = listOf(
            "0000-GA-Pensjon",
            "0000-GA-PENSJON_SAKSBEHANDLER"
        )
    )

    val prinleia = LdapUser(
        cn = "prinleia",
        samaccountname = "prinleia",
        givenname = "Prinsesse Leia",
        sn = "Organa",
        displayName = "Prinsesse Leia Organa",
        mail = "prinsesse.leia.organa@example.com",
        memberOf = listOf(
            "0000-GA-Pensjon",
            "0000-GA-PENSJON_SAKSBEHANDLER"
        )
    )
}

val ldapUsers = listOf(
    Users.saksbeh,
    Users.klageb,
    Users.veil,
    Users.lukesky,
    Users.prinleia
)
