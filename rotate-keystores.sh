#!/bin/bash

#rm -f keystore.p12
#
#keytool -genkey -keyalg RSA -alias default -keystore keystore.p12 -validity 30 -keysize 2048 -storetype pkcs12 -ext SAN=dns:alindsey-a01.vmware.com,dns:localhost,ip:192.168.50.203,ip:0.0.0.0,ip:127.0.0.1 -dname "CN=test, OU=Unknown, O=Unknown, L=Unknown, ST=Unknown, C=Unknown" -storepass password -noprompt
#
#cp keystore.p12 truststore.p12
#
#cp keystore.p12 locator/
#cp truststore.p12 locator/
#
#cp keystore.p12 server/
#cp truststore.p12 server/

# Generate PEM-encoded certificate and private key
openssl req -x509 -newkey rsa:2048 -nodes -days 365 -keyout tls.key -out tls.crt \
  -subj "/C=US/ST=Unknown/L=Unknown/O=Unknown/OU=Unknown/CN=Unknown" \
  -extensions san \
  -config <( \
    echo '[req]'; \
    echo 'distinguished_name=req'; \
    echo '[san]'; \
    echo 'subjectAltName=DNS:localhost,DNS:alindsey-a01.vmware.com,IP:127.0.0.1,IP:0.0.0.0,IP:192.168.50.203')

# Generate a PKCS#12 keystore with the certificate and private key
cat tls.key tls.crt > tls.tmp
openssl pkcs12 -export -in tls.tmp -out tls.p12 -name default -noiter -nomaciter -passout pass:password
rm tls.tmp

# Copy the keystore into the locator and server directories
mkdir -p locator
cp tls.p12 locator/
mkdir -p server
cp tls.p12 server/
