#!/bin/bash
if [ -z "$BASH_VERSION" ]; then
    echo "This script requires Bash. Use: bash $0 $*"
    exit 0
fi
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
set -e
function dataflow_post() {
    echo "Invoking POST $1 >> $2"
    result=$(curl -s -d "$1" -X POST "$2")
    rc=$?
    if ((rc != 0 )); then
        echo "$rc : $result"
        echo ""
        exit $rc
    fi
}
if [ "$BROKER" = "" ]; then
    BROKER=rabbitmq
fi
if [ "$DATAFLOW_IP" = "" ]; then
    DATAFLOW_IP=http://localhost:9393
fi
case $BROKER in
"" | "kafka")
    export BROKER=kafka
    ;;
"rabbit" | "rabbitmq")
    export BROKER=rabbitmq
    ;;
*)
    echo "BROKER=$BROKER not supported"
    ;;
esac

if [ "$BROKER" = "rabbitmq" ]; then
    BROKER_NAME=rabbit
else
    BROKER_NAME=$BROKER
fi
if [ "$STREAM_APPS_RT_VERSION" = "" ]; then
    export STREAM_APPS_RT_VERSION=2024.0.0
fi
echo "STREAM_APPS_RT_VERSION=$STREAM_APPS_RT_VERSION"
TYPE=maven

if [[ "$STREAM_APPS_RT_VERSION" = *"-SNAPSHOT"* ]]; then
    STREAM_APPS_DL_VERSION=$STREAM_APPS_RT_VERSION
    META_DATA="https://repo.spring.io/snapshot/org/springframework/cloud/stream/app/stream-applications-descriptor/${STREAM_APPS_RT_VERSION}/maven-metadata.xml"
    echo "Downloading $META_DATA"
    curl -o maven-metadata.xml -s $META_DATA
    DL_TS=$(xmllint --xpath "/metadata/versioning/snapshot/timestamp/text()" maven-metadata.xml | sed 's/\.//')
    STREAM_APPS_DL_VERSION=$(xmllint --xpath "/metadata/versioning/snapshotVersions/snapshotVersion[extension/text() = 'pom' and updated/text() = '$DL_TS']/value/text()" maven-metadata.xml)
    DESCRIPTORS="https://repo.spring.io/snapshot/org/springframework/cloud/stream/app/stream-applications-descriptor/${STREAM_APPS_RT_VERSION}/stream-applications-descriptor-${STREAM_APPS_DL_VERSION}.stream-apps-${BROKER_NAME}-${TYPE}"
else
    REL_TYPE=
    if [[ "$STREAM_APPS_RT_VERSION" = *"-M"* ]] || [[ "$STREAM_APPS_RT_VERSION" = *"-RC"* ]]; then
        REL_TYPE=milestone
    fi
    if [ "$REL_TYPE" != "" ]; then
        DESCRIPTORS="https://repo.spring.io/$REL_TYPE/org/springframework/cloud/stream/app/stream-applications-descriptor/${STREAM_APPS_RT_VERSION}/stream-applications-descriptor-${STREAM_APPS_RT_VERSION}.stream-apps-${BROKER_NAME}-${TYPE}"
    else
        DESCRIPTORS="https://repo.maven.apache.org/maven2/org/springframework/cloud/stream/app/stream-applications-descriptor/${STREAM_APPS_RT_VERSION}/stream-applications-descriptor-${STREAM_APPS_RT_VERSION}.stream-apps-${BROKER_NAME}-${TYPE}"
    fi
fi
echo "DATAFLOW_IP=$DATAFLOW_IP"
dataflow_post "uri=$DESCRIPTORS" "$DATAFLOW_IP/apps"

dataflow_post "uri=maven:io.spring:timestamp-task:3.0.0" "$DATAFLOW_IP/apps/task/timestamp"
dataflow_post "uri=maven:io.spring:timestamp-batch-task:3.0.0" "$DATAFLOW_IP/apps/task/timestamp-batch"

