# -*- coding: utf-8 -*-
"""Replace user-visible English UI literals with Chinese in Kotlin sources."""
from __future__ import annotations

from pathlib import Path

ROOT = Path(r"d:\lpm_pro\KLVW-Wallpaper")
TARGETS = [
    ROOT / "app/src/main/kotlin/com/klvw/wallpaper/ui",
    ROOT / "app/src/main/kotlin/com/klvw/wallpaper/tile",
    ROOT / "app/src/main/kotlin/com/klvw/wallpaper/aer/ui",
    ROOT / "app/src/main/kotlin/com/klvw/wallpaper/tasker",
]

# Longer keys first to avoid partial replacements inside larger phrases.
TRANSLATIONS: list[tuple[str, str]] = [
    (
        "This will unlock all wallpaper functions and restore the previous home and lock screen wallpapers.",
        "这将重新启用全部壁纸功能，并恢复之前的桌面与锁屏壁纸。",
    ),
    (
        "Both screens set to Static Image — this tile acts as a global kill switch. Tapping it will apply static wallpapers and disable all KLVW wallpaper functions until re-enabled via long press.",
        "桌面与锁屏都设为静态图时，此磁贴会变成总开关：点一下应用静态壁纸并停用全部 KLVW 壁纸功能；长按可重新启用。",
    ),
    (
        "For Random Image/Video, the tile uses the default folders set in the Folder Selector popup items.",
        "随机图片/视频时，磁贴会使用弹层「文件夹选择」里设置的默认文件夹。",
    ),
    (
        "Show a notification with remaining time when you unlock your screen. ",
        "解锁时显示通知，提示各定时器剩余时间。",
    ),
    (
        "When you press the Global Off QS button, all running timers are paused ",
        "按下「全局关闭」快捷设置按钮时，所有运行中的定时器会暂停。",
    ),
    (
        "Each enabled timer can pause automatically when you lock the device ",
        "锁屏时，已开启的定时器可自动暂停（倒计时冻结，解锁后继续）。",
    ),
    (
        "No popup items configured yet. Tap Add Item to build your popup menu.",
        "还没有弹层项目。点「添加项目」开始配置弹层菜单。",
    ),
    (
        "No static images saved. Add a Static Image action in KLVW Popup Menu first.",
        "还没有保存静态图。请先在「KLVW 弹层菜单」中添加「静态图」动作。",
    ),
    (
        "No Pujie watch faces saved. Add them in the Pujie Watch Faces section below.",
        "还没有保存 Pujie 表盘。请在下方「Pujie 表盘」区域添加。",
    ),
    (
        "No watch faces configured.\\nAdd them in Settings → Pujie Watch Faces.",
        "尚未配置表盘。\\n请到设置 → Pujie 表盘 中添加。",
    ),
    (
        "No watch items yet. Tap \\\"Add Watch Item\\\" to get started.",
        "还没有手表项目。点「添加手表项目」开始。",
    ),
    (
        "Customize the popup menu colors. Leave as Default to follow the system theme.",
        "自定义弹层颜色。保持「默认」则跟随系统主题。",
    ),
    (
        "Hide notification while all timers are paused.",
        "全部定时器暂停时隐藏通知。",
    ),
    (
        "Configure what the KLVW Quick Set tile does when tapped.",
        "配置点按「KLVW 快捷设置」磁贴时的行为。",
    ),
    (
        "Configure actions for your Galaxy Watch. The watch app fetches this list when opened.",
        "为 Galaxy Watch 配置动作。手表应用打开时会拉取此列表。",
    ),
    (
        "Used by popup items and Tasker actions when no folder is specified",
        "弹层项目或 Tasker 动作未指定文件夹时使用",
    ),
    (
        "Vibrate the phone when Global Off is triggered from the watch",
        "从手表触发「全局关闭」时震动手机",
    ),
    (
        "Add a 'Pujie Watch Face' item to the popup menu to access these faces from the popup.",
        "在弹层菜单中添加「Pujie 表盘」项目后，可从弹层访问这些表盘。",
    ),
    (
        "No watch faces added yet. Tap Add Watch Face to configure one.",
        "还没有添加表盘。点「添加表盘」进行配置。",
    ),
    (
        "Restores the last wallpaper that was active before the most recent change.",
        "恢复最近一次更换之前的壁纸。",
    ),
    (
        "Uses the default folder set in KLVW → Folder Selector",
        "使用 KLVW → 文件夹选择器 中设置的默认文件夹",
    ),
    (
        "No static images saved. Add images in the Static tab first.",
        "还没有保存静态图。请先在「静态」页添加图片。",
    ),
    (
        "No folders saved. Add folders in the app first.",
        "还没有保存文件夹。请先在应用中添加文件夹。",
    ),
    (
        "No actions configured — open Settings to add items",
        "尚未配置动作 — 打开设置添加项目",
    ),
    (
        "Files in Aer are stored in app-private storage. Open in Files App to browse with the system file manager and play videos with the default player. Use Manage to import media.",
        "Aer 文件保存在应用私有存储。可在「文件应用」中用系统文件管理器浏览，并用默认播放器播放视频。用「管理」导入媒体。",
    ),
    (
        "Enter timeout in minutes (0 or blank to disable).",
        "输入超时分钟数（0 或留空表示关闭）。",
    ),
    (
        "Lock Aer automatically when the screen turns off",
        "熄屏时自动卸载 Aer（外部应用不可见）",
    ),
    (
        "Browse and play media in the system file manager",
        "在系统文件管理器中浏览与播放",
    ),
    (
        "Import files and assign images as wallpapers",
        "导入文件，并可把图片设为壁纸",
    ),
    (
        "Use Vulkan for hardware-accelerated image rendering",
        "使用 Vulkan 进行硬件加速图片渲染",
    ),
    (
        "Remove cached thumbnails so replaced files show fresh",
        "清除缩略图缓存，使替换后的文件显示最新内容",
    ),
    (
        "Override status bar icon brightness",
        "强制覆盖状态栏图标明暗",
    ),
    (
        "Set KLVW as the live wallpaper service",
        "将 KLVW 设为动态壁纸服务",
    ),
    (
        "Tasker Plugin: Add the KLVW action in Tasker → New Task → + → Plugin → KLVW Wallpaper",
        "Tasker 插件：在 Tasker → 新建任务 → + → 插件 → KLVW Wallpaper 中添加动作",
    ),
    (
        "To sideload KLVW Watch onto your Galaxy Watch:",
        "将 KLVW Watch 侧载到 Galaxy Watch：",
    ),
    (
        "1. On the watch: Settings → Developer options → Wireless debugging → Enable",
        "1. 手表：设置 → 开发者选项 → 无线调试 → 开启",
    ),
    (
        "2. On your PC: pair and connect via ADB",
        "2. 电脑：用 ADB 配对并连接",
    ),
    (
        "3. Install the watch APK:",
        "3. 安装手表 APK：",
    ),
    (
        '4. Open \\"KLVW Watch\\" from the watch app drawer.',
        "4. 在手表应用列表中打开「KLVW Watch」。",
    ),
    (
        "Cycle wallpaper on each unlock",
        "每次解锁时轮换壁纸",
    ),
    (
        "On Unlock — Cycle Wallpaper",
        "解锁时 — 轮换壁纸",
    ),
    (
        "Reset home timer on unlock",
        "解锁时重置桌面定时",
    ),
    (
        "Reset lock timer on unlock",
        "解锁时重置锁屏定时",
    ),
    (
        "Reset timer on unlock",
        "解锁时重置定时",
    ),
    (
        "Pause timers on Global Off",
        "全局关闭时暂停定时",
    ),
    (
        "Timer status on unlock",
        "解锁时显示定时状态",
    ),
    (
        "Only show when running",
        "仅在运行时显示",
    ),
    (
        "Pause on Lock (P.o.L)",
        "锁屏暂停 (P.o.L)",
    ),
    (
        "Pause on lock",
        "锁屏时暂停",
    ),
    (
        "Apply width to overlays",
        "将宽度应用到子弹层",
    ),
    (
        "Icon Color Picker",
        "图标颜色选择器",
    ),
    (
        "Folder Selector",
        "文件夹选择器",
    ),
    (
        "Timer Popup",
        "定时弹层",
    ),
    (
        "All Folders Popup",
        "全部文件夹弹层",
    ),
    (
        "Display Control Popup",
        "显示控制弹层",
    ),
    (
        "Manual Item Size",
        "手动项目尺寸",
    ),
    (
        "Timer Interval Highlight",
        "定时间隔高亮色",
    ),
    (
        "Timer Section Border",
        "定时分区边框色",
    ),
    (
        "Primary Text",
        "主文字颜色",
    ),
    (
        "Secondary Text",
        "次文字颜色",
    ),
    (
        "Source Folder (optional)",
        "来源文件夹（可选）",
    ),
    (
        "Global Default Folder",
        "全局默认文件夹",
    ),
    (
        "Default Folders",
        "默认文件夹",
    ),
    (
        "Default folder",
        "默认文件夹",
    ),
    (
        "Use default folder",
        "使用默认文件夹",
    ),
    (
        "None (skip watch face)",
        "无（跳过表盘）",
    ),
    (
        "Pujie Watch Faces app not found",
        "未找到 Pujie 表盘应用",
    ),
    (
        "Add Watch Face",
        "添加表盘",
    ),
    (
        "Add Watch Item",
        "添加手表项目",
    ),
    (
        "Install Watch App",
        "安装手表应用",
    ),
    (
        "Vibrate on Global Off",
        "全局关闭时震动",
    ),
    (
        "Appear on top",
        "显示在最上层",
    ),
    (
        "Unmount on device lock",
        "设备锁屏时卸载",
    ),
    (
        "Open in Files App",
        "在文件应用中打开",
    ),
    (
        "Manage Aer Storage",
        "管理 Aer 存储",
    ),
    (
        "Unmount after",
        "超时后卸载",
    ),
    (
        "Aer Private Storage",
        "Aer 私密存储",
    ),
    (
        "Popup Appearance",
        "弹层外观",
    ),
    (
        "KLVW Popup Menu",
        "KLVW 弹层菜单",
    ),
    (
        "KLVW Quick Set Tile",
        "KLVW 快捷设置磁贴",
    ),
    (
        "Pujie Watch Faces",
        "Pujie 表盘",
    ),
    (
        "KLVW Display Control",
        "KLVW 显示控制",
    ),
    (
        "Wallpaper Timers",
        "壁纸定时器",
    ),
    (
        "KLVW Watch",
        "KLVW 手表",
    ),
    (
        "Display Control",
        "显示控制",
    ),
    (
        "Status Bar Icons",
        "状态栏图标",
    ),
    (
        "Live Wallpaper",
        "动态壁纸",
    ),
    (
        "Activate Live Wallpaper",
        "启用动态壁纸",
    ),
    (
        "Vulkan GPU Rendering",
        "Vulkan GPU 渲染",
    ),
    (
        "Clear Thumbnail Cache",
        "清除缩略图缓存",
    ),
    (
        "Cache cleared",
        "缓存已清除",
    ),
    (
        "Performance",
        "性能",
    ),
    (
        "Item Layout",
        "项目布局",
    ),
    (
        "Popup Width",
        "弹层宽度",
    ),
    (
        "Auto-scale",
        "自动缩放",
    ),
    (
        "Columns",
        "列数",
    ),
    (
        "Add Item",
        "添加项目",
    ),
    (
        "Home Screen",
        "桌面",
    ),
    (
        "Lock Screen",
        "锁屏",
    ),
    (
        "Watch Face",
        "表盘",
    ),
    (
        "Action Type",
        "动作类型",
    ),
    (
        "Random Image",
        "随机图片",
    ),
    (
        "Static Image",
        "静态图",
    ),
    (
        "Random Video",
        "随机视频",
    ),
    (
        "Restore Previous",
        "恢复上一张",
    ),
    (
        "Set Icon Color",
        "设置图标颜色",
    ),
    (
        "Icon Color",
        "图标颜色",
    ),
    (
        "Source Folder",
        "来源文件夹",
    ),
    (
        "All Folders",
        "全部文件夹",
    ),
    (
        "Apply To",
        "应用到",
    ),
    (
        "Apply to",
        "应用到",
    ),
    (
        "Set Wallpaper",
        "设为壁纸",
    ),
    (
        "Select Wallpaper",
        "选择壁纸",
    ),
    (
        "No timers running",
        "没有运行中的定时器",
    ),
    (
        "No folders added yet",
        "还没有添加文件夹",
    ),
    (
        "No files found in this folder.",
        "此文件夹中没有文件。",
    ),
    (
        "No static images saved.",
        "还没有保存静态图。",
    ),
    (
        "No subfolders here",
        "这里没有子文件夹",
    ),
    (
        "Unmounted — external apps cannot see files",
        "已卸载 — 外部应用看不到这些文件",
    ),
    (
        "Empty — tap + to import media",
        "空空如也 — 点 + 导入媒体",
    ),
    (
        "Add Image Folder",
        "添加图片文件夹",
    ),
    (
        "Add Video Folder",
        "添加视频文件夹",
    ),
    (
        "Add Aer Image",
        "添加 Aer 图片",
    ),
    (
        "Add Aer Video",
        "添加 Aer 视频",
    ),
    (
        "Image Folders",
        "图片文件夹",
    ),
    (
        "Video Folders",
        "视频文件夹",
    ),
    (
        "Folder Manager",
        "文件夹管理",
    ),
    (
        "KLVW Wallpaper",
        "KLVW 壁纸",
    ),
    (
        "KLVW Timers",
        "KLVW 定时器",
    ),
    (
        "KLVW Tasker Action",
        "KLVW Tasker 动作",
    ),
    (
        "Re-enable KLVW?",
        "重新启用 KLVW？",
    ),
    (
        "Change Wallpaper Now",
        "立即更换壁纸",
    ),
    (
        "Change Now",
        "立即更换",
    ),
    (
        "Pause Timer",
        "暂停定时",
    ),
    (
        "Resume Timer",
        "继续定时",
    ),
    (
        "Reset Timer",
        "重置定时",
    ),
    (
        "Home Image",
        "桌面图片",
    ),
    (
        "Home Video",
        "桌面视频",
    ),
    (
        "Lock Image",
        "锁屏图片",
    ),
    (
        "Lock Video",
        "锁屏视频",
    ),
    (
        "Dark Icons",
        "深色图标",
    ),
    (
        "Light Icons",
        "浅色图标",
    ),
    (
        "Import Files",
        "导入文件",
    ),
    (
        "Import Folder",
        "导入文件夹",
    ),
    (
        "New Folder",
        "新建文件夹",
    ),
    (
        "Folder name",
        "文件夹名称",
    ),
    (
        "Delete Items",
        "删除项目",
    ),
    (
        "New name",
        "新名称",
    ),
    (
        "New folder",
        "新建文件夹",
    ),
    (
        "Importing...",
        "正在导入…",
    ),
    (
        "Move up",
        "上移",
    ),
    (
        "Move down",
        "下移",
    ),
    (
        "Permissions",
        "权限",
    ),
    (
        "Background",
        "背景色",
    ),
    (
        "Minutes",
        "分钟",
    ),
    (
        "Settings",
        "设置",
    ),
    (
        "Wallpaper",
        "壁纸",
    ),
    (
        "Folders",
        "文件夹",
    ),
    (
        "Selected",
        "已选中",
    ),
    (
        "Images",
        "图片",
    ),
    (
        "Videos",
        "视频",
    ),
    (
        "Image",
        "图片",
    ),
    (
        "Video",
        "视频",
    ),
    (
        "Static",
        "静态",
    ),
    (
        "Paused",
        "已暂停",
    ),
    (
        "Resume",
        "继续",
    ),
    (
        "Reset",
        "重置",
    ),
    (
        "Pause",
        "暂停",
    ),
    (
        "Close",
        "关闭",
    ),
    (
        "Clear",
        "清除",
    ),
    (
        "Grant",
        "授权",
    ),
    (
        "Open",
        "打开",
    ),
    (
        "Save",
        "保存",
    ),
    (
        "Cancel",
        "取消",
    ),
    (
        "Create",
        "创建",
    ),
    (
        "Delete",
        "删除",
    ),
    (
        "Rename",
        "重命名",
    ),
    (
        "Mount",
        "挂载",
    ),
    (
        "Unmount",
        "卸载",
    ),
    (
        "Import",
        "导入",
    ),
    (
        "Remove",
        "移除",
    ),
    (
        "Edit",
        "编辑",
    ),
    (
        "Done",
        "完成",
    ),
    (
        "Label",
        "标签",
    ),
    (
        "List",
        "列表",
    ),
    (
        "Grid",
        "网格",
    ),
    (
        "Color",
        "颜色",
    ),
    (
        "About",
        "关于",
    ),
    (
        "Status",
        "状态",
    ),
    (
        "Default",
        "默认",
    ),
    (
        "Auto",
        "自动",
    ),
    (
        "Dark",
        "深色",
    ),
    (
        "Light",
        "浅色",
    ),
    (
        "Both",
        "两者",
    ),
    (
        "Home",
        "桌面",
    ),
    (
        "Lock",
        "锁屏",
    ),
    (
        "Back",
        "返回",
    ),
    (
        "Play",
        "播放",
    ),
    (
        "Yes",
        "是",
    ),
    (
        "No",
        "否",
    ),
    (
        "Aer Root",
        "Aer 根目录",
    ),
    (
        "Aer",
        "Aer",
    ),
    (
        "Version 1.0.0",
        "版本 7.0.0",
    ),
    (
        "firing…",
        "即将触发…",
    ),
    (
        "now",
        "现在",
    ),
]


def apply_to_text(text: str) -> tuple[str, int]:
    count = 0
    for en, zh in TRANSLATIONS:
        # Only replace inside double-quoted Kotlin/Compose string literals.
        old = f'"{en}"'
        new = f'"{zh}"'
        n = text.count(old)
        if n:
            text = text.replace(old, new)
            count += n
    return text, count


def main() -> None:
    total = 0
    files = 0
    for base in TARGETS:
        if not base.exists():
            continue
        for path in base.rglob("*.kt"):
            original = path.read_text(encoding="utf-8")
            updated, n = apply_to_text(original)
            if n:
                path.write_text(updated, encoding="utf-8")
                files += 1
                total += n
                print(f"{path.relative_to(ROOT)}: {n}")
    print(f"done: {total} replacements in {files} files")


if __name__ == "__main__":
    main()
