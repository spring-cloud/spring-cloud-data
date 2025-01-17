#!/bin/bash
(return 0 2>/dev/null) && sourced=1 || sourced=0

if [ "$NS" = "" ]; then
    echo "NS not defined" >&2
    if ((sourced != 0)); then
        return 2
    else
        exit 2
    fi
fi
EXTERNAL_IP=$(kubectl get --namespace "$NS" services scdf-server | grep -F "scdf-server" | awk '{ print $4 }')
LB_IP=$(kubectl get  --namespace "$NS" svc/scdf-server -o=jsonpath='{.status.loadBalancer.ingress[0].ip}')
if [ "$EXTERNAL_IP" = "<pending>" ]; then
    echo "The Loadbalancer is still pending"
    if ((sourced != 0)); then
        return 1
    else
        exit 1
    fi
fi
export PLATFORM_TYPE=kubernetes
if [ "$EXTERNAL_IP" != "" ]; then
  export DATAFLOW_IP=http://$EXTERNAL_IP:9393
  echo "DATAFLOW_IP=$DATAFLOW_IP"
  export DATAFLOW_URL="$DATAFLOW_IP"
  echo "DATAFLOW_URL=$DATAFLOW_IP"
else
  echo "EXTERNAL_IP not found"
  kubectl get --namespace "$NS" services scdf-server
fi
