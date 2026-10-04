FROM mcr.microsoft.com/devcontainers/java:17

# Install a virtual display (Xvfb), a VNC server (x11vnc), noVNC (browser-based VNC
# client) and websockify (bridges noVNC's websocket to the VNC server's TCP port).
RUN apt-get update && apt-get install -y --no-install-recommends \
        xvfb \
        x11vnc \
        novnc \
        websockify \
        fluxbox \
        maven \
    && rm -rf /var/lib/apt/lists/*

# novnc on Debian/Ubuntu is installed at /usr/share/novnc, but its launcher script
# is usually not symlinked onto PATH - this makes `novnc_proxy` findable.
RUN ln -sf /usr/share/novnc/utils/novnc_proxy /usr/local/bin/novnc_proxy || true
