# KLVW Wallpaper

**KLVW** is an advanced Android wallpaper manager built for power users who want full, automated control over their home and lock screen wallpapers. It supports images and videos, automatic timed rotation, on-unlock cycling, a fully customisable floating popup menu, and a Quick Settings tile — all without requiring Tasker to function.

---

## Features at a Glance

| Feature | Description |
|---|---|
| Live Wallpaper | Hardware-accelerated image and video rendering for home and lock screen |
| Popup Menu | Floating overlay with fully configurable action buttons |
| Quick Set Tile | Native QS tile that sets both screens in one tap |
| Wallpaper Timers | Automatic timed rotation per target (home image, home video, lock image, lock video) |
| Display Control | Cycle to a new random wallpaper every time the screen is unlocked |
| Kill Switch | Global disable that freezes all wallpaper changes with one tile tap |
| Icon Color Control | Force status bar icons to dark or light regardless of wallpaper brightness |
| Folder Management | Organise image and video source folders independently |
| Static Images | Save specific images for direct one-tap wallpaper setting |
| Vulkan Rendering | Optional Vulkan GPU path for hardware-accelerated image rendering |

---

## How It Works

KLVW runs two persistent live wallpaper services — one for the home screen and one for the lock screen. All wallpaper actions (setting, rotating, restoring) funnel through a single repository that tracks the previous wallpaper so it can always be restored.

The **KLVW Popup Menu** is a floating overlay activity that can be launched by any app that can fire an Android intent — including QuikShort QS tiles, automation apps, or any shortcut launcher. Inside the popup you configure exactly which actions appear, in what order, and what they do.

---

## Setting Up the Popup Menu as a QS Tile (QuikShort)

The popup menu is triggered by firing the intent action:

```
com.klvw.wallpaper.POPUP
```

