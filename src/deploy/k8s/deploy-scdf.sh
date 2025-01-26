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
K8S=$(realpath $SCDIR/../kubernetes)
if [ ! -d "$K8S" ]; then
  K8S=$(realpath $SCDIR/../../kubernetes)
fi
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
    echo "Creating namespace $NS"
    kubectl create namespace "$NS"
else
    echo "Namespace $NS exists"
fi
if [ "$K8S_DRIVER" != "tmc" ] && [ "$K8S_DRIVER" != "gke" ]; then
    if [ "$DOCKER_USER" = "" ] || [ "$DOCKER_SERVER" = "" ] || [ "$DOCKER_PASSWORD" = "" ]; then
        echo "DOCKER_SERVER, DOCKER_USER, DOCKER_PASSWORD, DOCKER_EMAIL is required" >&2
        exit 1
    fi
    kubectl create secret docker-registry registry-key --namespace "$NS" --docker-server=$DOCKER_SERVER --docker-username=$DOCKER_USER --docker-password=$DOCKER_PASSWORD --docker-email=$DOCKER_EMAIL
    kubectl patch serviceaccount default -p '{"imagePullSecrets": [{"name": "registry-key"}]}' --namespace "$NS"
fi

if [ "$USE_PRO" = "" ]; then
    USE_PRO=false
fi

if [ "$DATAFLOW_VERSION" = "" ]; then
    DATAFLOW_VERSION=3.0.0-SNAPSHOT
fi

if [ "$SKIPPER_VERSION" = "" ]; then
    SKIPPER_VERSION=3.0.0-SNAPSHOT
fi

YAML_PATH=$(realpath $SCDIR/yaml)

echo "YAML_PATH=$YAML_PATH"

set -e
if [ "$K8S_DRIVER" != "tmc" ] && [ "$K8S_DRIVER" != "gke" ]; then
    # TODO get version from yaml spec.template.spec.containers[0].image
    sh "$SCDIR/load-image.sh" "busybox:1"
    case $DATABASE in
    "mysql57")
        # TODO get version from yaml spec.template.spec.containers[0].image
        sh "$SCDIR/load-image.sh" "mysql:5.7"
        ;;
    "mariadb")
        # TODO get version from yaml spec.template.spec.containers[0].image
        sh "$SCDIR/load-image.sh" "mariadb:10.6"
        ;;
    "postgresql")
        # TODO get version from yaml spec.template.spec.containers[0].image
        sh "$SCDIR/load-image.sh" "postgres:14"
        ;;
    *)
        echo "DATABASE=$DATABASE not supported"
        ;;
    esac
    case $BROKER in
    "kafka")
        # TODO get version from yaml spec.template.spec.containers[0].image
        sh "$SCDIR/load-image.sh" "confluentinc/cp-kafka:5.5.2"
        sh "$SCDIR/load-image.sh" "confluentinc/cp-zookeeper:5.5.2"
        ;;
    "rabbit" | "rabbitmq")
        # TODO get version from yaml spec.template.spec.containers[0].image
        sh "$SCDIR/load-image.sh" "rabbitmq:3.8-management"
        ;;
    *)
        echo "BROKER=$BROKER not supported"
        ;;
    esac

    sh "$SCDIR/load-image.sh" "springcloud/spring-cloud-dataflow-composed-task-runner:$DATAFLOW_VERSION" true


    if [ "$USE_PRO" = "true" ]; then
        sh "$SCDIR/load-image.sh" "spring-scdf-docker-dev-local.usw1.packages.broadcom.com/p-scdf-for-kubernetes/scdf-pro-server:$DATAFLOW_PRO_VERSION" true
#        if [[ "$DATAFLOW_PRO_VERSION" == *"1.6"* ]]; then
#            sh "$SCDIR/load-image.sh" "spring-scdf-docker-dev-local.usw1.packages.broadcom.com/p-scdf-for-kubernetes/scdf-pro-skipper:$DATAFLOW_PRO_VERSION" true
#
#        else
            sh "$SCDIR/load-image.sh" "springcloud/spring-cloud-skipper-server:$SKIPPER_VERSION" true
#        fi
    else
        sh "$SCDIR/load-image.sh" "springcloud/spring-cloud-skipper-server:$SKIPPER_VERSION" true
        sh "$SCDIR/load-image.sh" "springcloud/spring-cloud-dataflow-server:$DATAFLOW_VERSION" true
    fi
