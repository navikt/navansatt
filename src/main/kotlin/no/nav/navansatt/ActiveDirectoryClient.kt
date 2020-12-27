package no.nav.navansatt

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.slf4j.LoggerFactory
import java.util.Hashtable
import java.util.regex.Pattern
import javax.naming.Context
import javax.naming.NamingEnumeration
import javax.naming.directory.Attributes
import javax.naming.directory.BasicAttribute
import javax.naming.directory.BasicAttributes
import javax.naming.ldap.InitialLdapContext

data class User(
    val ident: String,
    val displayName: String,
    val firstName: String,
    val lastName: String,
    val email: String,
    val groups: List<String>
)
class ActiveDirectoryClient(
    val url: String,
    val base: String,
    val username: String,
    val password: String?
) {
    companion object {
        private val LOG = LoggerFactory.getLogger(ActiveDirectoryClient::class.java)
    }

    suspend fun getUser(ident: String): User? = withContext(Dispatchers.IO) {
        val env = Hashtable<String, String>().apply {
            put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
            put(Context.PROVIDER_URL, url)
            put(Context.SECURITY_PRINCIPAL, username)
            password?.let {
                put(Context.SECURITY_CREDENTIALS, it)
            }
        }

        val root = InitialLdapContext(env, null)

        val attrs = BasicAttributes().apply {
            put(BasicAttribute("objectClass", "user"))
            put(BasicAttribute("cn", ident))
        }

        val result = root.search("OU=Users,OU=NAV,OU=BusinessUnits,$base", attrs)

        while (result.hasMore()) {
            val entry = result.next()
            return@withContext User(
                ident = ident,
                displayName = readAttribute(entry.attributes, "displayname"),
                firstName = readAttribute(entry.attributes, "givenname"),
                lastName = readAttribute(entry.attributes, "sn"),
                email = readAttribute(entry.attributes, "mail"),
                groups = entry.attributes["memberof"]?.getAll()?.let { getAllGroups(it) } ?: emptyList()
            )
        }

        LOG.warn("No user with ident \"$ident\" was found.")

        return@withContext null
    }

    private fun readAttribute(attrs: Attributes, key: String): String {
        return attrs[key]?.get()?.toString() ?: run {
            LOG.warn("LDAP object has no attribute \"$key\", defaulting to empty string.")
            return ""
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
