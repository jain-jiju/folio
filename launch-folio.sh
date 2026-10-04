#!/bin/bash
# Launches Folio - Personal Finance Analyser.
# Self-locates its own folder so it works no matter where this script is called from
# (double-click, app launcher, symlink, etc.) - always runs with the project folder
# as the working directory so finance_analyser.db / config.properties / profile_pic.*
# are found in the same place every time.

DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
cd "$DIR" || exit 1
exec mvn -q javafx:run
