#!/usr/bin/env bash
(return 0 2>/dev/null) && sourced=1 || sourced=0
if ((sourced != 0)); then
    echo "Do not source this script $0"
    exit 0
fi

SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
function print_usage() {
    echo "Usage: [release-name]"
    echo "Where:"
    echo "  release-name: Optional name for helm release. Default is dataflow"
}

if [ ! -f ./scdf-helm-values.yml ]; then
    echo "Cannot find scdf-helm-values.yml. Run helm-values-template.sh and populate the values for database and broker."
    exit 1
fi
if [ "$1" != "" ]; then
    RELEASE_NAME="$1"
else
    RELEASE_NAME=dataflow
fi
echo "Release name: $RELEASE_NAME"
if [ "$SCDF_TYPE" = "pro" ]; then
    HELM_PACKAGE=oci://docker.io/springcloud/spring-cloud-dataflow-helm
else
    HELM_PACKAGE=oci://dev.registry.tanzu.vmware.com/p-scdf-kubernetes/spring-pro-helm
fi
helm install "$RELEASE_NAME" "$HELM_PACKAGE" \
    --version "$PACKAGE_VERSION" \
    --create-namespace --namespace $NS \
    --values ./scdf-helm-values.yml

echo "Waiting for Skipper"
kubectl rollout status deployment --namespace "$NS" scdf-skipper
echo "Waiting for Data Flow"
kubectl rollout status deployment --namespace "$NS" scdf-server

source "$SCDIR/export-dataflow-ip.sh"
sh "$SCDIR/k8s/register-apps.sh"
echo "Access Data Flow at $DATAFLOW_URL/dashboard"
