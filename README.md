# AutoLaunch_WebView
This project is a secure Kiosk-mode Android application that auto-launches a fullscreen `WebView` after device boot or inactivity. It's designed for controlled environments such as digital signage, POS systems, self-service kiosks, or enterprise lockdown deployments
## 🚀 Key Features

- 🔐 **Kiosk Mode** (LockTask): Prevents exiting the app without PIN-based admin access.
- 🌐 **WebView Interface**: Loads a secure, configurable URL on startup.
- 🔁 **Auto-Launch on Boot**: Uses `BootReceiver` to automatically start `MainActivity` after device reboot.
- ⏳ **Inactivity Detection**: Relaunches WebView if the user exits the app or goes idle.
- 📶 **Heartbeat Monitoring**: Periodic log or server ping to track app liveness.
- 📱 **Accessibility Fallback**: Ensures recovery in case boot receivers are blocked.
- ⚙️ **Admin Panel**: Settings screen to update server URL and heartbeat interval.
- 🧾 **Device Admin Policies**: Optional Device Owner support for advanced lockdown.

---

## 🛠 Architecture Overview

- `MainActivity`: Core fullscreen WebView container, PIN-protected unlock, Kiosk setup.
- `KioskService`: Foreground service monitoring system status.
- `BootReceiver`: Triggers auto-launch after device reboot.
- `AutoLaunchAccessibilityService`: Accessibility-based fallback relaunch.
- `PermissionManager`: Centralized permission and battery optimization handler.
- `SettingsActivity`: Admin-accessible panel to configure URL and heartbeat.

---

## 🧑‍💻 How to Build & Deploy

### 📦 Requirements
- Android Studio (Giraffe or newer)
- Minimum SDK: 21 (Lollipop)
- Target SDK: 34+
- Gradle Plugin: 8.0+

### 🔧 Setup

1. Clone this repo:
   ```bash
   git clone https://github.com/your-org/autolaunch-kiosk.git
