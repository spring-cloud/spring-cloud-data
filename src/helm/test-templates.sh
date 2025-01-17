#!/bin/bash
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
pushd "$SCDIR" > /dev/null || exit
helm lint --debug scdf/ --values scdf/test-values.yaml
rm -rf ./output/*
helm template --debug scdf/ --values scdf/test-values.yaml --output-dir ./output
popd > /dev/null || exit
