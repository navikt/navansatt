#!/usr/bin/env sh

# Enable native access for Netty and other libraries that need it
export JAVA_OPTS="${JAVA_OPTS} --enable-native-access=ALL-UNNAMED"

if test -r "${NAV_TRUSTSTORE_PATH}";
then
    if ! keytool -list -keystore ${NAV_TRUSTSTORE_PATH} -storepass "${NAV_TRUSTSTORE_PASSWORD}" > /dev/null;
    then
        echo Truststore is corrupt, or bad password
        exit 1
    fi

    JAVA_OPTS="${JAVA_OPTS} -Djavax.net.ssl.trustStore=${NAV_TRUSTSTORE_PATH}"
    JAVA_OPTS="${JAVA_OPTS} -Djavax.net.ssl.trustStorePassword=${NAV_TRUSTSTORE_PASSWORD}"
    export JAVA_OPTS
fi

# inject proxy settings set by the nais platform
#export JAVA_OPTS="${JAVA_OPTS} ${JAVA_PROXY_OPTIONS}"
