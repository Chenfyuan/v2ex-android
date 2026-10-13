# V2 阅读

非官方 [V2EX](https://www.v2ex.com) Android 客户端。只读浏览主题、节点、用户和提醒，界面是 Material 3，带动态取色、深色模式和悬浮液态玻璃底栏。

> 本项目与 V2EX 官方无关。数据全部来自 V2EX 公开接口，不抓网页登录，也不模拟发帖。

## 功能

- **最热 / 最新 / 节点 / 收藏**：悬浮液态玻璃底栏；可在设置里调整顺序、默认标签，并把收藏放到底栏
- **主题详情**：原生排版；只看楼主；跳楼；`@你` 高亮；**记住阅读位置**（再次打开回到上次楼层）
- **收藏与历史**：本地收藏主题 / 节点，浏览历史；JSON 备份导入导出
- **屏蔽**：屏蔽用户或节点后，列表里不再显示
- **搜索**：本地节点 / 收藏 / 历史 / 主题 ID；全站搜索请跳转浏览器（官方无搜索 API）
- **提醒**：令牌后可读提醒、全部已读；铃铛角标
- **阅读设置**：字号 / 行距 / 字重
- **离线缓存**：已打开的主题正文和回复缓存约 7 天，弱网优先用缓存
- **限流提示**：中文说明剩余次数与大约恢复时间，可点重试
- **预测性返回**：Android 14+ 边缘滑动预览上一页
- **深链接**与分享；列表缩略图；开屏与应用图标

## 截图

| 首页（浅色） | 首页（深色） | 主题 |
| --- | --- | --- |
| ![home light](docs/shots/5-glassbar-light.png) | ![home dark](docs/shots/6-glassbar-dark.png) | ![topic](docs/shots/3-topic.png) |

## 下载

到 [Releases](https://github.com/Chenfyuan/v2ex-android/releases) 下载最新签名 APK。

最低 Android 8.0（API 26），目标 Android 16（API 37）。

## 个人访问令牌（可选）

1. 打开 [v2ex.com/settings/tokens](https://www.v2ex.com/settings/tokens)
2. 创建令牌，粘贴到 App「设置」

令牌只保存在本机。

## V2EX API 说明

| 能力 | 是否需要令牌 |
| --- | --- |
| 最热 / 最新 / 节点 / 主题 / 回复 / 用户 | 否 |
| 节点主题翻页、提醒 | 是 |

- 限流：每 IP 约 **600** 次 / 小时
- **没有**官方全文搜索、发帖、回复、感谢、账号收藏、密码登录。本机收藏 / 屏蔽 / 历史不经过服务器。

## 构建

```bash
./gradlew assembleDebug
V2EX_SIGNING_PROPERTIES=/path/to/signing.properties ./gradlew assembleRelease
```

## 已知限制

- 未填令牌时节点主题最多约 10 条
- 官方无搜索 API
- 「全部已读」为逐条删除提醒
- 毛玻璃需 Android 12+
- 数据库升级使用破坏性迁移（清除本地收藏 / 历史以外的新表时请留意版本变更说明）

## 技术栈

Kotlin · Jetpack Compose · Material 3 · Room · Coil · jsoup · Haze · SplashScreen

## 许可

[MIT](LICENSE)
