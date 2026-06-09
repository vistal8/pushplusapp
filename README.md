<img width="285" height="543" alt="e7597547-d03b-4ec0-b140-4d258cc5c6a3" src="https://github.com/user-attachments/assets/fe6424ef-0632-4803-b92b-df165f77114b" />注意：这是非官方的pushplusapp！！！！！</br>
这是一个原生 Android 应用，旨在将 PushPlus OpenAPI 的历史消息同步到本地 SQLite 数据库中，提供更便捷的查询、搜索和备份功能。我更愿意称它为 `pushplushistory`。

A native Android app for syncing PushPlus OpenAPI message history into a local SQLite database.

## 🚀 主要功能 / Features

### 🇨🇳 中文说明

- **数据同步**：
    - 拉取 PushPlus 历史消息。
    - 本地保存到 Android 内置 SQLite 数据库。
    - 支持下拉刷新同步最新消息。
    - 支持同步全部历史消息。
    - 支持自动同步（约每 3 小时拉取一次）。
    - 支持开机后自动恢复同步。
- **消息管理**：
    - **首页自动搜索**：输入关键词后自动筛选消息。
    - **智能摘要**：首页消息摘要自动去掉时间、空白及无用符号。
    - **验证码提取**：自动提取验证码，有验证码时优先显示，点击即可复制。
    - **详情查看**：点击消息进入详情页查看完整内容（无验证码时顶部显示标题）。
- **系统交互**：
    - 支持系统通知栏推送新消息（需开启权限）。
- **数据备份**：
    - 支持导出数据库到文件。
    - 支持从文件导入数据库。
- **个性化**：
    - 支持皮肤切换。
    - 支持语言设置。

### 🇺🇸 English Description

- **Sync & Storage**:
    - Save `User Token` and `SecretKey` locally in app preferences.
    - Request OpenAPI `accessKey` from PushPlus API.
    - Sync latest page or all pages from `/api/open/message/list`.
    - Store messages in local SQLite database `pushplus_history.db`.
    - Auto-sync approximately every 3 hours.
    - Restore auto-sync after device boot.
- **Search & View**:
    - Search by title, content text, topic, or channel.
    - Filter by `update_time` date range.
    - Tap a row to view message detail/raw JSON.
    - Auto-extract verification codes (click to copy).
- **Notifications**:
    - System notification bar push for new messages (Android 13+ requires permission).
- **Backup & Restore**:
    - Export local database with Android document picker.
    - Import a database file, replacing the local database.
- **Customization**:
    - Support skin switching and language settings.

## 📥 安装与使用 / Installation & Usage

### 1. 下载 / Download

Debug APK 位于: `manual-build/PushPlusHistory-debug.apk`

**注意**: 该 APK 为 Debug 签名，仅请求 `android.permission.INTERNET` 权限。

### 2. 首次配置 / First Time Setup

1. 安装并打开 App。
2. 进入“设置”页或跟随设置向导。
3. 点击“密钥设置”，填入以下信息并保存：
    - `User Token`
    - `SecretKey`
4. 如需后台自动同步，请在设置中打开“自动同步”。

### 3. 同步消息 / Sync Messages

- **下拉刷新**: 在首页下拉以同步最新消息。
- **全量同步**: 在设置页点击“同步全部”以拉取所有历史记录。
- **自动同步**: 开启后，App 将每隔约 3 小时在后台尝试同步。

### 4. 通知栏推送 / Notifications

- Android 13 及以上版本首次打开会请求通知权限，**必须允许**才能接收推送。
- 当同步到数据库中不存在的新消息时，会发送系统通知。点击通知可直接打开 App。

### 5. 数据库管理 / Database Management

在“设置”页中：

- **导出数据库**: 将本地消息数据库导出为文件。
- **导入数据库**: 选择本地文件恢复数据库。

## ⚠️ 注意事项 / Notes

- **IP 白名单**: PushPlus IP 白名单需要关闭或者需要你手动添加白名单！
- **内容限制**: App 目前仅存储 `message/list` 返回的列表数据；暂未实现抓取 `/shortMessage/{shortCode}` 的 HTML 详情内容。
- **编码说明**: 为避免手动构建时的编码问题，部分 UI 文本目前使用 ASCII 英文。
  ## 界面预览![Uploading e7597547-d03b-4ec0-b140-4d258cc5c6a3.png…])</br><img width="305" height="651" alt="579cbfd1-d177-4d46-882b-264f75f81cd2" src="https://github.com/user-attachments/assets/59c2c866-c617-4d5b-9b49-970a266b0142" />

