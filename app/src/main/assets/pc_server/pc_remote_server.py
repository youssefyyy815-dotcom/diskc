#!/usr/bin/env python3
"""
DeskLink PC Server Backend (Windows 10/11)
==========================================
Lightweight, zero-dependency Python 3 server for local Wi-Fi (LAN) and optional
remote PC management. Uses Python's standard library (ThreadingHTTPServer +
native sockets + Win32 ctypes / subprocess) so it runs 100% offline with no
external pip packages required.

Serves BOTH:
  1. JSON REST API for the DeskLink Android mobile controller.
  2. Responsive Web / Local PWA dashboard at http://<PC_LOCAL_IP>:<PORT>/

DISCLAIMER:
  The user assumes full, sole responsibility for hosting, configuring,
  securing, and using this administrative tool on their personal private network.
"""

from __future__ import annotations

import argparse
import ctypes
import hashlib
import hmac
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
import platform
import re
import secrets
import shlex
import socket
import subprocess
import sys
import threading
import time
from typing import Any, Dict, List, Optional, Tuple


VERSION = "1.0.0"
DEFAULT_PORT = 8765
CONFIG_FILENAME = "desklink_config.json"
TOKEN_TTL_SECONDS = 24 * 60 * 60
MAX_FAILED_ATTEMPTS = 5
LOCKOUT_SECONDS = 60

# Safe read-only / utility commands permitted by default in Safe Shell mode.
SAFE_COMMAND_ALLOWLIST = {
    "whoami",
    "hostname",
    "ver",
    "ipconfig",
    "ping",
    "tracert",
    "nslookup",
    "netstat",
    "arp",
    "getmac",
    "route",
    "systeminfo",
    "tasklist",
    "dir",
    "echo",
    "powercfg",
    "query",
    "quser",
    "qwinsta",
    "calc",
    "notepad",
    "taskmgr",
    "explorer",
}

DANGEROUS_PATTERNS = [
    r"[;&|`><]",
    r"\$\(",
    r"\bformat\b",
    r"\bdiskpart\b",
    r"\bdel\b",
    r"\berase\b",
    r"\brmdir\b",
    r"\brd\b",
    r"\breg\s+(delete|add)\b",
    r"\bbcdedit\b",
    r"\bvssadmin\b",
    r"\bcipher\s+/w",
    r"\brm\b",
]


def get_local_ipv4_addresses() -> List[str]:
    ips: List[str] = []
    try:
        # Probe outbound socket (no actual packets transmitted over internet)
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("192.0.2.1", 80))
            primary = s.getsockname()[0]
            if primary and not primary.startswith("127."):
                ips.append(primary)
    except OSError:
        pass

    try:
        hostname = socket.gethostname()
        for info in socket.getaddrinfo(hostname, None, socket.AF_INET):
            addr = info[4][0]
            if addr and not addr.startswith("127.") and addr not in ips:
                ips.append(addr)
    except OSError:
        pass

    return ips or ["127.0.0.1"]


def hash_pin(pin: str, salt: Optional[str] = None) -> Tuple[str, str]:
    actual_salt = salt or secrets.token_hex(16)
    digest = hashlib.pbkdf2_hmac(
        "sha256", pin.encode("utf-8"), actual_salt.encode("utf-8"), 120_000
    ).hex()
    return actual_salt, digest


def verify_pin(pin: str, salt: str, expected_hash: str) -> bool:
    _, candidate_hash = hash_pin(pin, salt)
    return hmac.compare_digest(candidate_hash, expected_hash)


def load_or_create_config(config_path: str, cli_pin: Optional[str]) -> Tuple[Dict[str, Any], Optional[str]]:
    generated_pin: Optional[str] = None
    config: Dict[str, Any] = {}

    if os.path.exists(config_path):
        try:
            with open(config_path, "r", encoding="utf-8") as fh:
                config = json.load(fh)
        except (OSError, json.JSONDecodeError):
            config = {}

    if cli_pin:
        salt, digest = hash_pin(cli_pin)
        config["pin_salt"] = salt
        config["pin_hash"] = digest
        generated_pin = cli_pin
    elif "pin_salt" not in config or "pin_hash" not in config:
        generated_pin = f"{secrets.randbelow(900000) + 100000}"
        salt, digest = hash_pin(generated_pin)
        config["pin_salt"] = salt
        config["pin_hash"] = digest

    if "custom_actions" not in config:
        config["custom_actions"] = [
            {"id": "net_info", "label": "Network Config", "command": "ipconfig /all"},
            {"id": "top_procs", "label": "Active Processes", "command": "tasklist"},
            {"id": "sys_info", "label": "System Summary", "command": "systeminfo"},
            {"id": "open_ports", "label": "Listening Ports", "command": "netstat -ano"},
            {"id": "dns_flush", "label": "Flush DNS Cache", "command": "ipconfig /flushdns"},
        ]

    try:
        with open(config_path, "w", encoding="utf-8") as fh:
            json.dump(config, fh, indent=2)
    except OSError:
        pass

    return config, generated_pin


