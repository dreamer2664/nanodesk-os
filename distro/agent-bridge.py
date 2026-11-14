#!/usr/bin/env python3
"""
NanoDesk Agent-Bridge Server
Ultra-lightweight HTTP + MJPEG + Remote Control + Overlay daemon for Debian.
Uses only Python stdlib + system tools (xdotool, scrot, xclip) -> ~20 MB RAM.
"""
import os
import sys
import json
import time
import base64
import socket
import subprocess
import tempfile
from http.server import ThreadingHTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs

PORT = int(os.environ.get("NANODESK_PORT", "8765"))
AUTH_TOKEN = os.environ.get("NANODESK_TOKEN", "")
DISPLAY = os.environ.get("DISPLAY", ":0")
INBOX_DIR = os.path.expanduser("~/Desktop/Inbox")
OVERLAY_SCRIPT = "/usr/local/bin/nanodesk-overlay"

os.makedirs(INBOX_DIR, exist_ok=True)


def run_x(cmd, timeout=5):
    env = os.environ.copy()
    env["DISPLAY"] = DISPLAY
    return subprocess.run(cmd, env=env, capture_output=True, text=True, timeout=timeout)


def get_screen_size():
    try:
        res = run_x(["xdotool", "getdisplaygeometry"])
        w, h = res.stdout.strip().split()
        return int(w), int(h)
    except Exception:
        return 1280, 720


def capture_jpeg(quality=65):
    tmp = tempfile.NamedTemporaryFile(suffix=".jpg", delete=False)
    tmp_path = tmp.name
    tmp.close()
    try:
        env = os.environ.copy()
        env["DISPLAY"] = DISPLAY
        subprocess.run(
            ["scrot", "-o", "-q", str(quality), tmp_path],
            env=env,
            capture_output=True,
            timeout=5,
        )
        with open(tmp_path, "rb") as f:
            return f.read()
    finally:
        if os.path.exists(tmp_path):
            os.remove(tmp_path)


def get_system_stats():
    mem_total = 0
    mem_avail = 0
    try:
        with open("/proc/meminfo") as f:
            for line in f:
                if line.startswith("MemTotal:"):
                    mem_total = int(line.split()[1]) // 1024
                elif line.startswith("MemAvailable:"):
                    mem_avail = int(line.split()[1]) // 1024
    except Exception:
        pass
    mem_used = max(0, mem_total - mem_avail)

    load = "0.00"
    try:
        with open("/proc/loadavg") as f:
            load = f.read().split()[0]
    except Exception:
        pass

    uptime_sec = 0
    try:
        with open("/proc/uptime") as f:
            uptime_sec = int(float(f.read().split()[0]))
    except Exception:
        pass

    active_win = ""
    try:
        res = run_x(["xdotool", "getactivewindow", "getwindowname"])
        active_win = res.stdout.strip()
    except Exception:
        pass

    w, h = get_screen_size()
    return {
        "hostname": socket.gethostname(),
        "ram_used_mb": mem_used,
        "ram_total_mb": mem_total,
        "load_1m": load,
        "uptime_sec": uptime_sec,
        "screen_w": w,
        "screen_h": h,
        "active_window": active_win,
        "display": DISPLAY,
    }


