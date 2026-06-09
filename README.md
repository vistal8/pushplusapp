特别声明：非pushpus官方app 第三方开发 增加同步所有历史消息，查询消息内容 导出所有历史消息到数据库 也可以导入历史消息  我更愿意称他为pushplushistory
主要功能
拉取 PushPlus 历史消息
本地保存到 Android 内置 SQLite 数据库
首页自动搜索：输入关键词后自动筛选消息
点击消息进入详情页查看完整内容
自动提取验证码，有验证码时优先显示验证码
点击验证码可复制
无验证码时详情页顶部显示消息标题
首页消息摘要自动去掉时间、空白、无用符号
支持下拉刷新同步最新消息
支持同步全部历史消息
支持自动同步，每 3 小时拉取一次
支持系统通知栏推送新消息
支持导出数据库
支持导入数据库
支持皮肤切换
支持语言设置
支持开机后恢复自动同步
首次使用

安装 APK。
打开 App。
进入设置向导或“设置”页。
点击“密钥设置”。
填入：
User Token
SecretKey
保存配置。
如需后台自动同步，打开“自动同步”。
同步消息

首页下拉刷新：同步最新消息
设置页点击“同步全部”：同步所有历史消息
开启自动同步后：大约每 3 小时自动同步一次
通知栏推送

Android 13 及以上首次打开会请求通知权限。
必须允许通知权限。
同步到数据库里没有的新消息时，会发送系统通知。
点击通知会打开 App。
数据库管理

在“设置”页：

“导出数据库”：把本地消息数据库导出到文件
“导入数据库”：从本地文件恢复数据库
# PushPlus History Android

Native Android app for syncing PushPlus OpenAPI message history into a local SQLite database.

## Features

- Save `User Token` and `SecretKey` locally in app preferences.
- Request OpenAPI `accessKey` from `https://www.pushplus.plus/api/common/openApi/getAccessKey`.
- Sync latest page or all pages from `/api/open/message/list`.
- Store messages in local SQLite database `pushplus_history.db`.
- Search by title, content text, topic, or channel.
- Filter by `update_time` date range.
- Tap a row to view message detail/raw JSON.
- Export local database with Android document picker.
- Import a database file, replacing the local database.

## APK

Debug APK output:

`manual-build/PushPlusHistory-debug.apk`

The APK is debug-signed and requests only `android.permission.INTERNET`.

## Notes

- PushPlus OpenAPI IP whitelist still applies. If the phone network IP is not authorized by PushPlus, access-key fetching will fail with IP authorization errors.
- The app currently stores list data returned by `message/list`; it does not fetch `/shortMessage/{shortCode}` HTML details yet.
- UI text is ASCII English to avoid Windows console/source encoding corruption during manual builds.
