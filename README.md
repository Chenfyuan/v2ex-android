# V2 阅读

非官方 [V2EX](https://www.v2ex.com) Android 客户端。只读浏览主题、节点、用户和提醒，界面是 Material 3，带动态取色、深色模式和悬浮液态玻璃底栏。

> 本项目与 V2EX 官方无关。数据全部来自 V2EX 公开接口，不抓网页登录，也不模拟发帖。

## 功能

- **最热 / 最新 / 节点**：首页悬浮底栏切换（液态玻璃毛玻璃）
- **主题详情**：原生排版（链接、引用、代码块、图片、附言）；回复滚动加载；**只看楼主**；点 `#replyN` / 回复引用可**跳楼**；填令牌后高亮 `@你`
- **收藏与历史**：本地收藏主题 / 节点，自动记录浏览历史；支持 JSON 导出 / 导入备份
- **搜索**：搜本地节点列表、收藏、历史；支持主题数字 ID；完整站内搜索请跳转浏览器（官方无搜索 API）
- **提醒**：个人访问令牌后可读提醒、删单条 / **全部已读**；首页铃铛显示数量角标
- **阅读设置**：正文字号、行距、字重
- **列表缩略图**：开启「自动加载图片」时，有图的主题在列表显示首图
- **节点 / 用户**：节点可收藏；用户主页与最近主题
- **深链接**：`/t/`、`/member/`、`/go/`；分享文案带作者与节点

## 截图

| 首页（浅色） | 首页（深色） | 主题 |
| --- | --- | --- |
| ![home light](docs/shots/5-glassbar-light.png) | ![home dark](docs/shots/6-glassbar-dark.png) | ![topic](docs/shots/3-topic.png) |

## 下载

到 [Releases](https://github.com/Chenfyuan/v2ex-android/releases) 下载最新签名 APK，覆盖安装即可。

最低 Android 8.0（API 26），目标 Android 16（API 37）。

## 个人访问令牌（可选）

V2EX 不提供第三方 OAuth。需要看提醒、或者在节点里一直往下翻页时：

1. 打开 [v2ex.com/settings/tokens](https://www.v2ex.com/settings/tokens)（需先登录网站）
2. 创建一个 Personal Access Token
3. 在 App「设置」里粘贴并保存

令牌只保存在本机，不会上传。不填令牌也能看最热、最新、主题详情和用户主页。

## V2EX API 说明

| 能力 | 接口 | 是否需要令牌 |
| --- | --- | --- |
| 最热 / 最新 | `/api/topics/hot.json`、`latest.json` | 否 |
| 全部节点 | `/api/nodes/all.json` | 否 |
| 主题 / 回复 / 用户 | `/api/topics/show.json`、`replies/show.json`、`members/show.json` | 否 |
| 节点主题翻页 | `/api/v2/nodes/:name/topics` | 是 |
| 提醒 | `/api/v2/notifications` | 是 |

- 限流：每个 IP 每小时约 **600** 次。本 App 对结果做了内存 + 磁盘缓存。
- **没有**官方全文搜索、发帖、回复、感谢、收藏、密码登录接口。搜索在 App 内仅覆盖本地数据；收藏 / 历史为**本机**存储。

## 构建

需要 JDK 17 和 Android SDK。

```bash
./gradlew assembleDebug

V2EX_SIGNING_PROPERTIES=/path/to/signing.properties ./gradlew assembleRelease
```

## 已知限制

- 未填令牌时，节点主题只能看到最新 10 条
- 官方无搜索 API：无法在 App 内做全站全文搜索
- 「全部已读」通过逐条删除提醒实现（API 仅提供 DELETE）
- 毛玻璃模糊依赖 Android 12+；更旧系统退化为半透明磨砂
- 没有发帖 / 回复 / 感谢 / 账号侧收藏

## 技术栈

Kotlin · Jetpack Compose · Material 3 · Room · Coil · jsoup · [Haze](https://github.com/chrisbanes/haze)

## 许可

[MIT](LICENSE)