class WindowsSystemController:
    """Executes Windows system power, media volume, and safe shell commands."""

    VK_VOLUME_MUTE = 0xAD
    VK_VOLUME_DOWN = 0xAE
    VK_VOLUME_UP = 0xAF
    VK_MEDIA_NEXT_TRACK = 0xB0
    VK_MEDIA_PREV_TRACK = 0xB1
    VK_MEDIA_PLAY_PAUSE = 0xB3
    KEYEVENTF_KEYUP = 0x0002

    def __init__(self, unrestricted_shell: bool = False) -> None:
        self.is_windows = platform.system().lower() == "windows"
        self.unrestricted_shell = unrestricted_shell
        self._lock = threading.Lock()
        self._cached_volume: int = 50
        self._cached_muted: bool = False
        self._pending_power_action: Optional[str] = None
        self._start_time = time.time()

    def get_uptime_seconds(self) -> int:
        if self.is_windows:
            try:
                get_tick_count64 = ctypes.windll.kernel32.GetTickCount64
                get_tick_count64.restype = ctypes.c_ulonglong
                return int(get_tick_count64() // 1000)
            except Exception:
                pass
        return int(time.time() - self._start_time)

    def _send_vk(self, vk_code: int) -> bool:
        if not self.is_windows:
            return False
        try:
            user32 = ctypes.windll.user32
            user32.keybd_event(vk_code, 0, 0, 0)
            user32.keybd_event(vk_code, 0, self.KEYEVENTF_KEYUP, 0)
            return True
        except Exception:
            return False

    def _ps_audio_query(self, script_body: str) -> Optional[str]:
        if not self.is_windows:
            return None
        ps_bridge = r"""
Add-Type -TypeDefinition @"
using System;
using System.Runtime.InteropServices;
[Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IAudioEndpointVolume {
    int RegisterControlChangeNotify(IntPtr pNotify);
    int UnregisterControlChangeNotify(IntPtr pNotify);
    int GetChannelCount(out uint pnChannelCount);
    int SetMasterVolumeLevel(float fLevelDB, Guid pguidEventContext);
    int SetMasterVolumeLevelScalar(float fLevel, Guid pguidEventContext);
    int GetMasterVolumeLevel(out float pfLevelDB);
    int GetMasterVolumeLevelScalar(out float pfLevel);
    int SetChannelVolumeLevel(uint nChannel, float fLevelDB, Guid pguidEventContext);
    int SetChannelVolumeLevelScalar(uint nChannel, float fLevel, Guid pguidEventContext);
    int GetChannelVolumeLevel(uint nChannel, out float pfLevelDB);
    int GetChannelVolumeLevelScalar(uint nChannel, out float pfLevel);
    int SetMute([MarshalAs(UnmanagedType.Bool)] bool bMute, Guid pguidEventContext);
    int GetMute(out bool pbMute);
}
[Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IMMDevice {
    int Activate(ref Guid iid, int dwClsCtx, IntPtr pActivationParams, [MarshalAs(UnmanagedType.IUnknown)] out object ppInterface);
}
[Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
public interface IMMDeviceEnumerator {
    int EnumAudioEndpoints(int dataFlow, int dwStateMask, out IntPtr ppDevices);
    int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice ppEndpoint);
}
[ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
public class MMDeviceEnumerator {}
public class DeskLinkAudio {
    private static IAudioEndpointVolume Vol() {
        var enumerator = (IMMDeviceEnumerator)(new MMDeviceEnumerator());
        IMMDevice dev;
        enumerator.GetDefaultAudioEndpoint(0, 1, out dev);
        var iid = typeof(IAudioEndpointVolume).GUID;
        object obj;
        dev.Activate(ref iid, 23, IntPtr.Zero, out obj);
        return (IAudioEndpointVolume)obj;
    }
    public static int GetVol() { float v; Vol().GetMasterVolumeLevelScalar(out v); return (int)Math.Round(v * 100); }
    public static bool GetMute() { bool m; Vol().GetMute(out m); return m; }
    public static void SetVol(int pct) {
        float f = Math.Max(0, Math.Min(100, pct)) / 100.0f;
        Vol().SetMasterVolumeLevelScalar(f, Guid.Empty);
    }
    public static void SetMute(bool m) { Vol().SetMute(m, Guid.Empty); }
}
"@
""" + script_body
        try:
            proc = subprocess.run(
                ["powershell", "-NoProfile", "-NonInteractive", "-Command", ps_bridge],
                capture_output=True,
                text=True,
                timeout=6,
                creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
            )
            if proc.returncode == 0:
                return proc.stdout.strip()
        except Exception:
            pass
        return None

    def get_audio_state(self) -> Tuple[int, bool]:
        if self.is_windows:
            out = self._ps_audio_query(
                'Write-Output "$([DeskLinkAudio]::GetVol()),$([DeskLinkAudio]::GetMute())"'
            )
            if out and "," in out:
                try:
                    vol_str, mute_str = out.splitlines()[-1].split(",", 1)
                    self._cached_volume = max(0, min(100, int(vol_str.strip())))
                    self._cached_muted = mute_str.strip().lower() == "true"
                except ValueError:
                    pass
        return self._cached_volume, self._cached_muted

    def handle_volume(self, action: str, level: Optional[int] = None) -> Dict[str, Any]:
        action = action.lower().strip()
        with self._lock:
            if action == "up":
                if self.is_windows:
                    self._send_vk(self.VK_VOLUME_UP)
                self._cached_volume = min(100, self._cached_volume + 5)
            elif action == "down":
                if self.is_windows:
                    self._send_vk(self.VK_VOLUME_DOWN)
                self._cached_volume = max(0, self._cached_volume - 5)
            elif action == "mute":
                if self.is_windows:
                    self._send_vk(self.VK_VOLUME_MUTE)
                self._cached_muted = not self._cached_muted
            elif action == "set" and level is not None:
                clamped = max(0, min(100, int(level)))
                if self.is_windows:
                    self._ps_audio_query(f"[DeskLinkAudio]::SetVol({clamped})")
                self._cached_volume = clamped
            elif action == "play_pause":
                if self.is_windows:
                    self._send_vk(self.VK_MEDIA_PLAY_PAUSE)
            elif action == "next":
                if self.is_windows:
                    self._send_vk(self.VK_MEDIA_NEXT_TRACK)
            elif action == "prev":
                if self.is_windows:
                    self._send_vk(self.VK_MEDIA_PREV_TRACK)
            else:
                raise ValueError(f"Unsupported media/volume action: {action}")

        vol, muted = self.get_audio_state()
        return {"ok": True, "action": action, "volume": vol, "muted": muted}

    def handle_power(self, action: str, delay_seconds: int = 5) -> Dict[str, Any]:
        action = action.lower().strip()
        delay = max(0, min(300, int(delay_seconds)))

        if action == "lock":
            if self.is_windows:
                locked = bool(ctypes.windll.user32.LockWorkStation())
                if not locked:
                    subprocess.Popen(["rundll32.exe", "user32.dll,LockWorkStation"])
            return {"ok": True, "action": "lock", "message": "Workstation locked."}

        if action == "sleep":
            self._pending_power_action = None
            if self.is_windows:
                def _do_sleep() -> None:
                    time.sleep(0.6)
                    subprocess.run(
                        ["rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0"],
                        check=False,
                    )
                threading.Thread(target=_do_sleep, daemon=True).start()
            return {"ok": True, "action": "sleep", "message": "System entering Sleep mode."}

        if action == "shutdown":
            self._pending_power_action = "shutdown"
            if self.is_windows:
                subprocess.run(
                    ["shutdown", "/s", "/t", str(delay), "/c", "DeskLink remote shutdown"],
                    check=True,
                )
            return {
                "ok": True,
                "action": "shutdown",
                "delay_seconds": delay,
                "message": f"Shutdown scheduled in {delay}s. Send Abort to cancel.",
            }

        if action == "restart":
            self._pending_power_action = "restart"
            if self.is_windows:
                subprocess.run(
                    ["shutdown", "/r", "/t", str(delay), "/c", "DeskLink remote restart"],
                    check=True,
                )
            return {
                "ok": True,
                "action": "restart",
                "delay_seconds": delay,
                "message": f"Restart scheduled in {delay}s. Send Abort to cancel.",
            }

        if action == "abort":
            self._pending_power_action = None
            if self.is_windows:
                subprocess.run(["shutdown", "/a"], check=False)
            return {"ok": True, "action": "abort", "message": "Pending shutdown/restart cancelled."}

        raise ValueError(f"Unsupported power action: {action}")

    def validate_safe_command(self, cmd: str) -> Tuple[bool, str]:
        stripped = cmd.strip()
        if not stripped:
            return False, "Command cannot be empty."
        if self.unrestricted_shell:
            return True, ""

        for pat in DANGEROUS_PATTERNS:
            if re.search(pat, stripped, flags=re.IGNORECASE):
                return (
                    False,
                    "Blocked by Safe Shell Guard: shell chaining, redirection, or destructive verbs require --unrestricted-shell on the PC server.",
                )

        try:
            tokens = shlex.split(stripped, posix=not self.is_windows)
        except ValueError as exc:
            return False, f"Invalid command syntax: {exc}"

        if not tokens:
            return False, "Empty command."

        exe = os.path.basename(tokens[0]).lower()
        if exe.endswith(".exe"):
            exe = exe[:-4]

        if exe not in SAFE_COMMAND_ALLOWLIST:
            allowed_preview = ", ".join(sorted(SAFE_COMMAND_ALLOWLIST)[:10])
            return (
                False,
                f"'{exe}' is not in Safe Shell allowlist ({allowed_preview}, ...). Launch server with --unrestricted-shell to allow arbitrary commands.",
            )
        return True, ""

    def execute_command(self, cmd: str) -> Dict[str, Any]:
        allowed, reason = self.validate_safe_command(cmd)
        if not allowed:
            return {
                "ok": False,
                "command": cmd,
                "exit_code": -1,
                "stdout": "",
                "stderr": reason,
                "duration_ms": 0,
            }

        started = time.perf_counter()
        try:
            args = shlex.split(cmd.strip(), posix=not self.is_windows)
            if self.is_windows:
                proc = subprocess.run(
                    ["cmd", "/c", *args] if not self.unrestricted_shell else cmd,
                    shell=self.unrestricted_shell,
                    capture_output=True,
                    text=True,
                    timeout=15,
                    creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0),
                )
            else:
                proc = subprocess.run(
                    args if not self.unrestricted_shell else cmd,
                    shell=self.unrestricted_shell,
                    capture_output=True,
                    text=True,
                    timeout=15,
                )
            elapsed_ms = int((time.perf_counter() - started) * 1000)
            return {
                "ok": proc.returncode == 0,
                "command": cmd,
                "exit_code": proc.returncode,
                "stdout": proc.stdout[:16000],
                "stderr": proc.stderr[:8000],
                "duration_ms": elapsed_ms,
            }
        except subprocess.TimeoutExpired:
            elapsed_ms = int((time.perf_counter() - started) * 1000)
            return {
                "ok": False,
                "command": cmd,
                "exit_code": -1,
                "stdout": "",
                "stderr": "Command timed out after 15 seconds.",
                "duration_ms": elapsed_ms,
            }
        except Exception as exc:
            elapsed_ms = int((time.perf_counter() - started) * 1000)
            return {
                "ok": False,
                "command": cmd,
                "exit_code": -1,
                "stdout": "",
                "stderr": str(exc),
                "duration_ms": elapsed_ms,
            }

    def get_status(self) -> Dict[str, Any]:
        vol, muted = self.get_audio_state()
        return {
            "online": True,
            "service": "DeskLink",
            "version": VERSION,
            "hostname": socket.gethostname(),
            "os": f"{platform.system()} {platform.release()}",
            "architecture": platform.machine(),
            "uptime_seconds": self.get_uptime_seconds(),
            "local_ips": get_local_ipv4_addresses(),
            "volume": vol,
            "muted": muted,
            "pending_power_action": self._pending_power_action,
            "unrestricted_shell": self.unrestricted_shell,
            "server_time": int(time.time()),
        }


