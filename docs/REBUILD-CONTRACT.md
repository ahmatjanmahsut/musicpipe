# MusicPipe 控制台重构契约（2026-10-23 · Awwwards/DESIGN.md 合规重设计）

目标：原地重写 router-merge/musicpipe/web 前端（零构建三件套 + 新 css/assets），彻底移除 Element Plus，视觉严格遵循 C:/Users/demo\Documents\design-specs\DESIGN.md（必读全文），后端 src/ 一行不改。行为基线 = 现行 web/index.html + web/app.js（md5 与线上一致，必读全文，所有 API 调用、轮询、WS、字段名、文案映射必须等价保留）。

## A. 文件与所有权（写域互斥）
- app-core 拥有：web/index.html、web/app.js、web/css/pages.css
- css-stylist 拥有：web/css/tokens.css、web/css/base.css、web/css/components.css、web/noise.svg
- 双方都不得改：src/**、web/assets/**（gsap.min.js、ScrollTrigger.min.js、lenis.min.js、icons.js、fonts/*.woff2 已就位）、web/style.css（旧文件保留仅作回滚，新 index.html 不引用它）、/path/to/musicpipe\web、publish/**
- 全局 JS 已可用：gsap、ScrollTrigger、Lenis、MP_ICONS（62 个 Lucide 图标，key 为 kebab-case：search,play,pause,trash-2,refresh-cw,settings,x,upload,download,user,users,link,music,hard-drive,activity,log-out,plus,check,chevron-right,chevron-left,chevron-down,folder,list,wifi,cpu,database,clock,disc-3,share-2,external-link,eye,eye-off,lock,terminal,gauge,loader-circle,circle-check,circle-alert,triangle-alert,arrow-right,arrow-up-right,copy,layers,sparkles,key-round,sliders-horizontal,file-archive,album,hourglass,grid-3x3,mic,power,rotate-ccw,star,tag,wrench,zap,more-vertical,more-horizontal,ban,infinity,radio）。缺图标就改用列表内近义项，不许引入 CDN。

## B. index.html 骨架（app-core）
- head：charset/viewport/title「MusicPipe 控制台」/theme-color #0d0d0f；link rel=preload as=font 引 Space Grotesk 400/500/700 + JetBrains Mono 400/500（/assets/fonts/ 下）；css 顺序 tokens→base→components→pages。
- body 顺序：#mp-loader（.pl-count 0–100 + .pl-brand）→ #mp-cursor（.c-dot/.c-ring/.c-label）→ div#app → 12 个 text/x-template（ID 不变：tpl-login/tpl-dashboard/tpl-search/tpl-tasks/tpl-tracks/tpl-shares/tpl-organize/tpl-sources/tpl-settings/tpl-appusers/tpl-releases/tpl-shell）→ 新增 tpl-modal、tpl-toasts。
- 脚本顺序：/vendor/vue.global.prod.js → /assets/gsap.min.js → /assets/ScrollTrigger.min.js → /assets/lenis.min.js → /assets/icons.js → /app.js。不再引用任何 element-plus 资源。
- Vue x-template 注意：字面双花括号（命名模板提示文案）必须放 span v-pre 内（旧代码此处本就渲染为空，顺手修正）。

## C. 应用结构（app-core · app.js）
完整保留现有 helper：api(path,opts)（401→throw "unauthorized:401"；FormData 不设 Content-Type）、wsState/connectWs（断线退避 ≤30s）、fmtTs/mb/dur/clip、PLAT_NAMES、LOGIN_ERR_ZH、hash 路由、window.__auth/__mprefresh、根挂载流程（/api/auth/status→login|setup|shell）。
新增机制：
1. v-ic 指令：v-ic="'play'" 渲染 svg（viewBox 0 0 24 24, fill=none, stroke=currentColor, stroke-width=1.5, linecap/linejoin=round, aria-hidden）+ MP_ICONS 图形；mounted/updated 双钩子处理 v-if 重建；未知 key 静默+console.warn。
2. mp-toast：globalProperties.$toast(kind,msg)，kind=ok|warn|err（替换全部 ElementPlus.ElMessage，含批量部分失败 warning）；堆栈右上 3.5s 自动消失，gsap transform/opacity 进退场。
3. MP.confirm({title,body,danger})→Promise：用 tpl-modal 单例（替代全部 el-popconfirm 与删除确认）。禁止 window.alert/confirm。
4. 动效层 MP.motion：Lenis 全局平滑滚动（reduced-motion/触屏降级）；splitChars(el) 文字遮罩揭示（行 overflow:hidden + 逐字 translateY 110%→0，stagger .06，ease expo.out，页面 mounted 与路由切换触发）；countUp(el) 统计数字滚动（tabular-nums）；magnet(el) 磁吸（80px 半径 ×0.25，CTA/主导航）；parallax：[data-parallax] yPercent ±12 scrub；marquee：.mp-marquee（CSS keyframes 无缝 + hover 减速；登录页平台跑马灯、dashboard 事件流、shares 空态）；路由切换 0.6s 遮罩 wipe；每页 unmounted 必须 kill 本页 ScrollTrigger 与定时器。reduced-motion 时全部禁用且静态可读。
5. 自定义光标：matchMedia("(pointer:fine)") 且非 reduced-motion 才启用；[data-cursor] hover→ring 56px + 文案（View/Go/Open）；输入/表格不放大。
6. 加载页：document.fonts.ready + window load → 0–100 计数（mono，约 1.2s）→ 遮罩上滑 → 触发首屏 splitChars。

## D. 组件类名契约（双方逐字遵守；css-stylist 实现样式）
- 按钮：.btn；修饰 .btn--cta / .btn--danger / .btn--ghost / .btn--icon；disabled 属性；.btn[data-loading=true]::after 旋转 loader。样式=透明底+1px solid var(--color-line)+大写小标，hover accent 填充文字反色。
- 字段：.field > .field__lb + 原生控件 .inp/.ta/.sel；错误 .field__err。
- 开关：button.sw.sw--on（role=switch aria-checked）。
- 数字输入：.num > .num__inp + button.num__btn。
- 表格：.mp-table（th=label 小标样式；数字/时间列 .t-mono）；行选择 input.ck（表头全选+indeterminate 由 JS 设）；加载态 .mp-table--busy。
- 状态：.tag.tag--ok|warn|err|info|accent；.dot.dot--ok|bad|warn。
- 进度条：.bar > .bar__fill，宽度用 CSS 变量 --bar-w 内联注入，动画只用 transform:scaleX。
- 页签：.tabs > button.tab.tab--on（含 .tab__n 计数）。
- 分页：.pager > .pager__btn/.pager__cur/.pager__sum。
- 模态：.mp-modal > __scrim/__panel/__head/__body/__foot；组件 props title，默认 slot + foot slot，emit close。
- toast：.mp-toast > .mp-toast__item（--ok/--warn/--err）。
- 空态：.empty（文案 + 可选 marquee）。
- 页框架：.page > .page__hero（.page__no 编号「01」、h1、.lede）+ .page__body；分区 .sect > .sect__h；工具行 .bar-tools。
- 壳：header.nav（sticky，mix-blend-difference）：.nav__wordmark、nav a.nav__link（.nav__idx 编号 01–10 + label）、.nav__ws（dot+文案）、.nav__user、.nav__out；footer.foot 超大 wordmark clamp(4rem,14vw,16rem)（全局一处）。
- 光标：#mp-cursor > .c-dot/.c-ring/.c-label。加载页：#mp-loader > .pl-count/.pl-brand。

## E. 视图行为规格（等价迁移；细节以旧文件为准，此处列易漏点）
- login/setup：双模式；isSetup→POST /api/auth/setup；LOGIN_ERR_ZH 映射；成功后更新 window.__auth emit done→shell。设计：满屏 100svh 编辑式场景（编号「00 · CONSOLE」+ clamp hero + PLAT_NAMES 平台 marquee + 背景超大 wordmark + 细线表单卡片）。
- dashboard：GET /api/system/status + /api/events，8s 轮询；count-up 统计：库内曲目/已入库/进行中任务/今日下载/磁盘 pct+剩余(GB)；服务表 5 行（musicpipe workers、rclone cache、WebDAV configured、Navidrome ok+nav_configured、Music Tag Web）；最近事件流 fmtTs/kind/clip(detail,90)。设计：事件流做 marquee 或右侧粘性栏。
- search：GET /api/search/all?keyword&page；results[].list 合并行，rowid=seq-platform-idx；tab=all|platform；quality 选择 auto/flac/320k/128k 默认 auto；下载 POST /api/tasks/download {items:[source,id,name,singer,album,duration,types,img,raw],quality}；行内单下载；page± 翻页；tip 文案（第N页 · 共X条 · ok/total 平台返回（失败平台名））保留。
- tasks：GET /api/tasks 5s 轮询 + ws task 事件触发 load；列 id/type/status/progress/message/speed(mb/s)/error(clip60)/操作；status∈[pending,downloading,uploading,tagging,verifying,organizing]→取消；failed→重试；只看失败(failed+cancelled)；批量 POST bulk-cancel/bulk-delete {ids}；DELETE /api/tasks/finished；toast 计数文案保留。
- tracks：GET /api/tracks?q=；列 title/artist/album/quality/status/remote_path/file_size/操作（预览=modal 内 audio /api/preview/:id；整理仅 status==='uploaded'，POST /:id/organize；刮削 POST /:id/scrape（scraping===id 时按钮 loading；toast 约1分钟生效文案）；删除/批量删除 MP.confirm；批量 errors→warning）；「去整理页」→ location.hash="#organize"。
- shares：POST /api/sharelinks {url}→openPreview(r)；preview GET /:id/preview→prev.link{title,user_id,share_user}；pvUser=link.share_user；pvQuality=link.quality||"flac"；预选择 state==='preview'&&!in_lib（无则全部 preview 行）；commit POST {share_user,quality,keys:ext_key[]}→toast「已入队 N 首下载（限速逐个执行）」；行操作：pending→预览下载；否则 预览/同步(POST /:id/sync json{}，busy=id loading)/暂停恢复(POST /:id/pause)/退订(DELETE，confirm 文案「已下载文件保留」)；状态 tag：active=同步中/pending=待确认/paused=已暂停/expired=链接失效；行内链接 emoji 换 v-ic link；空态文案保留。
- organize：GET /api/organize/incoming（name/size）；auto_organize switch 即时 PUT /api/settings；POST /api/organize/run→toast ok=/failed=（errors console.warn）；命名模板 input+保存 PUT naming_template+实时 preview（artist/album/trackNumber/title/year/quality 替换示例；字面 {{ }} 用 v-pre）；POST /api/system/scan→scanMsg；POST /api/system/refresh 后 loadIncoming；外链 http://{location.hostname}:4533 与 :8003 保留（局域网服务，非 web 端口）。
- sources：隐藏 file input(.js)；POST /api/sources {name,code}（code=file.text()）；列 name/platforms(mono join)/loaded_ok dot/last_error clip80/enabled switch→POST /:id/enable|disable/自测 POST /:id/test json{}（steps→「[平台] 搜索x 取链y(质量)」join「；」；✔/✘ 用 tag 色不用 emoji）/删除 confirm；提示「洛雪社区自定义源，本系统不内置分发」保留。
- settings：四组表单（WebDAV/下载与整理/自动刮削/外部服务）；GET /api/settings.values（值 "***" 与 undefined 跳过）；PUT 前删除空密钥键（webdav_pass/nav_pass/mt_pass/mt_token）；默认值保留（webdav_root=/music、concurrency=3、speed=0、cache_target_gb=10、default_quality=320k、mt_user=admin、mt_sources=netease、mt_media_root=/app/media）；saved hint 2s；testDav=先 PUT s 再 POST /api/webdav/test（成功文案含 JSON.stringify(detail.move)）；testMt=先 PUT mt_user/mt_pass/musictag_url 再 POST /api/musictag/test；数字 .num：1-8 / 0-1048576 step128 / 2-60。
- appusers：GET /api/v1/users?page&limit=20 {items,total}；modal 新增/编辑（编辑禁改用户名；密码留空不改；POST {username,password,role}/PUT {role,password?}；role=user|admin）；删除 confirm（会话失效文案）；统一账号体系 hint 保留。
- releases：GET /api/v1/app/releases {items}（versionName/versionCode/size/md5 clip32/changelog clip60/createdAt/downloadUrl 直链）；上传 modal：versionCode=(list[0]?.versionCode||0)+1；FormData versionName/versionCode/changelog/file；onFile 校验 /.apk$/i 与 ≤300MB（toast error）；发布/删除 toast 保留。设计：版本卡横向滚动 + ScrollTrigger pin（满足六类之一），上传仍走 modal。
- shell：hash 路由十页默认 dashboard；ws dot（1.5s 同步 wsState.ok）；who=__auth.username+（管理员）；退出 POST /api/auth/logout→__mprefresh；菜单图标 v-ic：dashboard=activity, search=search, tasks=download, tracks=music, organize=folder, shares=link, sources=sliders-horizontal, appusers=users, releases=file-archive, settings=settings。

## F. 设计硬约束（DESIGN.md 摘要；冲突以原文为准）
- 一切颜色/间距/字号/圆角/缓动走 CSS 变量；禁魔法数字、禁随意内联 style（仅 --bar-w 类 CSS 变量注入例外）；暗底 #0d0d0f 禁纯黑、文字 #f2f0ec 禁纯白、accent #ff4d2e 面积 ≤5%。
- 字体：--font-sans=Space Grotesk（自托管 woff2，CJK 回退 HarmonyOS Sans SC/PingFang SC/Microsoft YaHei）；--font-mono=JetBrains Mono；标题 clamp 流式字号；数字/编号/时间戳一律 mono + tabular-nums。
- 圆角 2px；间距 8pt scale；营销式区块 padding-block clamp(96px,12vw,192px)，控制台页 hero 定义为 token --pad-console: clamp(64px,8vw,112px) 并注明（数据密度妥协，spec 精神内）。
- 动画只用 transform/opacity/clip-path；prefers-reduced-motion CSS+JS 双降级；focus-visible outline 2px accent offset 3px；全站 grain（body::after /noise.svg opacity .05 steps 动画 pointer-events none）；禁渐变背景/渐变字、毛玻璃卡片（导航滚动后 backdrop-blur(8px)+1px 底边线为 spec 允许例外）、大圆角大投影、emoji 图标、卡片套卡片、三列图标卡阵列、灰占位图、alert()。
- 性能：零新增外部网络请求；ScrollTrigger/定时器页面卸载清理；首屏 JS<200KB gzip。

## G. 验收（联调阶段，Lead 执行）
- grep web 目录无 element-plus/el- 类引用、无外部 CDN；node --check app.js；本地起服务（bundled node + pnpm 安装依赖，PORT=7801，MUSICPIPE_DATA=/path/to/musicpipe\.tmp\mpdata，空库→setup 流程→建临时管理员）；Edge CDP e2e 截 1440/375 到 docs/screenshots/。
- 线上替换由 Lead 执行：远端 tar 备份→pscp 上传→docker compose up -d --build musicpipe→线上 e2e→§12/§10 逐项汇报。
