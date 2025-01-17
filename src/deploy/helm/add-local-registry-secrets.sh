#!/usr/bin/env bash
if [ -z "$BASH_VERSION" ]; then
    echo "This script requires Bash. Use: bash $0 $*"
    exit 1
fi
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
function check_env() {
  eval ev='$'$1
  if [ "$ev" == "" ]; then
    echo "env var $1 not defined"
    exit 1
  fi
}


if [ "$4" = "" ]; then
    echo "Arguments: <secret-name> <registry-name> <registry-user> <registry-password>"
    exit 1
fi
SECRET_NAME=$1
REGISTRY_NAME=$2
REGISTRY_USER=$3
REGISTRY_PWD=$4
SECRET_NS=$NS
if [ "$5" != "" ]; then
    SECRET_NS=$5
fi
check_env SECRET_NAME
check_env SECRET_NS

kubectl create secret docker-registry "$SECRET_NAME" \
    --docker-server="$REGISTRY_NAME" \
    --docker-username="$REGISTRY_USER" \
    --docker-password="$REGISTRY_PWD" \
    --namespace "$SECRET_NS"


if [ -f ./scdf-helm-values.yml ]; then
    REG_SECRET=$(yq '.global.registry.secret.ref' ./scdf-helm-values.yml)
    if [ "$REG_SECRET" = "" ]; then
        yq '.global.registry.secret.ref = strenv(SECRET_NAME)' -i ./scdf-helm-values.yml
    fi
    PULL_SECRET=$(yq '.global.imagePullSecrets | .[] | select(. == strenv(SECRET_NAME))' ./scdf-helm-values.yml)
    if [ "$PULL_SECRET" = "" ]; then
        PULL_SECRET=$(yq '.global.imagePullSecrets += strenv(SECRET_NAME)' ./scdf-helm-values.yml)
    fi
fi