class AuthManager:
    """PIN verification, session tokens, and per-IP brute-force protection."""

    def __init__(self, pin_salt: str, pin_hash: str) -> None:
        self._pin_salt = pin_salt
        self._pin_hash = pin_hash
        self._tokens: Dict[str, float] = {}
        self._failures: Dict[str, Tuple[int, float]] = {}
        self._lock = threading.Lock()

    def is_locked_out(self, client_ip: str) -> int:
        with self._lock:
            entry = self._failures.get(client_ip)
            if not entry:
                return 0
            count, last_ts = entry
            if count >= MAX_FAILED_ATTEMPTS:
                remaining = int(LOCKOUT_SECONDS - (time.time() - last_ts))
                if remaining > 0:
                    return remaining
                self._failures.pop(client_ip, None)
            return 0

    def authenticate_pin(self, pin: str, client_ip: str) -> Tuple[bool, Optional[str], Optional[str]]:
        if self.is_locked_out(client_ip) > 0:
            return False, None, "Too many failed attempts. Wait 60 seconds."
        if verify_pin(pin, self._pin_salt, self._pin_hash):
            token = secrets.token_urlsafe(32)
            with self._lock:
                self._failures.pop(client_ip, None)
                self._tokens[token] = time.time() + TOKEN_TTL_SECONDS
            return True, token, None
        with self._lock:
            count, _ = self._failures.get(client_ip, (0, 0.0))
            self._failures[client_ip] = (count + 1, time.time())
        return False, None, "Invalid PIN."

    def authorize_headers(self, headers: Any, client_ip: str) -> Tuple[bool, str]:
        if self.is_locked_out(client_ip) > 0:
            return False, "Rate limited due to repeated invalid PIN attempts."

        auth_header = headers.get("Authorization", "")
        token = ""
        if auth_header.startswith("Bearer "):
            token = auth_header[7:].strip()
        if not token:
            token = headers.get("X-Auth-Token", "").strip()

        if token:
            with self._lock:
                expiry = self._tokens.get(token)
                if expiry and expiry > time.time():
                    return True, ""
                if expiry:
                    self._tokens.pop(token, None)

        pin = headers.get("X-DeskLink-Pin", "").strip()
        if pin:
            ok, _, err = self.authenticate_pin(pin, client_ip)
            return ok, err or "Unauthorized"

        return False, "Authentication required. Provide X-DeskLink-Pin or Bearer token."


