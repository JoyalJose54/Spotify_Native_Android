"""
Spotify Server Control Center
=============================
A modern, simple Windows software to start and stop the Spotify Ingestion
Backend & Ngrok tunnel on demand with a single click.
"""

from __future__ import annotations

import os
import sys
import time
import socket
import shutil
import threading
import subprocess
import webbrowser
import urllib.request
import urllib.error
import json
from pathlib import Path
import tkinter as tk
from tkinter import ttk, messagebox

try:
    from PIL import Image, ImageTk
except ImportError:
    Image = None
    ImageTk = None

# Determine Directories (handles standalone .exe as well as .pyw script)
if getattr(sys, "frozen", False):
    BASE_DIR = Path(sys.executable).resolve().parent
else:
    BASE_DIR = Path(__file__).resolve().parent

CLOUD_FUNCTIONS_DIR = BASE_DIR / "cloud_functions"
DOMAIN_FILE = BASE_DIR / "ngrok_domain.txt"
DEFAULT_DOMAIN = "diary-lugged-petticoat.ngrok-free.dev"

# Windows Process Flags for silent execution (no black console windows)
CREATE_NO_WINDOW = 0x08000000 if sys.platform == "win32" else 0

# Colors (Spotify Dark Theme)
BG_MAIN = "#121212"
BG_CARD = "#181818"
BG_CARD_BORDER = "#282828"
BG_INPUT = "#242424"
BG_CONSOLE = "#0A0A0A"

TEXT_PRIMARY = "#FFFFFF"
TEXT_MUTED = "#A7A7A7"
TEXT_DIM = "#727272"

COLOR_GREEN = "#1DB954"
COLOR_GREEN_HOVER = "#1ED760"
COLOR_GREEN_ACTIVE = "#169C46"

COLOR_RED = "#E91429"
COLOR_RED_HOVER = "#FF334B"
COLOR_RED_ACTIVE = "#B80F20"

COLOR_BLUE = "#2E77D0"
COLOR_YELLOW = "#F59B23"


def get_configured_domain() -> str:
    """Read the configured Ngrok domain from ngrok_domain.txt if available."""
    if DOMAIN_FILE.is_file():
        try:
            with open(DOMAIN_FILE, "r", encoding="utf-8") as f:
                d = f.read().strip()
                if d:
                    return d
        except Exception:
            pass
    return DEFAULT_DOMAIN


def get_python_executable() -> str:
    """Find reliable python executable."""
    candidates = [
        r"C:\Python313\python.exe",
        shutil.which("python"),
    ]
    for c in candidates:
        if c and os.path.isfile(c):
            return c
    return "python"


def get_ngrok_executable() -> str:
    """Find reliable ngrok executable."""
    local_ngrok = BASE_DIR / "ngrok.exe"
    if local_ngrok.is_file():
        return str(local_ngrok)
    return "ngrok"


def is_port_in_use(port: int = 8080) -> bool:
    """Check if local port 8080 is already accepting connections."""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.settimeout(0.5)
        return s.connect_ex(("127.0.0.1", port)) == 0


