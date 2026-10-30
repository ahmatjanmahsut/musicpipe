# MusicPipe / MTmusic

一个自托管的音乐「下载 → 整理 → 播放 → 管理」全栈项目。四个子项目同仓库维护：

| 目录 | 作用 | 技术栈 |
|---|---|---|
| `app/` | Android 播放器（登录注册、播放、歌词、收藏、历史、艺人/歌单/专辑轴、均衡器、车载歌词、检查更新） | Kotlin 2.0 + Jetpack Compose + Media3 + Room + Retrofit |
| `router/` | 路由器 / NAS 上实际运行的网关（网盘音乐下载、标签整理、Navidrome 对接、App 网关、内嵌管理台、音频缓存） | Node.js 22 + Fastify 4 + 零构建 Vue 3 |
| `server/` | App 配套服务（账号、用户管理、APK 版本发布、歌词代理、Subsonic mock） | TypeScript + Fastify 4 + SQLite |
| `web/` | 独立管理控制台（用户管理 / APK 发布） | Vue 3 + Element Plus + Vite 5 |

## 它们的关系

```
Android app ──HTTP──> router (gateway, :7800) ──> rclone mount (WebDAV/网盘)
     │                        ├──> Navidrome (Subsonic API)
     │                        ├──> music-tag-web (标签刮削)
     │                        └──> 本地音频缓存 (LRU)
     └──HTTP──> server (APK 更新 / 账号服务)
Web console ──HTTP──> router 内嵌管理台 / server
```

`router/` 是当前线上在用的那一套（账号、用户管理、APK 发布、歌词、下载、缓存一体）；`server/` 是配套的独立更新服务实现，便于单独部署或二次开发。

## 当前版本

| 组件 | 版本 |
|---|---|
| Android App（品牌名 MTmusic） | 1.20.0（versionCode 20） |
| router 网关 | 1.0.0（随 App 版本一同发布） |
| server 更新服务 | 0.1.0 |

Release 资产：

- `MTmusic-v1.20.0.apk` — Android 安装包（`debug` 签名，仅个人自用/内测分发，请勿用于商店上架）
- `musicpipe-backend-v1.20.0.zip` — 后端源码包（`router/` + `server/` + `docs/`）

## 快速开始

### Android

```bash
cd app
./gradlew assembleDebug        # 产物 app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # 单元测试（162 个用例）
```

需要 JDK 17、Android SDK 35。首次打开 App 时在登录页或设置页填写服务端地址，默认占位为 `https://music.example.com`，局域网可填 `http://192.168.1.10:7800`。

### 网关（router）

```bash
cd router
npm install
node --experimental-sqlite src/index.js     # 默认 :7800
```

环境变量见 `router/src/config.js`（`MUSICPIPE_DATA`、`MUSICPIPE_LOCAL`、`MUSICPIPE_MUSIC`、`RCLONE_RC`、`MUSICTAG_URL`、`NAVIDROME_URL`、`PORT`）。部署示例：`router/docker-compose.example.yml` + `router/.env.example`。依赖外部容器：rclone（网盘挂载）、Navidrome、music-tag-web。

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

- [docs/01-架构与API契约.md](docs/01-架构与API契约.md) — 接口 / 字段 / 技术栈契约
- [docs/02-任务树.md](docs/02-任务树.md) — 结构化任务分解
- [docs/03-UI风格规范.md](docs/03-UI风格规范.md) — UI 规范
- [docs/开发报告.md](docs/开发报告.md) — 逐版本的需求、根因、修复、验证记录
- [docs/路由器整合报告.md](docs/路由器整合报告.md) — 网关部署与线上验证报告
- [docs/STYLE-SYSTEM-CONTRACT.md](docs/STYLE-SYSTEM-CONTRACT.md) / [docs/REBUILD-CONTRACT.md](docs/REBUILD-CONTRACT.md) — 前端改造契约
- [CHANGELOG.md](CHANGELOG.md) — 版本变更记录
- [FRONTEND_GUIDE.md](FRONTEND_GUIDE.md) — 前端设计与验收规范（本项目前端施工依据）

## 脱敏说明

本仓库是从私有开发仓库导出的公开版本，所有个人 / 基础设施信息均已替换为占位符：

| 原内容类型 | 占位符 |
|---|---|
| 公网域名 | `music.example.com` |
| 局域网地址 | `192.168.1.10` |
| 主机路径 | `/mnt/data/...`、`/path/to/musicpipe` |
| 账号 / 密码 / 主机指纹 | `demo` / `<REDACTED>` |
| 本机用户目录 | `C:/Users/demo/` |

未包含的内容：UI 截图（含真实账号与曲库信息）、任何数据库 / 运行时数据、构建产物、签名文件、真实部署配置（`.env`、Caddy 证书、Navidrome / rclone / music-tag 配置）。

导出后在干净副本上重新验证过：

- `gradlew assembleDebug` → **BUILD SUCCESSFUL**（产物 22.4 MB，`versionName=1.20.0` / `versionCode=20`）
- 单元测试 → **162 / 162 通过**

## 许可

个人项目，仅供学习与自托管参考。第三方依赖遵循各自许可证；请自行确保音乐来源合法。
