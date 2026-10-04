#!/usr/bin/env bash
# Starts a virtual display, a lightweight window manager, a VNC server on top of
# it, noVNC's browser-facing websocket proxy, and finally the real JavaFX app -
# unmodified, same command you already run locally (mvn javafx:run).
set -e

DISPLAY_NUM=99
export DISPLAY=:${DISPLAY_NUM}

echo "Starting virtual display on :${DISPLAY_NUM}..."
Xvfb :${DISPLAY_NUM} -screen 0 1280x800x24 &
sleep 2

echo "Starting window manager..."
fluxbox &
sleep 1

echo "Starting VNC server..."
x11vnc -display :${DISPLAY_NUM} -nopw -forever -shared -quiet &
sleep 1

echo "Starting noVNC web proxy on port 6080..."
# novnc_proxy bridges a browser websocket connection (port 6080) to the VNC
# server above (default VNC port 5900).
novnc_proxy --vnc localhost:5900 --listen 6080 &
sleep 2

echo "Building and launching Folio..."
cd "$(dirname "$0")/.."
mvn clean javafx:run
