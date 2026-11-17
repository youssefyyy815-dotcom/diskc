# DeskLink Windows PC Server Setup & Network Guide

DeskLink is a 100% offline-capable local Wi-Fi (LAN) and optional remote management system for your Windows PC.

## 1. Quick Start (Zero Dependencies, Works 100% Offline)

1. Ensure **Python 3.8+** is installed on your Windows PC.
2. Open PowerShell or Command Prompt in the `pc_server/` folder.
3. Run the server (optionally set your own PIN):
   ```powershell
   python pc_remote_server.py --pin 482910 --port 8765
   ```
   If you omit `--pin`, DeskLink generates a random 6-digit PIN and saves its PBKDF2 hash in `desklink_config.json`.
4. Note the LAN URL printed in the terminal, e.g. `http://192.168.1.42:8765`.
5. Open that URL in **any browser on your Wi-Fi** (to use the built-in Web / Local PWA Dashboard) OR enter the IP, port, and PIN into the **DeskLink Android App**.

## 2. Local Wi-Fi (LAN) Binding & Windows Firewall

- The server binds to `0.0.0.0:8765` by default so devices on the same Wi-Fi router can reach your PC without internet access.
- Find your Windows IPv4 address anytime via:
  ```powershell
  ipconfig
  ```
- Allow inbound TCP traffic on your **Private** Wi-Fi profile in Windows Defender Firewall (run PowerShell as Administrator):
  ```powershell
  New-NetFirewallRule -DisplayName "DeskLink PC Server" -Direction Inbound -Protocol TCP -LocalPort 8765 -Profile Private -Action Allow
  ```

## 3. Optional Remote Access (Over the Internet)

### Recommended: Zero-Config Mesh VPN (Tailscale or WireGuard)
- Install **Tailscale** or **WireGuard** on both your Windows PC and your phone.
- Connect DeskLink to your PC's VPN IP (e.g., `100.x.y.z:8765`). All traffic stays encrypted end-to-end without opening router ports.

### Alternative: Router Port Forwarding
1. Assign a **static LAN IP (DHCP Reservation)** to your Windows PC in your router's admin panel.
2. Forward external TCP port `8765` (or a high random port) to your PC's static LAN IP on port `8765`.
3. **Security Warning**: Exposing raw HTTP over the public internet sends your PIN/token in cleartext unless wrapped behind an **HTTPS reverse proxy (Caddy / Nginx / Cloudflare Tunnel)**. Use a strong PIN/passphrase and prefer a VPN whenever possible.

## 4. Safe Shell & Custom Quick Actions

- By default, `pc_remote_server.py` enforces **Safe Shell Guard**, permitting read-only/diagnostic commands (`ipconfig`, `whoami`, `systeminfo`, `tasklist`, `netstat`, `ping`, etc.) and blocking shell chaining (`&&`, `;`, `|`, `>`).
- Edit `desklink_config.json` to customize quick-action presets, or pass `--unrestricted-shell` only on trusted private networks.

## 5. Responsibility Notice

The user assumes full, sole responsibility for hosting, configuring, securing, and using this administrative tool on their personal private network.