WEB_DASHBOARD_HTML = r"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1" />
<meta name="theme-color" content="#0B0F14" />
<link rel="manifest" href="/manifest.json" />
<title>DeskLink // PC Remote Control</title>
<style>
  :root {
    --bg: #0B0F14; --surface: #121821; --elev: #18212E; --border: #233042;
    --cyan: #22D3EE; --green: #10B981; --amber: #F59E0B; --red: #EF4444;
    --text: #E2E8F0; --muted: #94A3B8; --mono: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  }
  * { box-sizing: border-box; }
  body {
    margin: 0; padding: 16px; background: var(--bg); color: var(--text);
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    display: flex; justify-content: center;
  }
  .wrap { width: 100%; max-width: 720px; display: flex; flex-direction: column; gap: 14px; }
  .card {
    background: var(--surface); border: 1px solid var(--border);
    border-radius: 12px; padding: 16px;
  }
  .header { display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap; }
  .title { font-size: 20px; font-weight: 700; letter-spacing: -0.02em; }
  .sub { font-size: 12px; color: var(--muted); font-family: var(--mono); margin-top: 2px; }
  .pill {
    display: inline-flex; align-items: center; gap: 7px; padding: 5px 11px;
    border-radius: 999px; font-size: 11px; font-weight: 700; font-family: var(--mono);
    border: 1px solid rgba(16,185,129,0.4); background: rgba(16,185,129,0.12); color: var(--green);
  }
  .pill.off { border-color: rgba(239,68,68,0.4); background: rgba(239,68,68,0.12); color: var(--red); }
  .dot { width: 8px; height: 8px; border-radius: 50%; background: currentColor; }
  .sec-title { font-family: var(--mono); font-size: 12px; color: var(--muted); margin-bottom: 10px; letter-spacing: 0.06em; }
  .grid2 { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; }
  .grid3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
  button {
    cursor: pointer; border-radius: 10px; border: 1px solid var(--border);
    background: var(--elev); color: var(--text); padding: 14px 12px;
    font-size: 14px; font-weight: 600; transition: transform 0.08s ease, border-color 0.15s ease;
    min-height: 48px;
  }
  button:active { transform: scale(0.98); }
  .btn-cyan { border-color: rgba(34,211,238,0.45); color: var(--cyan); }
  .btn-amber { border-color: rgba(245,158,11,0.45); color: var(--amber); }
  .btn-red { border-color: rgba(239,68,68,0.5); color: var(--red); background: rgba(239,68,68,0.1); }
  .btn-primary { background: var(--cyan); color: #0B0F14; border-color: var(--cyan); font-weight: 700; }
  input {
    width: 100%; padding: 12px; border-radius: 8px; border: 1px solid var(--border);
    background: #0E141C; color: var(--text); font-family: var(--mono); font-size: 14px;
  }
  .row { display: flex; gap: 8px; align-items: center; }
  .slider { width: 100%; accent-color: var(--cyan); height: 28px; }
  pre {
    margin: 0; background: #090D12; border: 1px solid var(--border); border-radius: 8px;
    padding: 12px; font-family: var(--mono); font-size: 12px; color: #CBD5E1;
    max-height: 220px; overflow: auto; white-space: pre-wrap; word-break: break-word;
  }
  .hidden { display: none !important; }
  .toast { font-size: 12px; font-family: var(--mono); color: var(--cyan); min-height: 18px; margin-top: 8px; }
  @media (max-width: 480px) { .grid2, .grid3 { grid-template-columns: 1fr; } }
</style>
</head>
<body>
<div class="wrap">
  <div class="card header">
    <div>
      <div class="title">DeskLink Web Console</div>
      <div class="sub" id="hostMeta">Waiting for authentication...</div>
    </div>
    <div class="pill off" id="statusPill"><span class="dot"></span><span id="statusText">LOCKED</span></div>
  </div>

  <div class="card" id="loginCard">
    <div class="sec-title">// AUTHENTICATE WITH PC PIN</div>
    <div class="row">
      <input id="pinInput" type="password" inputmode="numeric" placeholder="Enter Server PIN" />
      <button class="btn-primary" onclick="authenticate()" style="min-width:110px">Unlock</button>
    </div>
    <div class="toast" id="loginMsg"></div>
  </div>

  <div id="dashboard" class="hidden" style="display:flex;flex-direction:column;gap:14px;">
    <div class="card">
      <div class="sec-title">// POWER &amp; SESSION</div>
      <div class="grid2">
        <button class="btn-cyan" onclick="sendPower('lock', false)">Lock Screen</button>
        <button class="btn-amber" onclick="sendPower('sleep', true)">Sleep PC</button>
        <button class="btn-amber" onclick="sendPower('restart', true)">Restart PC</button>
        <button class="btn-red" onclick="sendPower('shutdown', true)">Shut Down PC</button>
      </div>
      <button class="btn-cyan hidden" id="abortBtn" onclick="sendPower('abort', false)" style="width:100%;margin-top:10px;">
        Abort Pending Shutdown / Restart
      </button>
      <div class="toast" id="powerToast"></div>
    </div>

    <div class="card">
      <div class="sec-title">// MEDIA &amp; VOLUME (<span id="volLabel">50%</span>)</div>
      <input type="range" min="0" max="100" value="50" class="slider" id="volSlider" oninput="onSliderMove(this.value)" onchange="setVolume(this.value)" />
      <div class="grid3" style="margin-top:10px;">
        <button onclick="mediaAction('down')">Vol -</button>
        <button onclick="mediaAction('mute')" id="muteBtn">Mute / Unmute</button>
        <button onclick="mediaAction('up')">Vol +</button>
        <button onclick="mediaAction('prev')">Prev Track</button>
        <button class="btn-cyan" onclick="mediaAction('play_pause')">Play / Pause</button>
        <button onclick="mediaAction('next')">Next Track</button>
      </div>
    </div>

    <div class="card">
      <div class="sec-title">// QUICK ACTIONS</div>
      <div class="grid2" id="quickGrid"></div>
    </div>

    <div class="card">
      <div class="sec-title">// SAFE SHELL TERMINAL</div>
      <div class="row" style="margin-bottom:10px;">
        <input id="cmdInput" placeholder="e.g. ipconfig, whoami, tasklist" onkeydown="if(event.key==='Enter') runCommand()" />
        <button class="btn-primary" onclick="runCommand()" style="min-width:96px">Run</button>
      </div>
      <pre id="cmdOutput">Ready.</pre>
    </div>
  </div>
</div>
<script>
let token = sessionStorage.getItem('desklink_token') || '';
let pollTimer = null;

async function api(path, method='GET', body=null) {
  const headers = {'Content-Type': 'application/json'};
  if (token) headers['Authorization'] = 'Bearer ' + token;
  const t0 = performance.now();
  const res = await fetch(path, { method, headers, body: body ? JSON.stringify(body) : null });
  const ms = Math.round(performance.now() - t0);
  const data = await res.json().catch(() => ({}));
  return { status: res.status, ok: res.ok, ms, data };
}

async function authenticate() {
  const pin = document.getElementById('pinInput').value.trim();
  const msg = document.getElementById('loginMsg');
  msg.textContent = 'Verifying PIN...';
  try {
    const r = await api('/api/auth', 'POST', { pin });
    if (r.ok && r.data.token) {
      token = r.data.token;
      sessionStorage.setItem('desklink_token', token);
      msg.textContent = '';
      enterDashboard();
    } else {
      msg.textContent = r.data.error || 'Invalid PIN';
    }
  } catch (e) {
    msg.textContent = 'Connection error: ' + e.message;
  }
}

async function enterDashboard() {
  document.getElementById('loginCard').classList.add('hidden');
  document.getElementById('dashboard').classList.remove('hidden');
  await refreshStatus();
  await loadActions();
  if (pollTimer) clearInterval(pollTimer);
  pollTimer = setInterval(refreshStatus, 3000);
}

async function refreshStatus() {
  const pill = document.getElementById('statusPill');
  const st = document.getElementById('statusText');
  const meta = document.getElementById('hostMeta');
  try {
    const r = await api('/api/status');
    if (r.status === 401) {
      token = '';
      sessionStorage.removeItem('desklink_token');
      document.getElementById('loginCard').classList.remove('hidden');
      document.getElementById('dashboard').classList.add('hidden');
      pill.classList.add('off'); st.textContent = 'LOCKED';
      return;
    }
    if (r.ok) {
      pill.classList.remove('off');
      st.textContent = `ONLINE • ${r.ms}ms`;
      const d = r.data;
      meta.textContent = `${d.hostname} (${d.os}) • Up ${Math.floor(d.uptime_seconds/60)}m`;
      document.getElementById('volSlider').value = d.volume;
      document.getElementById('volLabel').textContent = d.muted ? `${d.volume}% (MUTED)` : `${d.volume}%`;
      document.getElementById('abortBtn').classList.toggle('hidden', !d.pending_power_action);
    }
  } catch (e) {
    pill.classList.add('off');
    st.textContent = 'OFFLINE';
  }
}

async function loadActions() {
  const r = await api('/api/actions');
  if (r.ok && Array.isArray(r.data.actions)) {
    const grid = document.getElementById('quickGrid');
    grid.innerHTML = '';
    r.data.actions.forEach(a => {
      const btn = document.createElement('button');
      btn.textContent = a.label;
      btn.onclick = () => executeCmd(a.command);
      grid.appendChild(btn);
    });
  }
}

async function sendPower(action, askConfirm) {
  if (askConfirm && !confirm(`Confirm ${action.toUpperCase()} on this PC?`)) return;
  const r = await api('/api/power/' + action, 'POST', { delay: 5 });
  document.getElementById('powerToast').textContent = r.data.message || r.data.error || 'Done';
  refreshStatus();
}

function onSliderMove(v) { document.getElementById('volLabel').textContent = v + '%'; }
async function setVolume(v) { await api('/api/volume', 'POST', { action: 'set', level: Number(v) }); refreshStatus(); }
async function mediaAction(action) { await api('/api/volume', 'POST', { action }); refreshStatus(); }

function runCommand() { executeCmd(document.getElementById('cmdInput').value); }
async function executeCmd(cmd) {
  if (!cmd.trim()) return;
  const out = document.getElementById('cmdOutput');
  out.textContent = '> ' + cmd + '\nRunning...';
  const r = await api('/api/command', 'POST', { command: cmd });
  const text = (r.data.stdout || '') + (r.data.stderr ? '\n[STDERR]\n' + r.data.stderr : '');
  out.textContent = `> ${cmd} (exit ${r.data.exit_code}, ${r.data.duration_ms}ms)\n\n${text || '(no output)'}`;
}

if (token) enterDashboard();
</script>
</body>
</html>
"""


def make_handler(
    controller: WindowsSystemController,
    auth: AuthManager,
    config: Dict[str, Any],
):
    class DeskLinkRequestHandler(BaseHTTPRequestHandler):
        protocol_version = "HTTP/1.1"

        def log_message(self, format: str, *args: Any) -> None:
            sys.stdout.write(
                f"[{time.strftime('%H:%M:%S')}] {self.client_address[0]} - {format % args}\n"
            )
            sys.stdout.flush()

        def _send_cors(self) -> None:
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header(
                "Access-Control-Allow-Headers",
                "Content-Type, Authorization, X-Auth-Token, X-DeskLink-Pin",
            )
            self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")

        def _send_json(self, code: int, payload: Dict[str, Any]) -> None:
            raw = json.dumps(payload).encode("utf-8")
            self.send_response(code)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self._send_cors()
            self.end_headers()
            self.wfile.write(raw)

        def _send_bytes(self, code: int, content_type: str, raw: bytes) -> None:
            self.send_response(code)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(raw)))
            self._send_cors()
            self.end_headers()
            self.wfile.write(raw)

        def _read_json_body(self) -> Dict[str, Any]:
            length = int(self.headers.get("Content-Length", "0") or "0")
            if length <= 0 or length > 65536:
                return {}
            raw = self.rfile.read(length)
            try:
                data = json.loads(raw.decode("utf-8"))
                return data if isinstance(data, dict) else {}
            except (UnicodeDecodeError, json.JSONDecodeError):
                return {}

        def _require_auth(self) -> bool:
            ok, err = auth.authorize_headers(self.headers, self.client_address[0])
            if not ok:
                code = HTTPStatus.TOO_MANY_REQUESTS if "Rate limited" in err else HTTPStatus.UNAUTHORIZED
                self._send_json(code, {"ok": False, "error": err})
                return False
            return True

        def do_OPTIONS(self) -> None:
            self.send_response(HTTPStatus.NO_CONTENT)
            self._send_cors()
            self.send_header("Content-Length", "0")
            self.end_headers()

        def do_GET(self) -> None:
            path = self.path.split("?", 1)[0]
            if path in ("/", "/index.html"):
                self._send_bytes(
                    HTTPStatus.OK,
                    "text/html; charset=utf-8",
                    WEB_DASHBOARD_HTML.encode("utf-8"),
                )
                return

            if path == "/manifest.json":
                manifest = {
                    "name": "DeskLink PC Console",
                    "short_name": "DeskLink",
                    "start_url": "/",
                    "display": "standalone",
                    "background_color": "#0B0F14",
                    "theme_color": "#0B0F14",
                }
                self._send_json(HTTPStatus.OK, manifest)
                return

            if path == "/api/ping":
                self._send_json(
                    HTTPStatus.OK,
                    {
                        "online": True,
                        "service": "DeskLink",
                        "version": VERSION,
                        "auth_required": True,
                    },
                )
                return

            if path == "/api/status":
                if not self._require_auth():
                    return
                self._send_json(HTTPStatus.OK, controller.get_status())
                return

            if path == "/api/actions":
                if not self._require_auth():
                    return
                self._send_json(
                    HTTPStatus.OK,
                    {"ok": True, "actions": config.get("custom_actions", [])},
                )
                return

            self._send_json(HTTPStatus.NOT_FOUND, {"ok": False, "error": "Not found"})

        def do_POST(self) -> None:
            path = self.path.split("?", 1)[0]
            body = self._read_json_body()

            if path == "/api/auth":
                pin = str(body.get("pin", "")).strip()
                ok, token, err = auth.authenticate_pin(pin, self.client_address[0])
                if ok and token:
                    self._send_json(
                        HTTPStatus.OK,
                        {"ok": True, "token": token, "expires_in": TOKEN_TTL_SECONDS},
                    )
                else:
                    code = (
                        HTTPStatus.TOO_MANY_REQUESTS
                        if err and "Too many" in err
                        else HTTPStatus.UNAUTHORIZED
                    )
                    self._send_json(code, {"ok": False, "error": err or "Unauthorized"})
                return

            if not self._require_auth():
                return

            if path.startswith("/api/power/"):
                action = path.rsplit("/", 1)[-1]
                delay = int(body.get("delay", 5))
                try:
                    result = controller.handle_power(action, delay)
                    self._send_json(HTTPStatus.OK, result)
                except Exception as exc:
                    self._send_json(HTTPStatus.BAD_REQUEST, {"ok": False, "error": str(exc)})
                return

            if path == "/api/volume":
                action = str(body.get("action", "")).strip()
                level = body.get("level")
                try:
                    result = controller.handle_volume(
                        action, int(level) if level is not None else None
                    )
                    self._send_json(HTTPStatus.OK, result)
                except Exception as exc:
                    self._send_json(HTTPStatus.BAD_REQUEST, {"ok": False, "error": str(exc)})
                return

            if path == "/api/command":
                cmd = str(body.get("command", "")).strip()
                result = controller.execute_command(cmd)
                status = HTTPStatus.OK if result["ok"] else HTTPStatus.BAD_REQUEST
                self._send_json(status, result)
                return

            self._send_json(HTTPStatus.NOT_FOUND, {"ok": False, "error": "Not found"})

    return DeskLinkRequestHandler


def main() -> None:
    parser = argparse.ArgumentParser(description="DeskLink Windows PC Remote Control Server")
    parser.add_argument("--host", default=os.environ.get("DESKLINK_HOST", "0.0.0.0"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("DESKLINK_PORT", DEFAULT_PORT)))
    parser.add_argument("--pin", default=os.environ.get("DESKLINK_PIN"))
    parser.add_argument(
        "--unrestricted-shell",
        action="store_true",
        help="Disable Safe Shell allowlist (only enable on trusted private networks).",
    )
    args = parser.parse_args()

    if getattr(sys, "frozen", False):
        script_dir = os.path.dirname(os.path.abspath(sys.executable))
    else:
        script_dir = os.path.dirname(os.path.abspath(__file__))
    config_path = os.path.join(script_dir, CONFIG_FILENAME)
    config, shown_pin = load_or_create_config(config_path, args.pin)

    controller = WindowsSystemController(unrestricted_shell=args.unrestricted_shell)
    auth = AuthManager(config["pin_salt"], config["pin_hash"])
    handler_cls = make_handler(controller, auth, config)

    ThreadingHTTPServer.allow_reuse_address = True
    try:
        server = ThreadingHTTPServer((args.host, args.port), handler_cls)
    except OSError as exc:
        print(f"[ERROR] Could not bind to {args.host}:{args.port}: {exc}")
        if getattr(sys, "frozen", False):
            input("Press Enter to exit...")
        sys.exit(1)
    local_ips = get_local_ipv4_addresses()

    print("=" * 64)
    print(f" DeskLink PC Server v{VERSION} running on {platform.system()} ({socket.gethostname()})")
    print("=" * 64)
    for ip in local_ips:
        print(f"  LAN Dashboard / App Target : http://{ip}:{args.port}")
    if shown_pin:
        print(f"  Auth PIN (save this!)      : {shown_pin}")
    else:
        print(f"  Auth PIN                   : Loaded from {CONFIG_FILENAME} (use --pin to reset)")
    print(f"  Safe Shell Guard           : {'DISABLED' if args.unrestricted_shell else 'ENABLED (Allowlist)'}")
    print("-" * 64)
    print("  NOTE: You assume full, sole responsibility for hosting and")
    print("  securing this tool on your personal private network.")
    print("  Press Ctrl+C to stop server.")
    print("=" * 64)

    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down DeskLink server...")
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
