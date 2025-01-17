#!/usr/bin/env bash
bold="\033[1m"
dim="\033[2m"
end="\033[0m"
function count_kind() {
    jq --arg kind $1 --arg name $2 '.items | .[] | select(.kind == $kind) | .metadata | select(.name == $name) | .name' | grep -c -F "$2"
}
if [ -z "$BASH_VERSION" ]; then
    echo "This script requires Bash. Use: bash $0 $*"
    exit 1
fi
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
if [ "$BROKER" = "" ]; then
    echo "BROKER must be defined"
    exit 1
fi
start_time=$(date +%s)
case $BROKER in
"kafka")
    BINDER_NAME=kafka
    ;;
"rabbit" | "rabbitmq")
    BROKER=rabbitmq
    BINDER_NAME=rabbit
    ;;
*)
    echo "Invalid broker type $1"
    exit 1
    ;;
esac
K8S=$(realpath $SCDIR/../kubernetes)
if [ ! -d "$K8S" ]; then
  K8S=$(realpath $SCDIR/../../kubernetes)
fi
$SCDIR/prepare-local-namespace.sh "$BROKER-sa" $BROKER
kubectl create --namespace $BROKER -f $K8S/$BROKER/
if [ "$BROKER" = "rabbitmq" ]; then
    kubectl rollout status deployment --namespace "rabbitmq" rabbitmq
else
    kubectl rollout status deployment --namespace "kafka" kafka-zk
    kubectl rollout status sts --namespace "kafka" kafka-broker
fi

echo "Deployed $BROKER"
export BROKER

yq '.configuration.broker.type = strenv(BINDER_NAME)' -i ./scdf-helm-values.yml

if [ "$BROKER" = "rabbitmq" ]; then
    # RABBITMQ_HOST=$(kubectl get --namespace rabbitmq services rabbitmq | grep -F rabbitmq | awk '{ print $3 }')
    export RABBITMQ_HOST='rabbitmq.rabbitmq'
    yq '.configuration.broker.rabbit.host= strenv(RABBITMQ_HOST)' -i ./scdf-helm-values.yml
    yq ".configuration.broker.rabbit.port=5672" -i ./scdf-helm-values.yml
else
    # KAFKA_HOST=$(kubectl get --namespace kafka services kafka | grep -F kafka | awk '{ print $3 }')
    export KAFKA_HOST="kafka.kafka:9092"
    yq '.configuration.broker.kafka.brokers = strenv(KAFKA_HOST)' -i ./scdf-helm-values.yml
fi
end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo -e "Deployed $BROKER in ${bold}$elapsed${end} seconds"
