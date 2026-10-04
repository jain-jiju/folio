#!/bin/bash
# One-time setup: installs a Folio app icon into your Linux application menu
# so it launches like any other installed app. Run this ONCE from inside the
# finance-analyser-java project folder:   bash install-desktop-icon.sh

set -e
DIR="$(cd "$(dirname "$(readlink -f "$0")")" && pwd)"
chmod +x "$DIR/launch-folio.sh"

APPS_DIR="$HOME/.local/share/applications"
mkdir -p "$APPS_DIR"

cat > "$APPS_DIR/folio-finance.desktop" << DESKTOP
[Desktop Entry]
Type=Application
Name=Folio
Comment=Personal Finance Analyser
Exec=$DIR/launch-folio.sh
Icon=$DIR/icon.svg
Terminal=false
Categories=Office;Finance;
DESKTOP

chmod +x "$APPS_DIR/folio-finance.desktop"

if command -v update-desktop-database >/dev/null 2>&1; then
    update-desktop-database "$APPS_DIR" 2>/dev/null || true
fi

echo ""
echo "Done! Search for 'Folio' in your application launcher (Activities / Start menu)."
echo "First launch may take a few seconds while Maven starts up."
echo "You can right-click it in the launcher to 'Add to Favorites' / pin it to your dock."
