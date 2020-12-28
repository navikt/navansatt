#!/bin/bash
vault --version || (
  echo "ERROR: You need to install the Vault CLI on your machine: https://www.vaultproject.io/downloads.html" && exit 1
) || exit 1
jq --version || (
  echo "ERROR: You need to install the jq CLI tool on your machine: https://stedolan.github.io/jq/" && exit 1
) || exit 1
base64 --help || (
  echo "ERROR: You need to install the base64 tool on your machine. (brew install base64 on macOS)" && exit 1
) || exit 1

export VAULT_ADDR=https://vault.adeo.no

while true; do
	NAME="$(vault token lookup -format=json | jq '.data.display_name' -r; exit ${PIPESTATUS[0]})"
  ret=${PIPESTATUS[0]}
  if [ $ret -ne 0 ]; then
    echo "Looks like you are not logged in to Vault."

    read -p "Do you want to log in? (y/n) " -n 1 -r
    echo    # (optional) move to a new line
    if [[ $REPLY =~ ^[Yy]$ ]]
    then
      vault login -method=oidc -no-print
    else
      echo "Could not log in to Vault. Aborting."
      exit 1
    fi
  else
    break;
  fi
done

echo "Logged in to Vault as $NAME. Fetching secrets..."

mkdir -p secrets/ldap
mkdir -p secrets/truststore

vault kv get -field username serviceuser/test/srvssolinux > secrets/ldap/username
vault kv get -field password serviceuser/test/srvssolinux > secrets/ldap/password
vault kv get -field keystore certificate/dev/nav-truststore | base64 --decode > secrets/truststore/truststore.jts
vault kv get -field keystorepassword certificate/dev/nav-truststore > secrets/truststore/password

echo "All secrets fetched and stored in the \"secrets\" folder."
