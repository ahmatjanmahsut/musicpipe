# MusicPipe

一个自托管的音乐下载/整理/播放全栈项目，包含四个部分：

| 目录 | 作用 | 技术栈 |
|---|---|---|
| `app/` | Android 播放器（登录注册、播放、歌词、收藏、历史、检查更新） | Kotlin 2.0 + Jetpack Compose + Media3 + Room + Retrofit |
| `server/` | App 的配套更新服务（账号、用户管理、APK 版本发布、歌词代理、Subsonic mock） | TypeScript + Fastify 4 + node:sqlite |
| `web/` | 管理控制台（用户管理 / APK 发布） | Vue 3 + Element Plus + Vite 5 |
| `router/` | 路由器上实际运行的网关（rclone 网盘音乐下载、标签整理、Navidrome 对接、App 网关、内嵌管理台） | Node.js 22 + Fastify 4 |

## 它们的关系

```
Android app ──HTTP──> router (gateway, :7800) ──> rclone mount (WebDAV/网盘)
     │                        ├──> Navidrome (Subsonic API)
     │                        ├──> music-tag-web (标签刮削)
     └──HTTP──> server (APK 更新 / 账号服务)
Web console ──HTTP──> router 内嵌管理台 / server
```

`server/` 是最初按需求文档实现的更新服务；`router/` 是把同一批能力（账号、用户管理、APK 发布、歌词、下载）扩展后真正部署在路由器上的那一套，也是当前线上在用的实现。

## 快速开始

### Android

```bash
cd app
./gradlew assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # 单元测试
```

需要 JDK 17、Android SDK 35。首次打开 App 时在设置页填写服务端地址（默认占位为 `https://music.example.com`）。

### 服务端（router）

```bash
cd router
npm install
node --experimental-sqlite src/index.js   # 默认 :7800
```

环境变量见 `router/src/config.js`（`MUSICPIPE_DATA`、`MUSICPIPE_LOCAL`、`MUSICPIPE_MUSIC`、`RCLONE_RC`、`MUSICTAG_URL`、`NAVIDROME_URL`、`PORT`）。依赖的外部容器：rclone（网盘挂载）、Navidrome、music-tag-web。

### 更新服务（server）

```bash
cd server
pnpm install
pnpm build && pnpm start
```

### 管理控制台（web）

```bash
cd web
pnpm install
pnpm dev     # 开发模式，/api 代理到本机 :7800
pnpm build   # 产物 dist/，可由 router 静态托管
```

## 文档

- [docs/01-架构与API契约.md](docs/01-架构与API契约.md) — 接口/字段/技术栈契约（唯一依据）
- [docs/02-任务树.md](docs/02-任务树.md) — 结构化任务分解
- [docs/03-UI风格规范.md](docs/03-UI风格规范.md) — UI 规范
- [docs/开发报告.md](docs/开发报告.md) — 开发报告（每个版本的需求、根因、修复、验证）
- [docs/路由器整合报告.md](docs/路由器整合报告.md) — 路由器部署与线上验证报告

## 脱敏说明

本仓库是从私有开发仓库导出的公开版本，以下内容已全部替换为占位符：

| 原内容类型 | 占位符 |
|---|---|
| 局域网地址 | `192.168.1.10` |
| 公网域名 | `music.example.com` |
| 主机路径 | `/mnt/data/...` |
| 账号 / 密码 / 主机指纹 | `demo` / `<REDACTED>` |
| 本机用户目录 | `C:/Users/demo/` |

未包含的内容：`docs/shots/` 与 `docs/shots-router/` 下的 UI 截图（含真实账号与曲库信息）、任何数据库文件、构建产物、签名文件、真实部署配置。

导出后已在干净副本上重新执行 `gradlew assembleDebug testDebugUnitTest`：**95/95 测试通过，构建成功**，证明导出的代码是自洽可构建的。
