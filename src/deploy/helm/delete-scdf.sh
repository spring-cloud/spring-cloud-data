#!/usr/bin/env bash
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
function check_env() {
  eval ev='$'$1
  if [ "$ev" == "" ]; then
    echo "env var $1 not defined"
    if ((sourced != 0)); then
      return 1
    else
      exit 1
    fi
  fi
}
check_env NS
check_env PACKAGE_VERSION
if [ "$1" != "" ]; then
    RELEASE_NAME="$1"
else
    RELEASE_NAME=dataflow
fi

echo "Deleting $RELEASE_NAME from $NS"
helm uninstall $RELEASE_NAME --namespace $NS --wait

kubectl delete apps --all --namespace="$NS"
kubectl delete deployments --all --namespace="$NS"
kubectl delete statefulsets --all --namespace="$NS"
kubectl delete svc --all --namespace="$NS"
kubectl delete all --all --namespace="$NS"
kubectl delete pods --all --namespace="$NS"
kubectl delete pvc --all --namespace="$NS"
kubectl delete configmaps --all --namespace="$NS"
kubectl delete secrets --all --namespace="$NS"
kubectl delete namespace $NS
