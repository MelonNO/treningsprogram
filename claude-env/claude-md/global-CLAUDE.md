# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Environment

Raspberry Pi 5 running Raspberry Pi OS with the **labwc** Wayland compositor. This is a desktop environment configuration workspace, not a code project. Home directory: `/home/migul`.

## Key Config File Locations

| Component | Path |
|-----------|------|
| labwc compositor | `~/.config/labwc/rc.xml` |
| labwc autostart | `~/.config/labwc/autostart` |
| labwc env vars | `~/.config/labwc/environment` |
| Panel (wf-panel-pi) | `~/.config/wf-panel-pi/wf-panel-pi.ini` |
| GTK settings daemon | `~/.config/xsettingsd/xsettingsd.conf` |
| GTK3 fallback | `~/.config/gtk-3.0/settings.ini` |
| GTK4 fallback | `~/.config/gtk-4.0/settings.ini` |
| Terminal (foot) | `~/.config/foot/foot.ini` |
| Shell prompt | `~/.config/starship.toml` |
| Display layout | `~/.config/kanshi/config` |

System-level labwc autostart (do not edit): `/etc/xdg/labwc/autostart`

## Active Configuration

**Theme:** PiXonyx (dark), icon theme PiXtrix, cursor PiXtrix  
**Panel:** `wf-panel-pi` at bottom  
**Terminal:** `foot` (Wayland-native), Tokyo Night colorscheme, JetBrains Mono 11pt  
**Prompt:** `starship` with Tokyo Night palette, two-line format, wired into `~/.bashrc`

## GTK Dark Theme — Four Required Layers

To apply a GTK dark theme fully (including GTK4/libadwaita popups), all four of these must be set:

1. **xsettingsd** running in autostart — serves GTK2/3 settings at runtime
2. **`~/.config/gtk-3.0/settings.ini`** and **`~/.config/gtk-4.0/settings.ini`** — static fallback for apps that read ini directly
3. **`GTK_THEME=PiXonyx`** in `~/.config/labwc/environment` — forces theme for apps (e.g. Chromium) that ignore xsettingsd
4. **gsettings** (dconf): `gsettings set org.gnome.desktop.interface color-scheme prefer-dark` and `gtk-theme PiXonyx` — required for GTK4/libadwaita apps which bypass all of the above

## Autostart — Avoid Duplicate Panel

The system autostart at `/etc/xdg/labwc/autostart` already launches `pcmanfm-pi` (desktop icons) and `wf-panel-pi`. The user autostart `~/.config/labwc/autostart` must NOT include these or they will appear twice. User autostart currently only contains:

```
/usr/bin/kanshi &
/usr/bin/xsettingsd &
/usr/bin/lxsession-xdg-autostart
```

## Useful Commands

```bash
# Reload labwc config without restarting
labwc --reconfigure

# Apply gsettings dark theme
gsettings set org.gnome.desktop.interface color-scheme prefer-dark
gsettings set org.gnome.desktop.interface gtk-theme PiXonyx

# Check what's running in autostart
pgrep -a wf-panel-pi
pgrep -a xsettingsd

# Set default terminal
sudo update-alternatives --config x-terminal-emulator
```
