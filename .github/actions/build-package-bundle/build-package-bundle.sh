#!/usr/bin/env bash

function check_env() {
  eval ev='$'$1
  if [ "$ev" == "" ]; then
    echo "env var $1 not defined"
    if ((sourced != 0)); then
      return 1
    else
      exit 1
    fi
  fi
}

TMP=$(mktemp -d)
if [ "$PACKAGE_BUNDLE_GENERATED" = "" ]; then
    export PACKAGE_BUNDLE_GENERATED="$TMP/generated/packagebundle"
    mkdir -p "$PACKAGE_BUNDLE_GENERATED"
fi
if [ "$IMGPKG_LOCK_GENERATED_IN" = "" ]; then
    export IMGPKG_LOCK_GENERATED_IN="$TMP/generated/imgpkgin"
    mkdir -p "$IMGPKG_LOCK_GENERATED_IN"
fi
if [ "$IMGPKG_LOCK_GENERATED_OUT" = "" ]; then
    export IMGPKG_LOCK_GENERATED_OUT="$TMP/generated/imgpkgout"
    mkdir -p "$IMGPKG_LOCK_GENERATED_OUT"
fi

check_env PACKAGE_BUNDLE_TEMPLATE
check_env SERVER_VERSION
check_env SERVER_REPOSITORY
check_env DATAFLOW_VERSION
check_env SKIPPER_VERSION
check_env PACKAGE_NAME
check_env IMGPKG_LOCK_TEMPLATE
check_env VENDIR_SRC_IN

ytt \
    -f "$PACKAGE_BUNDLE_TEMPLATE" \
    --output-files "$PACKAGE_BUNDLE_GENERATED" \
    --data-value-yaml server.version="$SERVER_VERSION" \
    --data-value-yaml server.repository="$SERVER_REPOSITORY" \
    --data-value-yaml ctr.version="$DATAFLOW_VERSION" \
    --data-value-yaml dataflow.version="$DATAFLOW_VERSION" \
    --data-value-yaml skipper.version="$SKIPPER_VERSION" \
    --data-value-yaml grafana.version="$DATAFLOW_VERSION" \
    --data-value-yaml package.name="$PACKAGE_NAME" \
    --file-mark 'config/values.yml:type=text-template' \
    --file-mark '.imgpkg/bundle.yaml:type=text-template'
ytt \
    -f "$IMGPKG_LOCK_TEMPLATE" \
    --output-files "$IMGPKG_LOCK_GENERATED_IN" \
    --data-value-yaml server.version="$SERVER_VERSION" \
    --data-value-yaml server.repository="$SERVER_REPOSITORY" \
    --data-value-yaml ctr.version="$DATAFLOW_VERSION" \
    --data-value-yaml dataflow.version="$DATAFLOW_VERSION" \
    --data-value-yaml skipper.version="$SKIPPER_VERSION" \
    --data-value-yaml grafana.version="$DATAFLOW_VERSION" \
    --file-mark '**/*.yml:type=text-template'
cp -R "$VENDIR_SRC_IN" "$PACKAGE_BUNDLE_GENERATED/config/upstream"
vendir sync --chdir "$PACKAGE_BUNDLE_GENERATED"
mkdir -p "$IMGPKG_LOCK_GENERATED_OUT"

for DIR in $(ls $IMGPKG_LOCK_GENERATED_IN); do
    ytt -f "$PACKAGE_BUNDLE_GENERATED" -f "$IMGPKG_LOCK_GENERATED_IN/$DIR" >"$IMGPKG_LOCK_GENERATED_OUT/$DIR.yml"
done

mkdir -p "$PACKAGE_BUNDLE_GENERATED/.imgpkg"
kbld \
    -f "$IMGPKG_LOCK_GENERATED_OUT" \
    --imgpkg-lock-output "$PACKAGE_BUNDLE_GENERATED/.imgpkg/images.yml"
