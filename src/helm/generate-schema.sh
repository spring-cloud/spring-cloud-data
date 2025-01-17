#!/usr/bin/env bash
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
pushd "$SCDIR" > /dev/null || exit
helm schema --input scdf/values.yaml --output scdf/values.schema.json
popd > /dev/null || exit