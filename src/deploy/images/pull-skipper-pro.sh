#!/bin/bash
if [ "$DATAFLOW_PRO_VERSION" = "" ]; then
  DATAFLOW_PRO_VERSION=1.6.1-SNAPSHOT
fi
docker pull "dev.registry.tanzu.vmware.com/p-scdf-for-kubernetes/scdf-pro-skipper:$DATAFLOW_PRO_VERSION"
