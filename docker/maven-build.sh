#!/bin/sh
set -eu
# Temporary trust and proxy files are removed in this RUN, not retained in image layers.
truststore=""
wgetrc=""
settings=""
cleanup() {
  [ -z "$truststore" ] || rm -f "$truststore"
  [ -z "$wgetrc" ] || rm -f "$wgetrc"
  [ -z "$settings" ] || rm -f "$settings"
}
trap cleanup EXIT HUP INT TERM
if [ -f /run/secrets/proxy_ca ]; then
  truststore=$(mktemp)
  cp "$JAVA_HOME/lib/security/cacerts" "$truststore"
  keytool -importcert -noprompt -trustcacerts -alias build-proxy \
    -file /run/secrets/proxy_ca -keystore "$truststore" -storepass changeit >/dev/null 2>&1
  export MAVEN_OPTS="${MAVEN_OPTS:-} -Djavax.net.ssl.trustStore=$truststore"
  # The wrapper can download Maven with either wget or curl before Java starts.
  wgetrc=$(mktemp)
  printf 'ca_certificate = /run/secrets/proxy_ca\n' > "$wgetrc"
  export WGETRC="$wgetrc" CURL_CA_BUNDLE=/run/secrets/proxy_ca
fi
if [ -f /run/secrets/maven_settings ]; then
  sh ./mvnw -B -ntp -s /run/secrets/maven_settings "$@"
else
  settings=$(mktemp)
  java /build/ProxySettings.java "$settings"
  sh ./mvnw -B -ntp -s "$settings" "$@"
fi
