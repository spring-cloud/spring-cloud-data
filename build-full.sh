#!/bin/bash
if [ -z "$BASH_VERSION" ]; then
    echo "This script requires Bash. Use: bash $0 $*"
    exit 1
fi
SCDIR=$(dirname "$(readlink -f "${BASH_SOURCE[0]}")")
pushd "$SCDIR" > /dev/null || exit 1
./mvnw -s .settings.xml install -Pfull,asciidoctordocs,restdocs -B $*
popd > /dev/null || exit 1
