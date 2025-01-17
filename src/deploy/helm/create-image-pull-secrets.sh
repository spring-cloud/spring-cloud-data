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
check_env DOCKER_HUB_USERNAME
check_env DOCKER_HUB_PASSWORD

$SCDIR/add-local-registry-secret.sh reg-creds-dockerhub docker.io "$DOCKER_HUB_USERNAME" "$DOCKER_HUB_PASSWORD" "$NS"

if [ "$SCDF_TYPE" = "pro" ]; then
    check_env TANZU_DOCKER_USERNAME
    check_env TANZU_DOCKER_PASSWORD
    $SCDIR/add-local-registry-secret.sh reg-creds-dev-registry dev.registry.tanzu.vmware.com "$TANZU_DOCKER_USERNAME" "$TANZU_DOCKER_PASSWORD"
fi
