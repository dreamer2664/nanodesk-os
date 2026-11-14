#!/usr/bin/env bash
# NanoDesk OS Installer for Debian (1.9GB RAM / Low-Resource Friendly)
set -e

echo "============================================"
echo "  Installing NanoDesk OS (Ultra-Light)      "
echo "============================================"

export DEBIAN_FRONTEND=noninteractive

apt-get update -qq
apt-get install -y --no-install-recommends \
  openbox tint2 pcmanfm lxterminal rofi \
  chromium xdotool scrot xclip \
  xvfb x11-utils x11-xserver-utils \
  python3 python3-tk git curl ca-certificates

# Install binaries
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
install -m 755 "$SCRIPT_DIR/agent-bridge.py" /usr/local/bin/nanodesk-bridge
install -m 755 "$SCRIPT_DIR/overlay_window.py" /usr/local/bin/nanodesk-overlay
install -m 755 "$SCRIPT_DIR/nanodesk-chrome" /usr/local/bin/nanodesk-chrome
install -m 755 "$SCRIPT_DIR/nanodesk-git" /usr/local/bin/nanodesk-git

# Desktop entries for Tint2 launcher
mkdir -p /usr/share/applications
cat > /usr/share/applications/nanodesk-chrome.desktop << 'EOF'
[Desktop Entry]
Name=Chrome
Exec=nanodesk-chrome
Icon=chromium
Type=Application
Categories=Network;WebBrowser;
EOF

cat > /usr/share/applications/nanodesk-git.desktop << 'EOF'
[Desktop Entry]
Name=GitHub Sync
Exec=lxterminal -e nanodesk-git
Icon=git
Type=Application
Categories=Development;
EOF

cat > /usr/share/applications/nanodesk-terminal.desktop << 'EOF'
[Desktop Entry]
Name=Terminal
Exec=lxterminal
Icon=utilities-terminal
Type=Application
Categories=System;TerminalEmulator;
EOF

# Openbox & Tint2 configs
mkdir -p ~/.config/openbox ~/.config/tint2
cp "$SCRIPT_DIR/configs/openbox/autostart" ~/.config/openbox/autostart
chmod +x ~/.config/openbox/autostart
cp "$SCRIPT_DIR/configs/tint2/tint2rc" ~/.config/tint2/tint2rc

# Create start-nanodesk helper
cat > /usr/local/bin/start-nanodesk << 'EOF'
#!/usr/bin/env bash
export DISPLAY=${DISPLAY:-:0}
if ! pgrep -x Xvfb >/dev/null && ! xset q >/dev/null 2>&1; then
  Xvfb "$DISPLAY" -screen 0 1280x720x24 -nolisten tcp &
  sleep 1
fi
openbox-session &
EOF
chmod +x /usr/local/bin/start-nanodesk

echo "============================================"
echo "  NanoDesk OS installed successfully!       "
echo "  Run: start-nanodesk                       "
echo "  Agent-Bridge listens on port 8765         "
echo "============================================"
