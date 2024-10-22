package no.nav.navansatt.mock

import io.ktor.http.ContentType
import io.ktor.server.application.call
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get

fun Routing.oidcMocks() {
    get("/rest/AzureAd/123456/v2.0/.well-known/openid-configuration") {
        call.respondText(
            """
                        {
                          "frontendUrl": "http://localhost:8060/#",
                          "baseUrl": "http://localhost:8060/rest/AzureAd",
                          "graphUrl": "http://localhost:8060/rest/MicrosoftGraphApi",
                          "tenant": "123456",
                          "profile": null,
                          "claims_supported": [
                            "sub",
                            "iss",
                            "cloud_instance_name",
                            "cloud_instance_host_name",
                            "cloud_graph_host_name",
                            "msgraph_host",
                            "aud",
                            "exp",
                            "iat",
                            "auth_time",
                            "acr",
                            "nonce",
                            "preferred_username",
                            "name",
                            "tid",
                            "ver",
                            "at_hash",
                            "c_hash",
                            "email"
                          ],
                          "cloud_graph_host_name": "graph.windows.net",
                          "cloud_instance_name": "microsoftonline.com",
                          "frontchannel_logout_supported": true,
                          "http_logout_supported": true,
                          "id_token_signing_alg_values_supported": [
                            "RS256"
                          ],
                          "msgraph_host": "graph.microsoft.com",
                          "rbac_url": "https://pas.windows.net",
                          "request_uri_parameter_supported": false,
                          "response_modes_supported": [
                            "query",
                            "fragment",
                            "form_post"
                          ],
                          "response_types_supported": [
                            "code",
                            "id_token",
                            "code id_token",
                            "id_token token"
                          ],
                          "scopes_supported": [
                            "openid",
                            "profile",
                            "email",
                            "offline_access"
                          ],
                          "subject_types_supported": [
                            "pairwise"
                          ],
                          "tenant_region_scope": "EU",
                          "token_endpoint_auth_methods_supported": [
                            "client_secret_post",
                            "private_key_jwt",
                            "client_secret_basic"
                          ],
                          "issuer": "https://login.microsoftonline.com/123456/v2.0",
                          "userinfo_endpoint": "http://localhost:8060/rest/MicrosoftGraphApi/oidc/userinfo",
                          "end_session_endpoint": "http://localhost:8060/rest/AzureAd/123456/v2.0/logout",
                          "authorization_endpoint": "http://localhost:8060/#/azuread/123456/v2.0/authorize",
                          "jwks_uri": "http://localhost:8060/rest/AzureAd/123456/discovery/v2.0/keys",
                          "token_endpoint": "http://localhost:8060/rest/AzureAd/123456/oauth2/v2.0/token"
                        }
            """.trimIndent(),
            ContentType.parse("application/json")
        )
    }

    get("/rest/AzureAd/123456/discovery/v2.0/keys") {
        call.respondText(
            """
                        {
                            "keys": [
                                    {
                                        "kty": "RSA",
                                        "alg": "RS256",
                                        "use": "sig",
                                        "kid": "1",
                                        "n": "AIJXIQO8sJTogYcT4-lsRnG4k9no6X1Yr5Fs9CnUPAYl8WWlATK-IXQer6GH0lmjWmZXugL8tdDFa_oZ_BH9eRtcuKLf7xFXaJoSwbGl9VHMjEmPCfq2brKtbD5pAWW3tF6Ir7f_wCWlwUtjmYvD4AnvNB2BDdhtzwd8rbwPGAvfZd8Qc05mJUlgrYfZiYkeKL5UHbLMLu67gzQq7TtjJLS6xXoSpIWtyxYmYqtDE2l8ytl8r4I8FPKNdqclqVZ5hfL7bWNpkJZ_Auf8m08U9QutntkNEEfStubd2GOqng47A9EsqWsQrX4kOXcInaK8E4tZiims_-QLgAJrZlLEeqU",
                                        "e": "AQAB"
                                    }
                            ]
                        }
            """.trimIndent(),
            ContentType.parse("application/json")
        )
    }
}
