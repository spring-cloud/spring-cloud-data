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

cat > $SCDIR/run-ctr3b.shell <<EOF
task launch --name timestamp-ctr3b --properties app.composed-task-runner.logging.level.root=debug
EOF
"$SCDIR/shell.sh" --spring.shell.commandFile=$SCDIR/run-ctr3b.shell
