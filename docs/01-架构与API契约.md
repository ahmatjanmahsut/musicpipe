# MusicPipe App 配套工程 —— 架构与 API 契约（唯一依据）

> 本契约依据 C:/Users/demo/WorkBuddy/MusicPipe-开发方案.md（下称《方案》）编写。
> 《方案》已定义的技术栈、接口风格、数据字段一律沿用；《方案》未覆盖但用户需求必需的接口
> （注册/角色/用户管理/更新服务/歌词查询）按《方案》§4.1 的路由与鉴权惯例设计，并明确标注为「扩展」。
> 任何实现不得偏离本契约；发现契约问题先回报 Lead，不得私自改协议。

---

## 1. 总体架构

~~~
┌───────────────────────────┐        ┌──────────────────────────────────┐
│  Android App (Kotlin/     │  REST  │  musicpipe-appservice (路由器)    │
│  Jetpack Compose)         ├───────►│  Node.js 20 + Fastify + TS       │
│  com.musicpipe.player     │ :7800  │  better-sqlite3 (《方案》§4.1 栈) │
│                           │        │  /api/auth /api/users            │
│  ┌─────────────────────┐  │        │  /api/app/update /api/app/releases│
│  │ Media3 ExoPlayer    │  │        │  /api/lyrics + web/ 管理页托管    │
│  │ Room(历史/收藏)     │  │        └──────────────────────────────────┘
│  │ DataStore(会话/配置)│  │  Subsonic API (《方案》§2.4 公网发布链路)
│  └─────────────────────┘  ├───────────────────────────┐
└───────────────────────────┘                           ▼
                                          ┌──────────────────────────┐
                                          │ Navidrome :4533          │
                                          │ (rclone 挂载 123网盘视图) │
                                          └──────────────────────────┘
~~~

- App 的「账号体系」（注册/登录/角色/用户管理/更新）走 musicpipe-appservice REST（:7800）。
- App 的「音乐播放/浏览/搜索/封面/内嵌歌词/scrobble」走 Navidrome 的 Subsonic API（《方案》明确：客户端只持有 Navidrome Subsonic 凭据，永不接触网盘地址）。
- 播放历史与收藏（我喜欢）按需求为 App 本地持久化（Room），离线可看。

## 2. 技术栈锁定（禁止替换）

| 层 | 技术 | 版本锁定 |
|---|---|---|
| 服务端 | Node.js + Fastify + TypeScript + better-sqlite3 | Node>=20, fastify ^4.28, better-sqlite3 ^12（本机 Node 24 需 v12 预编译二进制）, typescript ^5.6, @fastify/cookie ^9, @fastify/multipart ^8, @fastify/static ^7 |
| Web 管理页 | Vue 3 + Vite + Element Plus（《方案》§2.2） | vue ^3.5, vite ^5.4, element-plus ^2.8, axios ^1.7 |
| Android | Kotlin + Jetpack Compose（DsoMusic 同风格） | Kotlin 2.0.21, AGP 8.7.3, Gradle 8.11.1, compileSdk 35, minSdk 24, Compose BOM 2024.10.01, Media3 1.4.1, Room 2.6.1 (KSP 2.0.21-1.0.28), Retrofit 2.11 + Gson, OkHttp 4.12, Coil 2.7, DataStore 1.1.1, navigation-compose 2.8.4 |
| 播放协议 | Subsonic API（Navidrome 实现） | v=1.16.1, f=json |

仓库结构（交付物）：

~~~
/path/to/musicpipe/
├── docs/            # 本契约 + 任务树 + UI 规范 + 开发报告
├── server/          # musicpipe-appservice（Node20+Fastify+TS+SQLite）
│   ├── src/  (index.ts config.ts db/ routes/ services/ mock/)
│   ├── test/        # node:test 集成测试
│   └── package.json tsconfig.json
├── web/             # Vue3+Vite+Element Plus 管理页（登录/用户管理/APK上传）
├── app/             # Android App（Kotlin+Compose）
├── .ref/DsoMusic    # UI 风格参考（非交付物）
└── .tools/          # JDK/Gradle/Android SDK（非交付物）
~~~

