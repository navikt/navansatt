package no.nav.navansatt.mock

import com.unboundid.ldap.listener.InMemoryDirectoryServer
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig
import com.unboundid.ldap.listener.InMemoryListenerConfig
import com.unboundid.ldap.sdk.Entry
import com.unboundid.ldif.LDIFAddChangeRecord
import com.unboundid.ldif.LDIFChangeRecord
import com.unboundid.ldif.LDIFReader
import kotlin.concurrent.thread

val setup = """
dn: dc=local
changetype: add
objectClass: top
objectClass: dcObject
dc: local

dn: DC=test,DC=local
changetype: add
dc: test
o: test
objectClass: top
objectclass: dcObject
objectclass: organization

dn: OU=BusinessUnits,DC=test,DC=local
changetype: add
dc: BusinessUnits
objectClass: organizationalUnit
objectClass: top

dn: OU=NAV,OU=BusinessUnits,DC=test,DC=local
changetype: add
dc: NAV
objectClass: organizationalUnit
objectClass: top

dn: OU=Users,OU=NAV,OU=BusinessUnits,DC=test,DC=local
changetype: add
dc: Users
objectClass: organizationalUnit
objectClass: top
""".trimIndent()

class LdapServer(val port: Int) {
    private val directoryServer = run {
        val cfg = InMemoryDirectoryServerConfig("DC=local")
        cfg.setEnforceAttributeSyntaxCompliance(false)
        cfg.setEnforceSingleStructuralObjectClass(false)
        cfg.schema = null // dropper valider schema slik at vi slipper å definere alle object classes
        val ldapConfig = InMemoryListenerConfig.createLDAPConfig("LDAP", port)
        cfg.setListenerConfigs(ldapConfig)
        val server = InMemoryDirectoryServer(cfg)

        LDIFReader(setup.byteInputStream()).use { reader ->
            do {
                val record: LDIFChangeRecord? = reader.readChangeRecord()
                record?.let {
                    record.processChange(server)
                }
            } while (record != null)
        }

        ldapUsers.forEach {
            val entry = Entry("CN=${it.cn},OU=Users,OU=NAV,OU=BusinessUnits,DC=test,DC=local").apply {
                addAttribute("objectClass", "user", "organizationalPerson", "person", "top")
                addAttribute("objectCategory", "CN=Person,CN=Schema,CN=Configuration,DC=test,DC=local")
                addAttribute("cn", it.cn)
                addAttribute("samaccountname", it.samaccountname)
                addAttribute("displayName", it.displayName)
                addAttribute("givenname", it.givenname)
                addAttribute("sn", it.sn)
                addAttribute("mail", it.mail)
                addAttribute("memberOf", it.memberOf.map { group -> "CN=$group,OU=AccountGroups,OU=Groups,OU=NAV,OU=BusinessUnits,DC=test,DC=local" })
            }

            LDIFAddChangeRecord(entry).processChange(server)
        }
        server
    }

    fun listen() {
        println("Starting LDAP server on $port")
        directoryServer.startListening()
    }
}
