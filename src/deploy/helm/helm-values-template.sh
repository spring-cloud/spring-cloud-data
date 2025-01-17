#!/usr/bin/env bash
if [ -z "$BASH_VERSION" ]; then
    echo "This script requires Bash. Use: bash $0 $*"
    exit 1
fi
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")

if [ "$SCDF_TYPE" = "" ]; then
    echo "SCDF_TYPE must be configured"
    exit 1
fi
echo "Copying scdf-$SCDF_TYPE-values.yml to ./scdf-helm-values.yml"
cp "$SCDIR/scdf-${SCDF_TYPE}-values.yml" ./scdf-helm-values.yml
