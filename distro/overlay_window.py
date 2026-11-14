#!/usr/bin/env python3
"""
NanoDesk Overlay Window
Displays a sleek, non-blocking, always-on-top message banner on the X11 screen.
"""
import sys
import json
import tkinter as tk

def show_overlay(title: str, message: str, duration: int = 8, level: str = "info"):
    colors = {
        "info": ("#1e1e2e", "#89b4fa", "#cdd6f4"),
        "success": ("#1e1e2e", "#a6e3a1", "#cdd6f4"),
        "warning": ("#1e1e2e", "#f9e2af", "#cdd6f4"),
        "urgent": ("#2b161b", "#f38ba8", "#cdd6f4"),
    }
    bg, accent, fg = colors.get(level, colors["info"])

    root = tk.Tk()
    root.overrideredirect(True)
    root.attributes("-topmost", True)
    try:
        root.attributes("-alpha", 0.94)
    except Exception:
        pass

    root.configure(bg=accent)

    inner = tk.Frame(root, bg=bg, padx=18, pady=12)
    inner.pack(padx=2, pady=2, fill="both", expand=True)

    header = tk.Frame(inner, bg=bg)
    header.pack(fill="x")

    lbl_title = tk.Label(
        header,
        text=f"📱 {title}",
        font=("Sans", 12, "bold"),
        fg=accent,
        bg=bg,
        anchor="w",
    )
    lbl_title.pack(side="left", fill="x", expand=True)

    btn_close = tk.Button(
        header,
        text="✕",
        font=("Sans", 10, "bold"),
        fg=fg,
        bg=bg,
        bd=0,
        highlightthickness=0,
        activebackground=bg,
        activeforeground=accent,
        command=root.destroy,
        cursor="hand2",
    )
    btn_close.pack(side="right")

    lbl_msg = tk.Label(
        inner,
        text=message,
        font=("Sans", 11),
        fg=fg,
        bg=bg,
        justify="left",
        wraplength=420,
        anchor="w",
    )
    lbl_msg.pack(fill="x", pady=(6, 0))

    root.update_idletasks()
    w = min(max(root.winfo_reqwidth(), 320), 480)
    h = root.winfo_reqheight()
    sw = root.winfo_screenwidth()
    x = sw - w - 24
    y = 48
    root.geometry(f"{w}x{h}+{x}+{y}")

    if duration > 0:
        root.after(duration * 1000, root.destroy)

    root.mainloop()

if __name__ == "__main__":
    if len(sys.argv) >= 2:
        try:
            data = json.loads(sys.argv[1])
            show_overlay(
                data.get("title", "AgentLink Message"),
                data.get("message", ""),
                int(data.get("duration", 8)),
                data.get("level", "info"),
            )
        except Exception:
            show_overlay("AgentLink", " ".join(sys.argv[1:]))
    else:
        show_overlay("AgentLink", "Overlay system ready.")
