#!/usr/bin/env bash
if [ -n "$BASH_SOURCE" ]; then
  SCDIR="$(readlink -f "${BASH_SOURCE[0]}")"
elif [ -n "$ZSH_VERSION" ]; then
  setopt function_argzero
  SCDIR="${(%):-%N}"
elif eval '[[ -n ${.sh.file} ]]' 2>/dev/null; then
  eval 'SCDIR=${.sh.file}'
else
  echo 1>&2 "Unsupported shell. Please use bash, ksh93 or zsh."
  exit 2
fi
SCDIR="$(dirname "$SCDIR")"

ROOTDIR=$(realpath "$SCDIR/../../..")
PRODIR=$(realpath "$ROOTDIR/../scdf-pro")
./mvnw -o -am -pl :spring-cloud-starter-dataflow-server install -DskipTests
pushd "$PRODIR" > /dev/null || exit
    ./mvnw -o -am -pl :scdf-pro-server clean install -DskipTests
    ./mvnw -o -pl :scdf-pro-server spring-boot:build-image -DskipTests
popd > /dev/null || exit