fi

pushd "$PARENT" >/dev/null

case $BROKER in
"kafka")

    kubectl create --namespace "$NS" -f $K8S/kafka/
    ;;
"rabbit" | "rabbitmq")
    kubectl create --namespace "$NS" -f $K8S/rabbitmq/
    ;;
*)
    echo "BROKER=$BROKER not supported"
    ;;
esac
kubectl create --namespace "$NS" -f $K8S/$DATABASE/


if [ "$PROMETHEUS" = "true" ] || [ "$METRICS" = "prometheus" ]; then
    echo "Loading Prometheus and Grafana"
    if [ "$K8S_DRIVER" != "tmc" ] && [ "$K8S_DRIVER" != "gke" ]; then
        sh "$SCDIR/load-image.sh" "springcloud/spring-cloud-dataflow-grafana-prometheus:$DATAFLOW_VERSION" false
        sh "$SCDIR/load-image.sh" "prom/prometheus:v2.37.8"
        sh "$SCDIR/load-image.sh" "micrometermetrics/prometheus-rsocket-proxy:2.0.0-M4"
    fi
    set +e
    kubectl create --namespace "$NS" serviceaccount prometheus-rsocket-proxy
    kubectl create --namespace "$NS" serviceaccount prometheus
    kubectl create --namespace "$NS" clusterrolebinding prometheus --clusterrole prometheus --user=prometheus
    kubectl create --namespace "$NS" clusterrolebinding prometheus-rsocket-proxy --clusterrole cluster-admin --user=prometheus-rsocket-proxy
    kubectl create --namespace "$NS" -f $K8S/prometheus/
    kubectl create --namespace "$NS" -f $K8S/prometheus-proxy/
    kubectl create --namespace "$NS" -f $K8S/grafana/
fi

# Deploy Spring Cloud Dataflow
set +e
kubectl create --namespace "$NS" -f $K8S/server/server-roles.yaml
kubectl create --namespace "$NS" -f $K8S/server/server-rolebinding.yaml
kubectl create --namespace "$NS" -f $K8S/server/service-account.yaml

kubectl create --namespace "$NS" -f "$YAML_PATH/datasource-config-$DATABASE.yaml"

kubectl apply --namespace "$NS" -f "$YAML_PATH/server-config.yaml"



kubectl create --namespace "$NS" clusterrolebinding scdftestrole --clusterrole cluster-admin --user=system:serviceaccount:default:scdf-sa

kubectl apply --namespace "$NS" -f "$YAML_PATH/skipper-config-$BROKER.yaml"
cat "$YAML_PATH/skipper-deployment.yaml" | envsubst '$DATAFLOW_VERSION,$SKIPPER_VERSION,$DATABASE' | kubectl create --namespace "$NS" -f -
kubectl create --namespace "$NS" -f "$YAML_PATH/skipper-svc.yaml"

if [ "$USE_PRO" = "true" ]; then
    echo "Deploying Skipper Pro $DATAFLOW_PRO_VERSION for $BROKER and $DATABASE"
    # kubectl apply --namespace "$NS" -f "$YAML_PATH/skipper-config-$BROKER.yaml"
    # cat "$YAML_PATH/skipper-deployment-pro.yaml" | envsubst '$DATAFLOW_PRO_VERSION,$DATABASE' | kubectl create --namespace "$NS" -f -
    echo "Deploying Data Flow Server Pro $DATAFLOW_PRO_VERSION and $DATABASE"
    cat "$YAML_PATH/server-deployment-pro.yaml" | envsubst '$DATAFLOW_VERSION,$DATAFLOW_PRO_VERSION,$DATABASE' | kubectl create --namespace "$NS" -f -
else
    # Deploy Spring Cloud Skipper
    echo "Deploying Skipper $SKIPPER_VERSION for $BROKER and $DATABASE"
    echo "Deploying Data Flow Server $DATAFLOW_VERSION for $DATABASE"
    cat "$YAML_PATH/server-deployment.yaml" | envsubst '$DATAFLOW_VERSION,$DATABASE' | kubectl create --namespace "$NS" -f -
fi

kubectl create --namespace "$NS" -f "$YAML_PATH/server-svc.yaml"

popd >/dev/null
