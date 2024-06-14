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

(return 0 2>/dev/null) && sourced=1 || sourced=0
if [ "$sourced" = "0" ]; then
  echo "This script must be invoked using: source $0 $*"
  exit 1
fi
export K8S_DRIVER=tmc
if [ "$1" = "" ]; then
  echo "Expected parameter providing TMC cluster"
  return 2
fi
export TMC_CLUSTER="$1"
echo "Connecting to $TMC_CLUSTER"
tmc cluster auth kubeconfig get $TMC_CLUSTER > $HOME/.kube/config
export KUBECONFIG=$HOME/.kube/config
shift
if [ "$1" != "" ]; then
  export NS=$1
  shift
fi
if [ "$NS" = "" ]; then
  export NS=scdf
fi
echo "Namespace: $NS"
source $SCDIR/set-ver.sh