class SpotifyServerApp(tk.Tk):
    def __init__(self):
        super().__init__()

        self.title("Spotify Backend Control Center")
        self.geometry("740x620")
        self.minsize(680, 560)
        self.configure(bg=BG_MAIN)

        # Set Window Icon
        self._set_app_icon()

        # Process Handles
        self.py_process: subprocess.Popen | None = None
        self.ngrok_process: subprocess.Popen | None = None
        self.is_running = False
        self.is_transitioning = False

        self.domain = get_configured_domain()

        self._setup_styles()
        self._build_ui()

        # Handle window close cleanly
        self.protocol("WM_DELETE_WINDOW", self.on_close)

        # Check initial state (maybe server was already running)
        self.after(500, self._check_existing_server)

        # Periodic health check loop
        self.after(5000, self._periodic_health_monitor)

    def _set_app_icon(self):
        ico_file = BASE_DIR / "spotify_logo.ico"
        png_file = BASE_DIR / "spotify_logo.png"

        if ico_file.is_file():
            try:
                self.iconbitmap(default=str(ico_file))
            except Exception:
                pass

        if png_file.is_file() and ImageTk:
            try:
                self.app_icon_photo = ImageTk.PhotoImage(file=str(png_file))
                self.iconphoto(False, self.app_icon_photo)
            except Exception:
                pass

    def _setup_styles(self):
        style = ttk.Style(self)
        style.theme_use("clam")

        # Configure scrollbar
        style.configure(
            "Dark.Vertical.TScrollbar",
            background=BG_CARD,
            troughcolor=BG_CONSOLE,
            bordercolor=BG_CARD_BORDER,
            arrowcolor=TEXT_MUTED,
        )

    def _build_ui(self):
        # Main Container
        main_frame = tk.Frame(self, bg=BG_MAIN)
        main_frame.pack(fill="both", expand=True, padx=20, pady=16)

        # ── Header ─────────────────────────────────────────────────────────────
        header_frame = tk.Frame(main_frame, bg=BG_MAIN)
        header_frame.pack(fill="x", pady=(0, 14))

        # Brand Container with Spotify Logo
        brand_container = tk.Frame(header_frame, bg=BG_MAIN)
        brand_container.pack(side="left")

        # Load & display Spotify Logo
        self.logo_photo = None
        png_file = BASE_DIR / "spotify_logo.png"
        if png_file.is_file() and Image and ImageTk:
            try:
                raw_img = Image.open(png_file)
                resized = raw_img.resize((44, 44), Image.Resampling.LANCZOS)
                self.logo_photo = ImageTk.PhotoImage(resized)
                logo_lbl = tk.Label(brand_container, image=self.logo_photo, bg=BG_MAIN)
                logo_lbl.pack(side="left", padx=(0, 12))
            except Exception:
                pass

        title_box = tk.Frame(brand_container, bg=BG_MAIN)
        title_box.pack(side="left")

        title_lbl = tk.Label(
            title_box,
            text="Spotify Backend Control",
            font=("Segoe UI", 18, "bold"),
            fg=TEXT_PRIMARY,
            bg=BG_MAIN,
        )
        title_lbl.pack(anchor="w")

        subtitle_lbl = tk.Label(
            title_box,
            text="YouTube Music Ingestion Engine & Cloud Tunnel",
            font=("Segoe UI", 10),
            fg=TEXT_MUTED,
            bg=BG_MAIN,
        )
        subtitle_lbl.pack(anchor="w")

        # Global Status Badge in Header
        self.badge_frame = tk.Frame(header_frame, bg=BG_CARD, padx=12, pady=6, highlightbackground=BG_CARD_BORDER, highlightthickness=1)
        self.badge_frame.pack(side="right")

        self.status_dot = tk.Label(self.badge_frame, text="●", font=("Segoe UI", 14), fg=COLOR_RED, bg=BG_CARD)
        self.status_dot.pack(side="left", padx=(0, 6))

        self.status_text = tk.Label(self.badge_frame, text="OFFLINE", font=("Segoe UI", 11, "bold"), fg=TEXT_MUTED, bg=BG_CARD)
        self.status_text.pack(side="left")

        # ── Status Card ─────────────────────────────────────────────────────────
        status_card = tk.Frame(main_frame, bg=BG_CARD, highlightbackground=BG_CARD_BORDER, highlightthickness=1, padx=16, pady=14)
        status_card.pack(fill="x", pady=(0, 14))

        # Local Server row
        r1 = tk.Frame(status_card, bg=BG_CARD)
        r1.pack(fill="x", pady=2)
        tk.Label(r1, text="Local Backend:", font=("Segoe UI", 10, "bold"), fg=TEXT_MUTED, bg=BG_CARD, width=14, anchor="w").pack(side="left")
        self.lbl_local_url = tk.Label(r1, text="http://127.0.0.1:8080", font=("Consolas", 10), fg=TEXT_PRIMARY, bg=BG_CARD)
        self.lbl_local_url.pack(side="left", padx=4)
        self.lbl_local_status = tk.Label(r1, text="[Stopped]", font=("Segoe UI", 9), fg=COLOR_RED, bg=BG_CARD)
        self.lbl_local_status.pack(side="left", padx=6)

        # Tunnel row
        r2 = tk.Frame(status_card, bg=BG_CARD)
        r2.pack(fill="x", pady=2)
        tk.Label(r2, text="Ngrok Tunnel:", font=("Segoe UI", 10, "bold"), fg=TEXT_MUTED, bg=BG_CARD, width=14, anchor="w").pack(side="left")
        self.lbl_tunnel_url = tk.Label(r2, text=f"https://{self.domain}", font=("Consolas", 10, "bold"), fg=COLOR_GREEN, bg=BG_CARD)
        self.lbl_tunnel_url.pack(side="left", padx=4)
        self.lbl_tunnel_status = tk.Label(r2, text="[Inactive]", font=("Segoe UI", 9), fg=COLOR_RED, bg=BG_CARD)
        self.lbl_tunnel_status.pack(side="left", padx=6)

        # Health Ping row
        r3 = tk.Frame(status_card, bg=BG_CARD)
        r3.pack(fill="x", pady=2)
        tk.Label(r3, text="Health Status:", font=("Segoe UI", 10, "bold"), fg=TEXT_MUTED, bg=BG_CARD, width=14, anchor="w").pack(side="left")
        self.lbl_health = tk.Label(r3, text="Not checked yet", font=("Segoe UI", 9), fg=TEXT_DIM, bg=BG_CARD)
        self.lbl_health.pack(side="left", padx=4)

        # ── Action Buttons ──────────────────────────────────────────────────────
        actions_frame = tk.Frame(main_frame, bg=BG_MAIN)
        actions_frame.pack(fill="x", pady=(0, 14))

        # Big START button
        self.btn_start = tk.Button(
            actions_frame,
            text="▶   START SERVER",
            font=("Segoe UI", 12, "bold"),
            bg=COLOR_GREEN,
            fg="#000000",
            activebackground=COLOR_GREEN_HOVER,
            activeforeground="#000000",
            relief="flat",
            cursor="hand2",
            padx=24,
            pady=10,
            command=self.start_server,
        )
        self.btn_start.pack(side="left", expand=True, fill="x", padx=(0, 8))

        # Big STOP button
        self.btn_stop = tk.Button(
            actions_frame,
            text="⏹   STOP SERVER",
            font=("Segoe UI", 12, "bold"),
            bg=BG_CARD,
            fg=TEXT_DIM,
            activebackground=COLOR_RED_HOVER,
            activeforeground="#FFFFFF",
            relief="flat",
            cursor="hand2",
            padx=24,
            pady=10,
            state="disabled",
            command=self.stop_server,
        )
        self.btn_stop.pack(side="left", expand=True, fill="x", padx=(0, 8))

        # Utility Buttons
        self.btn_copy = tk.Button(
            actions_frame,
            text="📋 Copy URL",
            font=("Segoe UI", 10),
            bg=BG_INPUT,
            fg=TEXT_PRIMARY,
            activebackground=BG_CARD_BORDER,
            relief="flat",
            cursor="hand2",
            padx=12,
            pady=10,
            command=self.copy_tunnel_url,
        )
        self.btn_copy.pack(side="left", padx=(0, 4))

        self.btn_browser = tk.Button(
            actions_frame,
            text="🌐 Open",
            font=("Segoe UI", 10),
            bg=BG_INPUT,
            fg=TEXT_PRIMARY,
            activebackground=BG_CARD_BORDER,
            relief="flat",
            cursor="hand2",
            padx=12,
            pady=10,
            command=self.open_in_browser,
        )
        self.btn_browser.pack(side="left", padx=(0, 4))

        self.btn_ping = tk.Button(
            actions_frame,
            text="🔄 Ping",
            font=("Segoe UI", 10),
            bg=BG_INPUT,
            fg=TEXT_PRIMARY,
            activebackground=BG_CARD_BORDER,
            relief="flat",
            cursor="hand2",
            padx=12,
            pady=10,
            command=self.trigger_health_check,
        )
        self.btn_ping.pack(side="left")

        # ── Console Log Area ────────────────────────────────────────────────────
        log_header = tk.Frame(main_frame, bg=BG_MAIN)
        log_header.pack(fill="x", pady=(0, 4))

        tk.Label(
            log_header,
            text="Live Output Console",
            font=("Segoe UI", 10, "bold"),
            fg=TEXT_MUTED,
            bg=BG_MAIN,
        ).pack(side="left")

        btn_clear = tk.Button(
            log_header,
            text="Clear Logs",
            font=("Segoe UI", 8),
            bg=BG_MAIN,
            fg=TEXT_DIM,
            activebackground=BG_CARD,
            activeforeground=TEXT_PRIMARY,
            relief="flat",
            cursor="hand2",
            command=self.clear_logs,
        )
        btn_clear.pack(side="right")

        log_container = tk.Frame(main_frame, bg=BG_CONSOLE, highlightbackground=BG_CARD_BORDER, highlightthickness=1)
        log_container.pack(fill="both", expand=True)

        self.log_text = tk.Text(
            log_container,
            wrap="word",
            bg=BG_CONSOLE,
            fg="#D4D4D4",
            insertbackground="#FFFFFF",
            font=("Consolas", 9),
            padx=10,
            pady=8,
            relief="flat",
            state="normal",
        )
        scrollbar = ttk.Scrollbar(log_container, orient="vertical", command=self.log_text.yview, style="Dark.Vertical.TScrollbar")
        self.log_text.configure(yscrollcommand=scrollbar.set)

        scrollbar.pack(side="right", fill="y")
        self.log_text.pack(side="left", fill="both", expand=True)

        # Text Tags for Syntax Coloring
        self.log_text.tag_config("INFO", foreground="#4FC1FF")
        self.log_text.tag_config("SUCCESS", foreground=COLOR_GREEN)
        self.log_text.tag_config("WARN", foreground=COLOR_YELLOW)
        self.log_text.tag_config("ERROR", foreground="#F48771")
        self.log_text.tag_config("TIME", foreground=TEXT_DIM)
        self.log_text.tag_config("HIGHLIGHT", foreground="#CE9178")

        self.append_log("Spotify Server Control ready. Click 'START SERVER' when you want to ingest songs.", "INFO")

    def append_log(self, text: str, tag: str = "INFO"):
        """Safely append colored timestamped line to log text box."""
        now = time.strftime("%H:%M:%S")
        self.log_text.insert("end", f"[{now}] ", "TIME")
        self.log_text.insert("end", f"{text}\n", tag)
        self.log_text.see("end")

    def clear_logs(self):
        self.log_text.delete("1.0", "end")

    def copy_tunnel_url(self):
        url = f"https://{self.domain}"
        self.clipboard_clear()
        self.clipboard_append(url)
        self.append_log(f"Copied tunnel URL to clipboard: {url}", "SUCCESS")

    def open_in_browser(self):
        url = f"https://{self.domain}/ping"
        self.append_log(f"Opening {url} in default web browser...", "INFO")
        webbrowser.open(url)

    def _check_existing_server(self):
        """Detect if port 8080 is already active on app startup."""
        if is_port_in_use(8080):
            self.append_log("Detected an existing backend running on port 8080.", "WARN")
            self._set_ui_state(running=True)
            self.trigger_health_check()
        else:
            self._set_ui_state(running=False)

    def _set_ui_state(self, running: bool, starting: bool = False):
        self.is_running = running
        if starting:
            self.status_dot.config(fg=COLOR_YELLOW)
            self.status_text.config(text="STARTING...", fg=COLOR_YELLOW)
            self.btn_start.config(state="disabled", bg=BG_CARD, fg=TEXT_DIM)
            self.btn_stop.config(state="disabled", bg=BG_CARD, fg=TEXT_DIM)
            return

        if running:
            self.status_dot.config(fg=COLOR_GREEN)
            self.status_text.config(text="ONLINE", fg=COLOR_GREEN)
            self.lbl_local_status.config(text="[Active]", fg=COLOR_GREEN)
            self.lbl_tunnel_status.config(text="[Connected]", fg=COLOR_GREEN)

            self.btn_start.config(state="disabled", bg=BG_CARD, fg=TEXT_DIM)
            self.btn_stop.config(state="normal", bg=COLOR_RED, fg="#FFFFFF")
        else:
            self.status_dot.config(fg=COLOR_RED)
            self.status_text.config(text="OFFLINE", fg=TEXT_MUTED)
            self.lbl_local_status.config(text="[Stopped]", fg=COLOR_RED)
            self.lbl_tunnel_status.config(text="[Inactive]", fg=COLOR_RED)
            self.lbl_health.config(text="Offline", fg=TEXT_DIM)

            self.btn_start.config(state="normal", bg=COLOR_GREEN, fg="#000000")
            self.btn_stop.config(state="disabled", bg=BG_CARD, fg=TEXT_DIM)

    def start_server(self):
        """Start the Python Flask Backend and Ngrok Tunnel."""
        if self.is_running or self.is_transitioning:
            return

        self.is_transitioning = True
        self._set_ui_state(running=False, starting=True)
        threading.Thread(target=self._start_server_thread, daemon=True).start()

    def _start_server_thread(self):
        try:
            py_exe = get_python_executable()
            ngrok_exe = get_ngrok_executable()

            self.append_log(f"Using Python: {py_exe}", "INFO")
            self.append_log(f"Using Ngrok: {ngrok_exe}", "INFO")
            self.append_log(f"Configured Ngrok Domain: {self.domain}", "INFO")

            # 1. Start Python Backend
            main_py = CLOUD_FUNCTIONS_DIR / "main.py"
            if not main_py.is_file():
                self.append_log(f"Error: {main_py} not found!", "ERROR")
                self.is_transitioning = False
                self.after(0, lambda: self._set_ui_state(running=False))
                return

            self.append_log("Starting Python Flask backend on http://127.0.0.1:8080...", "INFO")
            self.py_process = subprocess.Popen(
                [py_exe, "main.py"],
                cwd=str(CLOUD_FUNCTIONS_DIR),
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
                creationflags=CREATE_NO_WINDOW,
            )

            # Spawn reader thread for Python stdout
            threading.Thread(target=self._stream_process_output, args=(self.py_process, "Backend"), daemon=True).start()

            # Wait up to 10 seconds for port 8080 to become ready
            port_ready = False
            for _ in range(20):
                if is_port_in_use(8080):
                    port_ready = True
                    break
                time.sleep(0.5)

            if not port_ready:
                self.append_log("Warning: Port 8080 not responding yet, continuing tunnel launch...", "WARN")
            else:
                self.append_log("Backend listening on port 8080 \u2713", "SUCCESS")

            # 2. Start Ngrok Tunnel
            self.append_log(f"Connecting Ngrok tunnel to https://{self.domain}...", "INFO")
            ngrok_cmd = [ngrok_exe, "http", f"--url={self.domain}", "8080", "--log=stdout", "--log-format=term"]
            self.ngrok_process = subprocess.Popen(
                ngrok_cmd,
                cwd=str(BASE_DIR),
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
                creationflags=CREATE_NO_WINDOW,
            )

            threading.Thread(target=self._stream_process_output, args=(self.ngrok_process, "Ngrok"), daemon=True).start()

            time.sleep(2.0)
            self.append_log("Server & Tunnel are now RUNNING! \u2713", "SUCCESS")
            self.append_log(f"Public API endpoint: https://{self.domain}", "HIGHLIGHT")

            self.is_transitioning = False
            self.after(0, lambda: self._set_ui_state(running=True))
            self.after(500, self.trigger_health_check)

        except Exception as e:
            self.append_log(f"Failed to start server: {e}", "ERROR")
            self.is_transitioning = False
            self.after(0, lambda: self._set_ui_state(running=False))

    def _stream_process_output(self, proc: subprocess.Popen, prefix: str):
        """Read lines from process stdout and display them in log box."""
        if not proc.stdout:
            return
        for line in iter(proc.stdout.readline, ""):
            clean = line.strip()
            if clean:
                tag = "INFO"
                if "error" in clean.lower() or "exception" in clean.lower():
                    tag = "ERROR"
                elif "\u2713" in clean or "success" in clean.lower():
                    tag = "SUCCESS"
                elif "warn" in clean.lower():
                    tag = "WARN"
                self.after(0, self.append_log, f"[{prefix}] {clean}", tag)
        proc.stdout.close()

    def stop_server(self):
        """Stop both Python and Ngrok processes cleanly."""
        if not self.is_running and not self.is_transitioning:
            return

        self.append_log("Stopping Spotify Backend and Ngrok tunnel...", "WARN")

        threading.Thread(target=self._stop_server_thread, daemon=True).start()

    def _stop_server_thread(self):
        try:
            # Terminate ngrok
            if self.ngrok_process:
                try:
                    self.ngrok_process.terminate()
                    self.ngrok_process.wait(timeout=2)
                except Exception:
                    try:
                        self.ngrok_process.kill()
                    except Exception:
                        pass
                self.ngrok_process = None

            # Terminate python
            if self.py_process:
                try:
                    self.py_process.terminate()
                    self.py_process.wait(timeout=2)
                except Exception:
                    try:
                        self.py_process.kill()
                    except Exception:
                        pass
                self.py_process = None

            # Extra cleanup via taskkill just in case
            if sys.platform == "win32":
                subprocess.run(
                    ["taskkill", "/F", "/IM", "ngrok.exe"],
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                    creationflags=CREATE_NO_WINDOW,
                )
                # Kill any lingering process using port 8080
                self._kill_process_on_port(8080)

            self.append_log("Server stopped successfully. System resources released \u2713", "SUCCESS")
        except Exception as e:
            self.append_log(f"Error while stopping server: {e}", "ERROR")
        finally:
            self.is_transitioning = False
            self.after(0, lambda: self._set_ui_state(running=False))

    def _kill_process_on_port(self, port: int):
        """Find and terminate any process listening on the given port (Windows)."""
        try:
            cmd = f'netstat -ano | findstr :{port}'
            output = subprocess.check_output(cmd, shell=True, text=True, stderr=subprocess.DEVNULL)
            for line in output.strip().splitlines():
                if "LISTENING" in line:
                    parts = line.strip().split()
                    pid = parts[-1]
                    if pid and pid != "0":
                        subprocess.run(
                            ["taskkill", "/F", "/PID", pid],
                            stdout=subprocess.DEVNULL,
                            stderr=subprocess.DEVNULL,
                            creationflags=CREATE_NO_WINDOW,
                        )
        except Exception:
            pass

    def trigger_health_check(self):
        """Run health check against backend in background thread."""
        threading.Thread(target=self._health_check_worker, daemon=True).start()

    def _health_check_worker(self):
        # First check local
        local_ok = False
        start_t = time.time()
        try:
            req = urllib.request.Request("http://127.0.0.1:8080/ping", headers={"User-Agent": "SpotifyManager/1.0"})
            with urllib.request.urlopen(req, timeout=2.5) as resp:
                if resp.status == 200:
                    ms = int((time.time() - start_t) * 1000)
                    local_ok = True
                    text = f"Local: 200 OK ({ms}ms)"
        except Exception:
            text = "Local: Not responding"

        # Check ngrok tunnel if local is ok
        tunnel_text = ""
        if local_ok and self.domain:
            t_start = time.time()
            try:
                t_req = urllib.request.Request(f"https://{self.domain}/ping", headers={"User-Agent": "SpotifyManager/1.0"})
                with urllib.request.urlopen(t_req, timeout=3.5) as t_resp:
                    if t_resp.status == 200:
                        t_ms = int((time.time() - t_start) * 1000)
                        tunnel_text = f" | Tunnel: 200 OK ({t_ms}ms)"
            except Exception:
                tunnel_text = " | Tunnel: Inactive/Offline"

        full_status = text + tunnel_text
        self.after(0, lambda: self._update_health_label(full_status, local_ok))

    def _update_health_label(self, text: str, ok: bool):
        color = COLOR_GREEN if ok else COLOR_RED
        self.lbl_health.config(text=text, fg=color)

    def _periodic_health_monitor(self):
        if self.is_running:
            self.trigger_health_check()
        self.after(10000, self._periodic_health_monitor)

    def on_close(self):
        """Prompt to stop server if still running before quitting."""
        if self.is_running or is_port_in_use(8080):
            answer = messagebox.askyesnocancel(
                "Stop Server on Exit?",
                "The Spotify backend is currently running.\n\nDo you want to STOP the server before exiting?\n\n• Yes: Stop server and close\n• No: Keep server running in background and close\n• Cancel: Don't close",
            )
            if answer is None:
                return
            if answer is True:
                self.append_log("Stopping server before exit...", "WARN")
                self._stop_server_thread()

        self.destroy()


def main():
    try:
        app = SpotifyServerApp()
        app.mainloop()
    except Exception:
        import traceback
        err_file = BASE_DIR / "server_manager_error.log"
        with open(err_file, "w", encoding="utf-8") as f:
            f.write(traceback.format_exc())
        raise


if __name__ == "__main__":
    main()