class BridgeHandler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def log_message(self, format, *args):
        pass  # Keep logs quiet for performance

    def _check_auth(self):
        if not AUTH_TOKEN:
            return True
        auth = self.headers.get("Authorization", "")
        if auth == f"Bearer {AUTH_TOKEN}":
            return True
        qs = parse_qs(urlparse(self.path).query)
        if qs.get("token", [""])[0] == AUTH_TOKEN:
            return True
        self._send_json(401, {"error": "Unauthorized"})
        return False

    def _send_json(self, code, data):
        body = json.dumps(data).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Access-Control-Allow-Origin", "*")
        self.end_headers()
        self.wfile.write(body)

    def _read_json(self):
        length = int(self.headers.get("Content-Length", "0"))
        if length <= 0:
            return {}
        raw = self.rfile.read(length)
        return json.loads(raw.decode("utf-8"))

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization")
        self.end_headers()

    def do_GET(self):
        if not self._check_auth():
            return
        parsed = urlparse(self.path)
        path = parsed.path

        if path == "/api/status":
            self._send_json(200, get_system_stats())

        elif path == "/api/screenshot":
            try:
                jpg = capture_jpeg(quality=70)
                self.send_response(200)
                self.send_header("Content-Type", "image/jpeg")
                self.send_header("Content-Length", str(len(jpg)))
                self.send_header("Cache-Control", "no-store")
                self.end_headers()
                self.wfile.write(jpg)
            except Exception as e:
                self._send_json(500, {"error": str(e)})

        elif path == "/stream.mjpg":
            self.send_response(200)
            self.send_header("Content-Type", "multipart/x-mixed-replace; boundary=frame")
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            try:
                while True:
                    jpg = capture_jpeg(quality=60)
                    self.wfile.write(b"--frame\r\n")
                    self.wfile.write(b"Content-Type: image/jpeg\r\n")
                    self.wfile.write(f"Content-Length: {len(jpg)}\r\n\r\n".encode())
                    self.wfile.write(jpg)
                    self.wfile.write(b"\r\n")
                    self.wfile.flush()
                    time.sleep(0.12)  # ~8 FPS, super light on CPU/RAM
            except Exception:
                pass

        elif path == "/api/clipboard":
            res = run_x(["xclip", "-o", "-selection", "clipboard"])
            self._send_json(200, {"text": res.stdout})

        else:
            self._send_json(404, {"error": "Not found"})

    def do_POST(self):
        if not self._check_auth():
            return
        parsed = urlparse(self.path)
        path = parsed.path

        try:
            data = self._read_json()
        except Exception as e:
            self._send_json(400, {"error": f"Invalid JSON: {e}"})
            return

        if path == "/api/input/mouse":
            action = data.get("action", "click")
            nx = float(data.get("x", 0.5))
            ny = float(data.get("y", 0.5))
            button = str(data.get("button", 1))
            w, h = get_screen_size()
            px = max(0, min(w - 1, int(nx * w)))
            py = max(0, min(h - 1, int(ny * h)))

            if action == "move":
                run_x(["xdotool", "mousemove", str(px), str(py)])
            elif action == "click":
                run_x(["xdotool", "mousemove", str(px), str(py), "click", button])
            elif action == "down":
                run_x(["xdotool", "mousemove", str(px), str(py), "mousedown", button])
            elif action == "up":
                run_x(["xdotool", "mousemove", str(px), str(py), "mouseup", button])
            elif action == "scroll":
                direction = data.get("direction", "down")
                btn = "5" if direction == "down" else "4"
                run_x(["xdotool", "mousemove", str(px), str(py), "click", "--repeat", "3", btn])
            self._send_json(200, {"ok": True, "x": px, "y": py})

        elif path == "/api/input/key":
            text = data.get("text")
            key = data.get("key")
            if text:
                run_x(["xdotool", "type", "--delay", "12", text])
            if key:
                run_x(["xdotool", "key", key])
            self._send_json(200, {"ok": True})

        elif path == "/api/overlay":
            payload = json.dumps({
                "title": data.get("title", "AgentLink"),
                "message": data.get("message", ""),
                "duration": int(data.get("duration", 8)),
                "level": data.get("level", "info"),
            })
            env = os.environ.copy()
            env["DISPLAY"] = DISPLAY
            script = OVERLAY_SCRIPT if os.path.exists(OVERLAY_SCRIPT) else os.path.join(os.path.dirname(__file__), "overlay_window.py")
            subprocess.Popen(["python3", script, payload], env=env)
            self._send_json(200, {"ok": True})

        elif path == "/api/clipboard":
            text = data.get("text", "")
            env = os.environ.copy()
            env["DISPLAY"] = DISPLAY
            p = subprocess.Popen(["xclip", "-i", "-selection", "clipboard"], stdin=subprocess.PIPE, env=env)
            p.communicate(input=text.encode("utf-8"))
            self._send_json(200, {"ok": True})

        elif path == "/api/action":
            act = data.get("action", "")
            env = os.environ.copy()
            env["DISPLAY"] = DISPLAY
            if act == "chrome":
                subprocess.Popen(["nanodesk-chrome"], env=env)
            elif act == "terminal":
                subprocess.Popen(["x-terminal-emulator"], env=env)
            elif act == "gitsync":
                subprocess.Popen(["x-terminal-emulator", "-e", "nanodesk-git"], env=env)
            elif act == "files":
                subprocess.Popen(["pcmanfm"], env=env)
            else:
                self._send_json(400, {"error": f"Unknown action: {act}"})
                return
            self._send_json(200, {"ok": True, "action": act})

        elif path == "/api/exec":
            cmd = data.get("cmd", "")
            if not cmd:
                self._send_json(400, {"error": "Missing cmd"})
                return
            try:
                res = subprocess.run(cmd, shell=True, capture_output=True, text=True, timeout=15)
                self._send_json(200, {
                    "returncode": res.returncode,
                    "stdout": res.stdout[-4000:],
                    "stderr": res.stderr[-2000:],
                })
            except Exception as e:
                self._send_json(500, {"error": str(e)})

        elif path == "/api/upload":
            filename = os.path.basename(data.get("filename", "upload.bin"))
            content_b64 = data.get("content_b64", "")
            dest = os.path.join(INBOX_DIR, filename)
            with open(dest, "wb") as f:
                f.write(base64.b64decode(content_b64))
            self._send_json(200, {"ok": True, "path": dest})

        else:
            self._send_json(404, {"error": "Not found"})


if __name__ == "__main__":
    server = ThreadingHTTPServer(("0.0.0.0", PORT), BridgeHandler)
    print(f"NanoDesk Agent-Bridge listening on 0.0.0.0:{PORT} (DISPLAY={DISPLAY})")
    server.serve_forever()
