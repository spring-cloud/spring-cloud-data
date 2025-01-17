#!/usr/bin/env bash
bold="\033[1m"
dim="\033[2m"
end="\033[0m"
if [ -z "$BASH_VERSION" ]; then
    echo "This script requires Bash. Use: bash $0 $*"
    exit 1
fi
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
start_time=$(date +%s)
K8S=$(realpath $SCDIR/../kubernetes)
if [ ! -d "$K8S" ]; then
  K8S=$(realpath $SCDIR/../../kubernetes)
fi

$SCDIR/prepare-local-namespace.sh prometheus prometheus
kubectl create serviceaccount prometheus-rsocket-proxy --namespace prometheus

kubectl create --namespace prometheus -f $K8S/prometheus/
kubectl create --namespace prometheus -f $K8S/prometheus-proxy/
kubectl create --namespace prometheus -f $K8S/grafana/

kubectl rollout status deployment --namespace prometheus grafana
kubectl rollout status deployment --namespace prometheus prometheus
kubectl rollout status deployment --namespace prometheus prometheus-rsocket-proxy
GRAFANA_HOST=$(kubectl get --namespace prometheus services grafana | grep -F grafana | awk '{ print $3 }')
echo "Set dashboard url=$GRAFANA_HOST:3000"
export GRAFANA_URL="http://$GRAFANA_HOST:3000"
yq '.global.management.metrics.export.prometheus.enabled=true' -i ./scdf-helm-values.yml
yq '.configuration.metrics.dashboard.url = strenv(GRAFANA_URL)' -i ./scdf-helm-values.yml
yq '.global.management.metrics.export.prometheus.rsocket.enabled = true' -i ./scdf-helm-values.yml
yq '.global.management.defaults.metrics.export.enabled = true' -i ./scdf-helm-values.yml

$SCDIR/configure-prometheus-proxy.sh prometheus-rsocket-proxy.prometheus 7001

end_time=$(date +%s)
elapsed=$((end_time - start_time))
echo -e "Deployed Prometheus, Prometheus proxy and Grafana in ${bold}$elapsed${end} seconds"
