# NanoDesk OS + AgentLink

An ultra-lightweight, Debian-based Linux desktop environment (**NanoDesk OS**) paired with a native Android companion app (**AgentLink**) for full remote screen viewing, touch-to-mouse control, on-screen overlay messaging, and quick productivity tools.

---

## What Was Built

### 1. NanoDesk OS (`distro/`)
Designed specifically for low-RAM Debian environments (tested for **1.9 GB RAM** agent VMs/containers):
- **Ultra-Light Stack (~180–250 MB idle RAM)**: `Openbox` + `Tint2` (Catppuccin Mocha dark theme) + `PCManFM` + `LXTerminal` + `Xvfb` (for headless VMs).
- **Easy Chrome Access (`nanodesk-chrome`)**: One-click Chromium launcher on the bottom dock with `--disable-dev-shm-usage` and container-safe flags so it runs reliably in low-RAM environments.
- **Easy GitHub Push / Pull (`nanodesk-git`)**: One-click GitHub Sync helper on the dock (Pull, Commit & Push, Status, Clone, and PAT credential setup).
- **Agent-Bridge Daemon (`agent-bridge.py`)**: A lightweight Python 3 HTTP server (port `8765`, ~20 MB RAM) using only Python stdlib + `xdotool` + `scrot` + `xclip` + `python3-tk`:
  - `GET /api/status` — Live RAM, CPU load, hostname, active window
  - `GET /api/screenshot` & `GET /stream.mjpg` — Live screen streaming
  - `POST /api/input/mouse` — Remote mouse move, left/right click, scroll
  - `POST /api/input/key` — Remote keyboard typing & key combos (`ctrl+c`, `alt+Tab`, etc.)
  - `POST /api/overlay` — Displays a non-blocking, always-on-top **floating overlay banner** on the Linux screen (`info`, `success`, `warning`, `urgent`)
  - `GET/POST /api/clipboard` — Two-way clipboard sync
  - `POST /api/action` — Quick-launch Chrome, Terminal, GitHub Sync, or Files
  - `POST /api/exec` — Run shell commands remotely
  - `POST /api/upload` — Upload files from Android to `~/Desktop/Inbox/`

### 2. AgentLink Android APK (`android/`)
A native, dark-themed Android app (`AgentLink-v1.0.0.apk`, Android 7.0+ / API 24–33):
- **Full Screen View & Touch Control**: Live screen view with tap = left click, long-press = right click, drag = mouse move.
- **Screen Overlay Messages**: Send custom floating banners directly onto the Linux desktop screen.
- **Keyboard & Special Keys**: Type text or send `Return`, `Escape`, `Tab`, `Ctrl+C`, `Ctrl+V`, `Alt+Tab`, `Super`.
- **Quick Launchers**: One-tap **Chrome** and **Git Sync** buttons.
- **Clipboard Sync**: Push phone clipboard to Linux or pull Linux clipboard to phone.
- **Remote Shell**: Run commands on the Debian machine and view output.

---

## Quick Start

### On Debian (Agent / Server)
See [`AGENT_INSTRUCTIONS.md`](AGENT_INSTRUCTIONS.md) or run:
```bash
git clone https://github.com/dreamer2664/nanodesk-os.git
cd nanodesk-os/distro
sudo bash install-distro.sh
start-nanodesk
```

### On Android
1. Download `AgentLink-v1.0.0.apk` from the [GitHub Releases](https://github.com/dreamer2664/nanodesk-os/releases) page.
2. Install the APK on your Android device.
3. Open **AgentLink**, tap **Connect**, and enter your Debian machine's IP address and port `8765`.