## 3. 服务端 REST API 契约（musicpipe-appservice，默认端口 7800）

### 3.0 通用约定

- 全部 JSON（UTF-8）；请求/响应体均为对象。
- 鉴权：沿用《方案》§4.1「session cookie」——登录成功后下发 Set-Cookie: mp_session=<token>; HttpOnly; SameSite=Lax; Path=/ 。除 POST /api/auth/login 与 POST /api/auth/register 外，所有 /api/* 均要求有效会话，否则 401。
- 角色：role 取值 "admin" 或 "user"。管理员专属接口对普通用户返回 403。
- 首个注册用户自动成为 admin（引导规则；库中无任何用户时生效），其余注册默认 user。
- 错误格式（统一）：
~~~json
{ "error": "<machine_code>", "message": "<人类可读信息>" }
~~~
machine_code 枚举：unauthorized / forbidden / invalid_params / username_taken / user_not_found / wrong_password / cannot_delete_self / last_admin / release_not_found / duplicate_version_code / bad_apk / no_release / lyric_not_found / upstream_error / internal_error
- 成功响应：HTTP 2xx，body 直接是数据对象；列表用 { "items": [...], "total": n }；删除用 204 无 body。

### 3.1 SQLite 表结构（db/migrations/001_init.sql）

~~~sql
CREATE TABLE IF NOT EXISTS users (
  id            INTEGER PRIMARY KEY AUTOINCREMENT,
  username      TEXT NOT NULL UNIQUE,
  password_hash TEXT NOT NULL,          -- scrypt(password, salt, 64) hex
  salt          TEXT NOT NULL,          -- 16B 随机 hex
  role          TEXT NOT NULL DEFAULT 'user' CHECK(role IN ('admin','user')),
  created_at    INTEGER NOT NULL        -- unix ms
);
CREATE TABLE IF NOT EXISTS sessions (
  token_hash TEXT PRIMARY KEY,          -- sha256(session token) hex
  user_id    INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  expires_at INTEGER NOT NULL           -- unix ms，默认 7 天
);
CREATE TABLE IF NOT EXISTS app_releases (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  version_name TEXT NOT NULL,
  version_code INTEGER NOT NULL UNIQUE,
  apk_path     TEXT NOT NULL,           -- 相对 data/apk/
  apk_size     INTEGER NOT NULL,
  md5          TEXT NOT NULL,           -- 32 位小写 hex
  changelog    TEXT NOT NULL DEFAULT '',
  created_at   INTEGER NOT NULL
);
~~~

密码哈希：Node 内置 crypto.scryptSync(password, salt, 64).toString('hex')；会话 token 为 32B 随机 hex，库存其 sha256。

### 3.2 认证（扩展，遵循《方案》§4.1 鉴权惯例）

| 接口 | 鉴权 | 请求 | 成功响应 |
|---|---|---|---|
| POST /api/auth/register | 无 | {username, password} | 201 {id, username, role}；用户名 3-32 位（字母/数字/下划线/中文），密码 >=6 位，否则 400 invalid_params；重名 409 username_taken |
| POST /api/auth/login | 无 | {username, password} | 200 {id, username, role} + Set-Cookie mp_session；用户不存在 404 user_not_found；密码错 401 wrong_password |
| POST /api/auth/logout | 会话 | — | 204，清 cookie 删会话 |
| GET /api/auth/me | 会话 | — | 200 {id, username, role} |

### 3.3 用户管理（扩展；仅 admin，普通用户一律 403 forbidden）

| 接口 | 说明 |
|---|---|
| GET /api/users?page=1&limit=20 | 200 {items:[{id,username,role,created_at}], total}，按 id 升序 |
| POST /api/users | body {username, password, role}（role 仅 admin/user）→ 201 {id,username,role}；重名 409 |
| PUT /api/users/:id | body {password?, role?}（至少一项）→ 200 {id,username,role}；不存在 404 user_not_found；把最后一名 admin 降级 409 last_admin |
| DELETE /api/users/:id | 204；删自己 400 cannot_delete_self；删最后一名 admin 409 last_admin；不存在 404 user_not_found |

### 3.4 更新服务（扩展；对应需求 5「更新服务部署在自有路由器上」）

| 接口 | 鉴权 | 说明 |
|---|---|---|
| GET /api/app/update/latest | 会话 | 200 最新发布版：{versionName, versionCode, md5, size, changelog, downloadUrl, publishedAt}；downloadUrl 形如 /api/app/releases/download?versionCode=<n>；无任何发布 404 no_release |
| POST /api/app/releases | admin | multipart：字段 versionName(string) versionCode(int) changelog(string,可空) + 文件字段 file(必须 .apk，<=200MB)。服务端计算 md5/size 存入 data/apk/ → 201 {id, versionName, versionCode, md5, size, changelog, downloadUrl, createdAt}；versionCode 重复 409 duplicate_version_code；非 apk/超大小 400 bad_apk |
| GET /api/app/releases?page=1&limit=20 | admin | 200 {items:[...], total}，按 versionCode 降序 |
| GET /api/app/releases/download?versionCode=:n | 会话 | 200 流式 APK，Content-Type application/vnd.android.package-archive，带 Content-Length 与 X-Apk-Md5 头；不存在 404 release_not_found |
| DELETE /api/app/releases/:id | admin | 204，同时删磁盘文件；不存在 404 release_not_found |

App 端比对规则：latest.versionCode > BuildConfig.VERSION_CODE → 提示更新；相等或更小 → 「已是最新版本」。

### 3.5 歌词（扩展；包装《方案》§2.3 MusicSource.getLyric 语义）

| 接口 | 鉴权 | 说明 |
|---|---|---|
| GET /api/lyrics?platform=:p&songId=:id | 会话 | 200 {platform, songId, lrc}（LRC 文本，可含多时间标签）；无歌词 404 lyric_not_found |
| GET /api/lyrics/search?title=:t&artist=:a | 会话 | 按歌曲信息模糊匹配（title 必传）→ 200 {title, artist, lrc, matchedBy}；无匹配 404 lyric_not_found |

服务端以可插拔 LyricProvider 实现（默认实现：内置 data/lyrics/*.lrc 目录 + 归一化标题/艺人匹配；预留接入《方案》音源 getLyric 的接口位）。
App 歌词获取顺序：① Subsonic getLyricsBySongId / getLyrics（Navidrome 内嵌/刮削歌词）→ ② GET /api/lyrics/search?title&artist → ③ 都失败：播放页显示降级提示条「未匹配到歌词」，播放不受影响（需求 3）。

### 3.6 系统

- GET /api/system/status（会话）：200 {ok:true, version:"<pkg版本>", uptimeSec, db:"ok", releases:<数量>}（《方案》§4.1 system.ts 的精简实现）。
- 静态托管：web/dist 构建产物挂到 /（《方案》：前端构建进 server、由 server 静态托管）；SPA fallback 到 index.html。
- 标注：3.2–3.5 为《方案》未覆盖、按用户需求新增的「扩展接口」，路由前缀、鉴权、错误风格与《方案》§4.1 完全一致。

## 4. Subsonic API 使用约定（Navidrome :4533，《方案》§2.4 发布链路）

- 认证参数（每个请求追加）：u=<用户名>&t=md5(<密码>+<salt>)&s=<salt>&v=1.16.1&c=MusicPipeApp&f=json，salt 为 8-16 位随机 hex。
- 响应信封：{"subsonic-response":{"status":"ok|failed","version":"...", ...}}；failed 时含 error:{code,message}（code 40=未授权, 41=token 错, 70=请求数据错）。
- App 使用端点：

| 用途 | 端点 |
|---|---|
| 连通性测试 | ping.view |
| 浏览（首页随机/专辑列表） | getRandomSongs.view、getAlbumList2.view?type=random或newest或frequent&size=30 |
| 搜索 | search3.view?query=&songCount=30&albumCount=10&artistCount=0 |
| 播放流 | stream.view?id=<songId>&bitrate=<kbps 可省>（ExoPlayer 数据源 URL） |
| 封面 | getCoverArt.view?id=<artId>（Coil 加载，App 磁盘缓存→离线可显示） |
| 歌词 | getLyricsBySongId.view?songId=（优先）/ getLyrics.view?artist=&title=（兜底） |
| 播放上报 | scrobble.view?id=<songId>（播放开始 >5 秒后上报一次） |
| 服务器端收藏(可选联动) | star.view / unstar.view / getStarred2.view（本地收藏为主，联网时尽力同步，失败不影响本地） |

- 歌曲字段（App 域模型映射自 Subsonic child 对象）：id, title, artist, album, duration(秒), coverArt, suffix, bitRate, size。

## 5. Android 本地数据契约（Room, DB 名 musicpipe.db, version 1）

~~~
Entity history_tracks  : id Long PK自增, songId String, title String, artist String, album String,
                         durationMs Long, coverUrl String?, platform String(默认 subsonic),
                         subsonicId String?, playedAt Long(unix ms)   // 同 songId 重复播放→更新 playedAt 置顶
Entity favorite_tracks : songId String PK, title, artist, album, durationMs, coverUrl?, platform,
                         subsonicId?, addedAt Long                     // 「我喜欢」本地收藏（需求4）
~~~

DataStore（preferences, 文件名 musicpipe_prefs）键：server_url(默认 http://192.168.1.10:7800)、subsonic_url(默认 http://192.168.1.10:4533)、subsonic_username、subsonic_password、session_cookie(mp_session 值)、last_user_id、last_username、last_role、theme_mode(system/light/dark)。

域模型（ui/data 共用）：
~~~kotlin
data class Song(val id: String, val title: String, val artist: String, val album: String,
                val durationMs: Long, val coverUrl: String?, val platform: String, val subsonicId: String?)
data class LyricLine(val timeMs: Long, val text: String)
data class AppUser(val id: Long, val username: String, val role: String)  // role: admin | user
~~~

## 6. App 更新流程（需求 5 时序）

~~~
设置页[检查更新] → GET {server_url}/api/app/update/latest (带会话 cookie)
  ├─ 404 no_release / 网络失败 → Toast「检查更新失败，请重试」
  ├─ versionCode <= BuildConfig.VERSION_CODE → 对话框「已是最新版本 vX.Y.Z」
  └─ versionCode >  当前 → 更新对话框（新版本号/更新说明/大小）[立即更新][稍后]
       → [立即更新] 下载 GET downloadUrl（OkHttp 流式→ cacheDir/update/musicpipe-<vc>.apk，进度回调）
       → 校验 MD5(文件) == latest.md5（不一致：删文件，对话框「文件校验失败」[重试][取消]，旧版本不受影响）
       → Android 8+ 检查 canRequestPackageInstalls()：false → 引导 Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES
          （附说明对话框），返回后复检
       → FileProvider.getUriForFile + ACTION_VIEW (application/vnd.android.package-archive, FLAG_GRANT_READ_URI_PERMISSION)
       → 下载失败 → 「下载失败」[重试][取消]；已下载完整且校验通过的文件二次进入直接复用
~~~

## 7. 默认配置与部署

- 路由器地址默认 192.168.1.10：App 默认 server_url=http://192.168.1.10:7800，subsonic_url=http://192.168.1.10:4533（App 设置页可改）。
- 服务端环境变量：PORT(默认7800)、MUSICPIPE_DATA(默认 ./data)、SESSION_TTL_MS(默认 7 天)。
- AndroidManifest 权限：INTERNET、ACCESS_NETWORK_STATE、REQUEST_INSTALL_PACKAGES、POST_NOTIFICATIONS(33+播放通知)、WAKE_LOCK、FOREGROUND_SERVICE、FOREGROUND_SERVICE_MEDIA_PLAYBACK。
- FileProvider authorities：applicationId 加 .fileprovider 后缀，paths 含 cache-path update/ 目录。
