# Agent Instructions: Installing NanoDesk OS on Debian (1.9 GB RAM)

These instructions are designed for an AI agent running on a Debian (Bookworm/Trixie) environment with ~1.9 GB RAM.

## 1. Clone & Install

```bash
git clone https://github.com/dreamer2664/nanodesk-os.git
cd nanodesk-os/distro
sudo bash install-distro.sh
```

## 2. Start the Desktop & Agent-Bridge

```bash
start-nanodesk
```

This starts:
- **Xvfb** (virtual X11 display `:0` at `1280x720x24`) if no physical display is present
- **Openbox** window manager + **Tint2** dark-mode panel (~180 MB total idle RAM)
- **Agent-Bridge** server on `0.0.0.0:8765`

## 3. Optional: Set an Auth Token

```bash
export NANODESK_TOKEN="your-secret-token"
NANODESK_TOKEN="your-secret-token" nanodesk-bridge &
```

## 4. Verify Running

```bash
curl -s http://localhost:8765/api/status
```

## 5. Quick CLI Tools Available

- `nanodesk-chrome` — Launches Chromium with low-RAM & container-safe flags.
- `nanodesk-git` — Interactive GitHub push/pull/status/PAT helper.
- `nanodesk-overlay '{"title":"Hello","message":"Test","level":"info","duration":5}'` — Triggers an on-screen overlay banner.