Use **[QuikShort](https://quikshort.atolphadev.com/docs/)** to create a Quick Settings tile that fires this intent:

1. Install [QuikShort](https://quikshort.atolphadev.com/docs/) from the Play Store.
2. Add a new QuikShort tile in your QS panel.
3. Configure the tile action as **Start Activity / Send Broadcast** using:
   - **Action:** `com.klvw.wallpaper.POPUP`
   - **Category:** `android.intent.category.DEFAULT`
   - **Package:** `com.klvw.wallpaper`
4. Label the tile (e.g. **KLVW Menu**) and save.

Pulling down your notification shade and tapping the tile will now open the KLVW popup overlay directly over whatever app or screen you are on.

> See the full QuikShort documentation at **https://quikshort.atolphadev.com/docs/** for details on intent configuration, tile icons, and advanced options.

---

## KLVW Popup Menu

### Adding and Configuring Items

Open the KLVW app → **Settings** → **KLVW Popup Menu** → **Add Item**.

Each item has a **Label** and one of the following **Action Types**:

| Action | What it does |
|---|---|
| **Random Image** | Sets a random image from a selected folder (or the default image folder) to the chosen screen target |
| **Random Video** | Sets a random video from a selected folder (or the default video folder) to the chosen screen target |
| **Static Image** | Sets a specific saved image as the wallpaper for the chosen screen target |
| **Restore Previous** | Restores the wallpaper that was active before the last change |
| **Icon Color** | Forces status bar icons to Auto, Dark, or Light |
| **Select File** | Opens a file browser so you can pick any image or video from storage at runtime |
| **Timer Control** | Opens the wallpaper timer panel inside the popup |
| **All Folders** | Opens a panel to set the default source folder for all four targets at once |
| **Display Control** | Opens the on-unlock cycle control panel inside the popup |

For **Random Image** and **Random Video** items you can optionally pin them to a specific folder; leaving it blank uses the global default folder for that target.

For **Static Image** items, you first save images via the Wallpaper Picker screen, then select one from the list in the editor.

Items can be reordered (up/down arrows) and edited or deleted at any time.

### Screen Target

Actions that apply to a specific screen let you choose:
- **Home** — home screen only
- **Lock** — lock screen only
- **Both** — home and lock screen simultaneously

Actions that manage their own targets at runtime (Timer Control, All Folders, Display Control, Select File, Icon Color) do not show a target selector.

### Layout

- **List** — items shown in a scrollable vertical list
- **Grid** — items shown in a configurable grid (2 – 5 columns)

### Sizing

- **Popup Width** — Narrow / Medium / Wide (controls the width of the main popup card)
- **Auto-scale** — automatically sizes items to fill the popup width
- **Manual Item Size** — when auto-scale is off: XS / S / M / L / XL
- **Apply width to overlays** — independently apply the same popup width to each child overlay:
  - Icon Color Picker
  - Folder Selector
  - Timer Popup
  - All Folders Popup
  - Display Control Popup

---

## KLVW Quick Set Tile

A native Android Quick Settings tile that sets both home and lock screen wallpapers in a single tap, without opening the popup menu.

### Setup

The tile appears as **KLVW Quick Set** in your QS tile picker. Add it to your panel via **Settings → Notifications → Quick Settings**.

### Configuration (in-app)

Settings → **KLVW Quick Set Tile**

| Setting | Options |
|---|---|
| Home Screen action | Random Image · Random Video · Static Image |
| Lock Screen action | Random Image · Random Video · Static Image |
| Static image (home) | Pick from saved static images |
| Static image (lock) | Pick from saved static images |

For Random Image/Video the tile uses the default folders configured via the **All Folders** popup item or the **Display Control** screen.

### Kill Switch Mode

When **both** Home and Lock are set to **Static Image**, the tile becomes a global kill switch:

- **Tap** — applies the configured static wallpapers to both screens, then **disables all KLVW wallpaper functions** (timers, popup actions, display control all stop). The tile turns inactive.
- **Long press** (or tap while inactive) — opens a confirmation dialog. Tap **Yes** to re-enable everything and apply the tile action. Tap **No** to cancel.

This is useful when you want to freeze your wallpapers and prevent anything from changing them.

---

## Wallpaper Timers

Automatically rotate wallpapers on a schedule. Timers survive device sleep (AlarmManager-based) and restart after reboot.

### Access

Via the **Timer Control** popup item in the KLVW Popup Menu.

### Per-Target Timers

Four independent timers, each with its own enable switch, interval, and pause/resume control:

| Timer | Target |
|---|---|
| Home Image | Home screen — cycles random images |
| Home Video | Home screen — cycles random videos |
| Lock Image | Lock screen — cycles random images |
| Lock Video | Lock screen — cycles random videos |

### Intervals

5 min · 10 min · 15 min · 30 min · 45 min · 1 h · 2 h · 4 h · 8 h · 24 h

### Controls

- **Enable/Disable switch** — toggles the timer on or off
- **Pause / Resume** — suspends firing without changing the enabled state; the countdown freezes at the current remaining time and resumes exactly from where it left off when unpaused
- **Countdown display** — shows the time remaining until the next wallpaper change for each active timer
- **Interval selector** — scrollable chip row; tap any value to reschedule from that moment

Changing the interval takes effect immediately — the next alarm is rescheduled from that moment.

### Pause on Lock (P.o.L)

Each timer has an optional **Pause on Lock** toggle. When enabled:

- The timer automatically pauses the moment the device screen locks and resumes automatically on unlock.
- The remaining countdown is frozen during the lock period — if 2 h were left when you locked, 2 h will still be left when you unlock.
- P.o.L state and remaining time are persisted to DataStore so they survive Samsung killing the background process while locked.

**Configure** in **Settings → Wallpaper Timers → Pause on Lock** (per-timer toggles), or directly inside the **Timer Control** overlay in the popup menu.

### Timer Notification

The persistent timer status notification (shown on unlock when enabled) lets you:

- **Tap notification body** — pause all running timers (or resume all if all are paused)
- **Resume button** — resume paused timers
- **Reset button** — reschedule to the full interval immediately
- **Change Now button** — immediately apply the next wallpaper without waiting for the timer

Manual pauses from the notification also survive process restart — the paused state is backed by DataStore alongside P.o.L pauses.

---

## Display Control

Automatically cycle to a new random wallpaper every time the screen is unlocked (user dismisses the lock screen).

### Setup

Settings → **KLVW Display Control**

#### Required Permissions

| Permission | Purpose | How to grant |
|---|---|---|
| Phone State | Read device call/screen state | Tap **Grant** in the section |
| Usage Access | Read app foreground state | Tap **Grant** → enable KLVW in Usage Access settings |
| Device Admin | Reliable lock-screen state detection | Tap **Grant** → enable KLVW Device Administrator |

#### On-Unlock Cycle Toggles

Enable each target independently:
- **Home Image** — cycles a random image to the home screen on unlock
- **Home Video** — cycles a random video to the home screen on unlock
- **Lock Image** — cycles a random image to the lock screen on unlock
- **Lock Video** — cycles a random video to the lock screen on unlock

The source folder for each target is the corresponding default folder (set via the **All Folders** popup item or the per-target folder selectors).

#### Reset Timer on Unlock

When any cycle toggle is active, two optional checkboxes appear:

- **Reset home timer on unlock** — when the home screen cycles on unlock, any running home timers are rescheduled from that moment (resets the countdown)
- **Reset lock timer on unlock** — same for lock screen timers

This keeps your timers in sync with your unlock behaviour — if you unlock and a new wallpaper is applied, the timer starts fresh so you don't get a second change moments later.

### Display Control in the Popup Menu

Add a **Display Control** item to your popup to access the same on-unlock toggles and timer-reset options without opening the main app.

---

## Folder Management

Settings → **Folders** tab (bottom navigation)

### Image Folders
Source folders for random image wallpapers. KLVW picks randomly from all images found inside the folder. You can add multiple folders; each appears in the popup editor's source folder list.

### Video Folders
Same as image folders but for video wallpapers.

### Adding a Folder
Tap **Add Image Folder** or **Add Video Folder** → pick a folder using the system folder picker → KLVW stores a persistent URI permission so it can read the folder after reboot without re-prompting.

### Deleting a Folder
Tap the delete icon next to any folder. This only removes it from KLVW's list; the actual folder and its files are untouched.

---

## Status Bar Icon Color

Independently from wallpaper setting, KLVW can force your status bar icons to a specific brightness mode.

**Settings → Status Bar Icons** or via the **Icon Color** popup menu item.

| Mode | Behaviour |
|---|---|
| Auto | Icons follow the wallpaper brightness (system default) |
| Dark | Icons are always dark (for light wallpapers) |
| Light | Icons are always light (for dark wallpapers) |

The Icon Color popup item lets you switch between modes directly from the floating popup or a QS tile.

---

## Popup Appearance

Settings → **Popup Appearance**

Customise the look of the popup card:

| Setting | Description |
|---|---|
| Background Color | Hex colour for the popup card background (leave blank for theme default) |
| Primary Text Color | Hex colour for item labels and headings |
| Secondary Text Color | Hex colour for subtitles and hints |
| Timer Interval Highlight | Hex colour for the selected interval chip in the timer panel (leave blank for primary colour at 35% opacity) |
| Timer Section Border | Hex colour for the border around each timer card in the timer panel (leave blank for white at 15% opacity) |

---

## Performance

Settings → **Performance**

| Setting | Description |
|---|---|
| Vulkan GPU Rendering | Uses the Vulkan graphics API for hardware-accelerated wallpaper rendering. Disable if you experience visual glitches on older devices. |

---

## Tasker Integration

KLVW registers a broadcast receiver for the action `com.klvw.wallpaper.SET_WALLPAPER`. You can use Tasker's **Send Intent** action to set a wallpaper by folder name:

| Extra | Value |
|---|---|
| `folder_name` | Name of a registered folder (supports Tasker variables e.g. `%KLVWFOLDER`) |
| `target` | `home` · `lock` · `both` |
| `type` | `image` · `video` (optional, defaults to image) |

> In Tasker: New Task → + → Plugin → **KLVW Wallpaper** to use the plugin action instead of a raw Send Intent.

---

## Requirements

- Android 8.0 (API 26) or higher
- KLVW set as the live wallpaper via **Settings → Live Wallpaper → Activate Live Wallpaper**
- Storage permission (READ_MEDIA_IMAGES / READ_MEDIA_VIDEO on Android 13+)
- SYSTEM_ALERT_WINDOW permission for the popup overlay (prompted on first use)
- WAKE_LOCK permission (held briefly on screen-off to ensure P.o.L timer pause completes before the CPU sleeps — no persistent wake lock)

---

## 打包说明（GitHub Actions / 本机）

本仓库 **不要提交** 签名证书和密码：

- `app/klvw-release.keystore`（或任何 `*.keystore` / `*.jks`）
- `keystore.properties`

模板见根目录 `keystore.properties.example`。

### 推荐：GitHub Actions 打正式包

当前 workflow：`.github/workflows/build.yml`

- push 到 `main` / `master` → 默认打 **release**
- Actions 页可手动选 `release` 或 `debug`
- 产物在 run 页面的 **Artifacts**（`klvw-wallpaper-release` / `klvw-wallpaper-debug`）

#### 1. 配置 4 个 Repository Secrets（名称必须完全一致）

仓库 → **Settings** → **Secrets and variables** → **Actions** → **New repository secret**

| Name（固定） | Secret 填什么 |
|---|---|
| `KEYSTORE_BASE64` | 证书文件的 base64 整段 |
| `KEYSTORE_PASSWORD` | 密钥库密码 |
| `KEY_ALIAS` | 证书别名 |
| `KEY_PASSWORD` | 密钥密码 |

#### 2. 生成本机证书的 base64（PowerShell 一行）

证书默认放在项目内：`app/klvw-release.keystore`（已被 gitignore）。

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("D:\lpm_pro\KLVW-Wallpaper\app\klvw-release.keystore")) | Set-Clipboard
```

执行后 Ctrl+V 贴进 `KEYSTORE_BASE64` 的 Secret 框。路径按你机器上的实际位置改。

也可用 `gh`（需已登录）：

```powershell
cd D:\lpm_pro\KLVW-Wallpaper
gh secret set KEYSTORE_BASE64 --body ([Convert]::ToBase64String([IO.File]::ReadAllBytes("app\klvw-release.keystore")))
gh secret set KEYSTORE_PASSWORD --body "你的密钥库密码"
gh secret set KEY_ALIAS --body "你的别名"
gh secret set KEY_PASSWORD --body "你的密钥密码"
```

#### 3. 推送后下载

```text
git push
→ GitHub → Actions → Build APK → Artifacts → klvw-wallpaper-release
```

未配置 `KEYSTORE_BASE64` 时，release 会退回 debug 签名（能装，但不是你的正式证书）。

### 可选：本机打包

需要本机同时有：

1. **JDK 17+**（可进 PATH，或当前终端临时指定）
2. **Android SDK**（写到 `local.properties` 的 `sdk.dir=...`，该文件已 gitignore）
3. 根目录 `keystore.properties`（从 `keystore.properties.example` 复制并填好）

示例（仅当前终端有效，关掉窗口需重设）：

```powershell
cd D:\lpm_pro\KLVW-Wallpaper
$env:JAVA_HOME = "C:\Users\admin\.jabba\jdk\system@17.0.19"
$env:Path = "$env:JAVA_HOME\bin;" + $env:Path
.\gradlew :app:assembleRelease
```

正式包输出：`app/build/outputs/apk/release/`

没有 Android SDK 时不要强行本机编，直接用上面的 GitHub Actions 即可。

---

## License

Private / personal use. All rights reserved.

