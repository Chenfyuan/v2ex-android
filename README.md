# V2 阅读

非官方 [V2EX](https://www.v2ex.com) Android 客户端。只读浏览主题、节点、用户和提醒，界面是 Material 3，带动态取色和深色模式。

> 本项目与 V2EX 官方无关。数据全部来自 V2EX 公开接口，不抓网页登录，也不模拟发帖。

## 功能

- **最热 / 最新 / 节点**：首页用悬浮底栏切换（液态玻璃毛玻璃效果）
- **主题详情**：原生排版显示正文和回复（链接、引用、代码块、图片、附言），回复可滚动加载
- **节点**：搜索全部节点，查看节点主题（填令牌后可翻页）
- **用户主页**：头像、签名、网站 / GitHub、最近主题
- **提醒**：个人访问令牌登录后可读提醒、删提醒
- **看图**：全屏缩放
- **设置**：令牌、外观（跟随系统 / 浅色 / 深色）、动态取色、是否自动加载图片、清除缓存
- **站内链接**：主题 / 用户 / 节点在 App 内打开；系统分享的 `v2ex.com/t/...` 也可唤起本 App

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

- 限流：每个 IP 每小时约 **600** 次。本 App 对结果做了内存 + 磁盘缓存，断网时也能看旧内容。
- **没有**官方发帖、回复、感谢、收藏、密码登录接口。开源客户端若实现这些，多半是抓网页，容易违反站点规则，本项目不做。

## 构建

需要 JDK 17 和 Android SDK。

```bash
# Debug
./gradlew assembleDebug

# Release（可选签名）
# 准备 signing.properties：
#   storeFile=/path/to.jks
#   storePassword=...
#   keyAlias=v2ex
#   keyPassword=...
V2EX_SIGNING_PROPERTIES=/path/to/signing.properties ./gradlew assembleRelease
```

产物在 `app/build/outputs/apk/`。

## 已知限制

- 未填令牌时，节点主题只能看到最新 10 条
- 回复接口一次返回全部，长帖初次加载会稍慢
- 毛玻璃模糊依赖 Android 12+（API 31）；更旧的系统会退化为半透明磨砂底
- 没有发帖 / 回复 / 感谢 / 收藏 / 签到
- 图标是临时绘制的，后续会换

## 技术栈

Kotlin · Jetpack Compose · Material 3 · Coil · jsoup · [Haze](https://github.com/chrisbanes/haze)（毛玻璃）

## 许可

[MIT](LICENSE)
