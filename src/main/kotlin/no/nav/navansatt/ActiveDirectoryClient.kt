package no.nav.navansatt

import io.opentelemetry.api.trace.SpanKind
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.text.StringEscapeUtils
import org.slf4j.LoggerFactory
import java.util.Hashtable
import java.util.Locale
import java.util.regex.Pattern
import javax.naming.Context
import javax.naming.NamingEnumeration
import javax.naming.directory.Attributes
import javax.naming.directory.BasicAttribute
import javax.naming.directory.BasicAttributes
import javax.naming.directory.SearchControls
import javax.naming.ldap.InitialLdapContext

data class User(
    val ident: String,
    val displayName: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val streetAddress: String?,
    val groups: List<String>,
)

class ActiveDirectoryClient(
    val url: String,
    val base: String,
    val username: String,
    val password: String?,
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(ActiveDirectoryClient::class.java)
    }

    private val env = Hashtable<String, String>().apply {
        put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
        put(Context.PROVIDER_URL, url)
        put(Context.SECURITY_PRINCIPAL, username)
        password?.let {
            put(Context.SECURITY_CREDENTIALS, it)
        }
    }

    @WithSpan(kind = SpanKind.CLIENT)
    suspend fun getUsers(idents: List<String>): List<User> = withContext(Dispatchers.IO) {
        val root = InitialLdapContext(env, null)

        val filter = (0..(idents.size - 1)).map {
            "(cn={$it})"
        }.joinToString("")

        val filterExpr = "(&(objectClass=user)(|$filter))"

        val result = root.search(
            "OU=Users,OU=NAV,OU=BusinessUnits,$base",
            filterExpr,
            idents.toTypedArray(),
            SearchControls()
        )

        val users: MutableList<User> = ArrayList()
        while (result.hasMore()) {
            val entry = result.next()

            users.add(
                User(
                    ident = readAttribute(entry.attributes, "cn"),
                    displayName = readAttribute(entry.attributes, "displayname"),
                    firstName = readAttribute(entry.attributes, "givenname"),
                    lastName = readAttribute(entry.attributes, "sn"),
                    email = readEmail(entry.attributes),
                    streetAddress = readAttribute(entry.attributes, "streetaddress"),
                    groups = entry.attributes["memberof"]?.all?.let { getAllGroups(it) } ?: emptyList()
                )
            )
        }

        if (idents.size != users.size) {
            LOG.warn("getUsers queried ${idents.size} idents, but only ${users.size} were found in LDAP.")

            val requestedItentsSet = idents.toSet()
            val returnedIdents = users.map { it.ident }.toSet()
            val missing = requestedItentsSet.minus(returnedIdents)

            // Don't log the full list, if very many idents are missing. (Prevent log spamming)
            if (missing.size < 50) {
                LOG.warn("Idents missing in LDAP: [${missing.joinToString(", ")}]")
            }
        }

        users
    }

    @WithSpan(kind = SpanKind.CLIENT)
    suspend fun getUser(ident: String): User? = withContext(Dispatchers.IO) {
        val root = InitialLdapContext(env, null)

        val attrs = BasicAttributes().apply {
            put(BasicAttribute("objectClass", "user"))
            put(BasicAttribute("cn", ident))
        }

        val result = root.search("OU=Users,OU=NAV,OU=BusinessUnits,$base", attrs)

        while (result.hasMore()) {
            val entry = result.next()

            val returnedIdent = readAttribute(entry.attributes, "cn")

            if (ident.lowercase(Locale.getDefault()) != returnedIdent.lowercase(Locale.getDefault())) {
                LOG.warn("Mismatch in ident: Asked for ident $ident but a user with CN $returnedIdent was found.")
            }

            return@withContext User(
                ident = returnedIdent,
                displayName = readAttribute(entry.attributes, "displayname"),
                firstName = readAttribute(entry.attributes, "givenname"),
                lastName = readAttribute(entry.attributes, "sn"),
                email = readEmail(entry.attributes),
                streetAddress = readAttribute(entry.attributes, "streetaddress"),
                groups = entry.attributes["memberof"]?.all?.let { getAllGroups(it) } ?: emptyList()
            )
        }

        LOG.warn("No user with ident \"$ident\" was found.")

        return@withContext null
    }

    private fun readAttribute(attrs: Attributes, key: String): String {
        val result = attrs[key]?.get()?.toString() ?: run {
            LOG.warn("LDAP object has no attribute \"$key\", defaulting to empty string.")
            return ""
        }

        val unescaped = StringEscapeUtils.unescapeJava(result)
        if (unescaped != result) {
            LOG.warn("LDAP returned a strange result, original=$result, unescaped=$unescaped. Returning original.")
        }

        return result
    }

    private fun readEmail(attrs: Attributes): String {
        return attrs["mail"]?.get()?.toString() ?: run {
            val upn = attrs["userprincipalname"]?.get()?.toString()
            upn?.let {
                if (it.contains('@')) {
                    return@run it
                } else {
                    LOG.warn("LDAP object has no attribute \"mail\", and its attribute \"upn\" seems like it's not an email address. Fallback to empty string.")
                    return ""
                }
            } ?: run {
                LOG.warn("LDAP object had neither a \"mail\" attribute or \"upn\" attribute. Fallback to empty string.")
                return ""
            }
        }
    }

    private fun getAllGroups(values: NamingEnumeration<*>): List<String> {
        val list = ArrayList<String>()
        while (values.hasMoreElements()) {
            val nextElement = parseGroupName(values.nextElement().toString())
            nextElement?.let {
                list += it
            }
        }
        return list
    }

    private fun parseGroupName(fullGroupName: String): String? {
        val matcher = Pattern.compile("CN=([^,]+)").matcher(fullGroupName)
        while (matcher.find()) {
            return matcher.group(1)
        }
        LOG.warn("Malformed response from LDAP server: \"$fullGroupName\" is not a valid LDAP group format. Expected format \"CN=thegroupname, OU=AccountGroups, OU=Groups, OU=NAV etc...\"")
        return null
    }
}
