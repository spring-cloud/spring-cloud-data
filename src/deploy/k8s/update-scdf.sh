#!/bin/bash
if [ "$NS" = "" ]; then
    echo "NS not defined" >&2
    exit 0
fi
if [ -z "$BASH_VERSION" ]; then
    echo "This script requires Bash. Use: bash $0 $*"
    exit 1
fi
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
PARENT=$(realpath "$SCDIR/../../..")
if [ "$DATAFLOW_PRO_VERSION" = "" ]; then
  DATAFLOW_PRO_VERSION=1.6.1-SNAPSHOT
fi
if [ "$DATAFLOW_VERSION" = "" ]; then
  export DATAFLOW_VERSION=3.0.0-SNAPSHOT
fi
if [ "$SKIPPER_VERSION" = "" ]; then
  export SKIPPER_VERSION=3.0.0-SNAPSHOT
fi

case $BROKER in
"kafka")
    export BROKER="kafka"
    ;;
"rabbit" | "rabbitmq")
    export BROKER="rabbit"
    ;;
"")
    export BROKER="kafka"
    ;;
*)
    echo "BROKER=$BROKER not supported"
    ;;
esac

case $DATABASE in
"mariadb")
    # default is MariaDB
    ;;

"postgres" | "postgresql")
    export DATABASE=postgresql
    ;;

"mysql" | "mysql57")
    export DATABASE=mysql57
    ;;

"")
    # default is MariaDB
    export DATABASE=mariadb
    ;;
*)
    echo "DATABASE=$DATABASE not supported"
    ;;
esac

if [ "$K8S_DRIVER" = "" ]; then
    K8S_DRIVER=kind
fi
set +e
COUNT=$(kubectl get namespace | grep -c "$NS")
if [ "$COUNT" = "0" ]; then
    echo "Namespace $NS not found"
    exit 1
fi
if [ "$K8S_DRIVER" != "tmc" ] && [ "$K8S_DRIVER" != "gke" ]; then
    if [ "$DOCKER_USER" = "" ] || [ "$DOCKER_SERVER" = "" ] || [ "$DOCKER_PASSWORD" = "" ]; then
        echo "DOCKER_SERVER, DOCKER_USER, DOCKER_PASSWORD, DOCKER_EMAIL is required" >&2
        exit 1
    fi
fi

if [ "$USE_PRO" = "" ]; then
    USE_PRO=false
fi

YAML_PATH=$(realpath $SCDIR/yaml)

echo "YAML_PATH=$YAML_PATH"

set -e
if [ "$K8S_DRIVER" != "tmc" ] && [ "$K8S_DRIVER" != "gke" ]; then

    sh "$SCDIR/load-image.sh" "springcloud/spring-cloud-dataflow-composed-task-runner:$DATAFLOW_VERSION" true

    if [ "$USE_PRO" = "true" ]; then
        sh "$SCDIR/load-image.sh" "spring-scdf-docker-dev-local.usw1.packages.broadcom.com/p-scdf-for-kubernetes/scdf-pro-server:$DATAFLOW_PRO_VERSION" true
        sh "$SCDIR/load-image.sh" "spring-scdf-docker-dev-local.usw1.packages.broadcom.com/p-scdf-for-kubernetes/scdf-pro-skipper:$DATAFLOW_PRO_VERSION" true
    else
        sh "$SCDIR/load-image.sh" "springcloud/spring-cloud-skipper-server:$SKIPPER_VERSION" true
        sh "$SCDIR/load-image.sh" "springcloud/spring-cloud-dataflow-server:$DATAFLOW_VERSION" true
    fi
fi

pushd "$PARENT" >/dev/null

kubectl apply --namespace "$NS" -f "$YAML_PATH/datasource-config-$DATABASE.yaml"

kubectl apply --namespace "$NS" -f "$YAML_PATH/server-config.yaml"

# Deploy Spring Cloud Skipper
echo "Deploying Skipper $SKIPPER_VERSION for $BROKER and $DATABASE"
kubectl apply --namespace "$NS" -f "$YAML_PATH/skipper-config-$BROKER.yaml"
kubectl apply --namespace "$NS" -f "$YAML_PATH/skipper-svc.yaml"

cat "$YAML_PATH/skipper-deployment.yaml" | envsubst '$DATAFLOW_VERSION,$SKIPPER_VERSION,$DATABASE' | kubectl apply --namespace "$NS" -f -
kubectl --namespace "$NS" set image deployments/skipper skipper="springcloud/spring-cloud-skipper-server:$SKIPPER_VERSION"
kubectl --namespace "$NS" set image deployments/scdf-server scdf-server=$SCDF_SERVER_IMAGE

if [ "$USE_PRO" = "true" ]; then
    echo "Deploying Data Flow Server Pro $DATAFLOW_PRO_VERSION for $BROKER and $DATABASE"
    SCDF_SERVER_IMAGE="spring-scdf-docker-dev-local.usw1.packages.broadcom.com/p-scdf-for-kubernetes/scdf-pro-server:$DATAFLOW_PRO_VERSION"
#    SCDF_SKIPPER_IMAGE="spring-scdf-docker-dev-local.usw1.packages.broadcom.com/p-scdf-for-kubernetes/scdf-pro-skipper:$DATAFLOW_PRO_VERSION"
#    cat "$YAML_PATH/server-deployment-pro.yaml" | envsubst '$DATAFLOW_VERSION,$DATAFLOW_PRO_VERSION,$DATABASE' | kubectl apply --namespace "$NS" -f -
    cat "$YAML_PATH/server-deployment-pro.yaml" | envsubst '$DATAFLOW_VERSION,$DATAFLOW_PRO_VERSION,$DATABASE' | kubectl apply --namespace "$NS" -f -
    kubectl --namespace "$NS" set image deployments/scdf-server scdf-server=$SCDF_SERVER_IMAGE
else
    echo "Deploying Data Flow Server $DATAFLOW_VERSION for $BROKER and $DATABASE"
    cat "$YAML_PATH/server-deployment.yaml" | envsubst '$DATAFLOW_VERSION,$DATAFLOW_VERSION,$DATABASE' | kubectl apply --namespace "$NS" -f -
    SCDF_SERVER_IMAGE="springcloud/spring-cloud-dataflow-server:$DATAFLOW_VERSION"
    kubectl --namespace "$NS" set image deployments/scdf-server scdf-server=$SCDF_SERVER_IMAGE
fi

kubectl apply --namespace "$NS" -f "$YAML_PATH/server-svc.yaml"

echo "Waiting for skipper"
kubectl rollout status deployment --namespace "$NS" skipper
echo "Waiting for dataflow"
kubectl rollout status deployment --namespace "$NS" scdf-server
popd >/dev/null
