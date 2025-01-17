#!/bin/bash
function add_role() {
    ROLE=$1
    ROLE_NAME=$(echo "rolebinding-$NS-default-$ROLE" | sed 's/:/-/g')
    echo "ROLE_NAME=$ROLE_NAME into $NS"
    set +e
    kubectl create rolebinding "$ROLE_NAME" \
      --namespace $NS \
      "--clusterrole=$ROLE" \
      "--user=system:serviceaccount:$NS:default"

    CROLE_NAME=$(echo "cluster-$NS-$ROLE" | sed 's/:/-/g')
    echo "CROLE_NAME=$CROLE_NAME into $NS"
    kubectl delete clusterrolebinding "cluster-$NS-${ROLE/:/-}"
    kubectl create clusterrolebinding "$CROLE_NAME" \
      --clusterrole=$ROLE \
      --group=system:authenticated --namespace $NS
}
if [ "$NS" == "" ]; then
  echo "NS not defined"
  exit 1
fi
for role in "$@"; do
  echo "Adding Role: $role"
  add_role "$role"
done