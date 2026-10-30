# STYLE-SYSTEM-CONTRACT.md — Web 控制台六套风格（与 App 对齐）

> 用户需求（m00693）：**跟 app 一样，设计几种页面风格用来替换**。
> App 侧已有一套完整风格体系：`AppStyle` 6 套 ×（暗/亮）×（跟随系统），
> 规格唯一真相来源 = `app/src/main/java/com/musicpipe/player/ui/theme/StyleSpecs.kt`。
> 本任务把它移植到 Web 控制台，**页面/组件代码不认识风格，只读 token**。

## 0. 风格清单（id 必须与 App 完全一致，用于 localStorage 兼容）

| id | 名称 | tagline（设置页展示原文，取自 App） |
|---|---|---|
| `awwwards` | Editorial | 暗底 · 噪点 · 硬边 · 编辑排版 |
| `classic` | Classic | 红白配色 · 圆角卡片 · 旧版 MusicPipe |
| `material` | Material You | Google 官方 · 色调表面 · 大圆角 |
| `cupertino` | Cupertino | Apple 风格 · 分组列表 · 系统蓝 |
| `shadcn` | Shadcn | GitHub 最多 star · 中性 · 细边框 |
| `brutal` | Brutalist | 粗描边 · 零圆角 · 高对比 |

默认风格 = `awwwards`（= DESIGN.md 已验收的版本，**视觉必须零变化**）。
默认明暗 = `system`（跟随 `prefers-color-scheme`）。

> **DESIGN.md 的约束范围**：DESIGN.md 是「新项目前端」规范，本项目按用户指令把
> Awwwards/Editorial 作为默认皮肤完整保留。其余 5 套风格是对齐 App 各自设计语言
> （Material You / HIG / shadcn / Neubrutalism / 旧版）的**显式用户选择**，
> 与 App 保持同一套 token 规则（形状/字重/字距/密度/动效随风格变），不适用
> DESIGN.md 的「2px 圆角、禁大圆角」等具体数值，但以下铁律**所有风格都要守**：
> - 颜色/间距/字号/圆角/缓动**只能引用 token**，组件文件零色值字面量（唯一例外：`css/skins.css`）
> - 只用本地资源，零外链字体/CDN；图标只用 Lucide 1.5px 子集
> - `prefers-reduced-motion` 全风格降级；focus-visible 全风格可见
> - 首屏 JS gzip ≤ 200KB；无渐变背景/无毛玻璃卡片（Material/Cupertino 的 tonal 表面用纯色 token 实现，不用 backdrop-filter 大面积铺）

## 1. 文件所有权（写域互斥，不得越界）

| 文件 | 负责人 | 说明 |
|---|---|---|
| `css/tokens.css` | **Lead**（已完成） | 结构 token + Editorial/暗色默认皮肤 + 角色变量（`--r-card/--r-input/--r-btn`、`--density`、`--type-scale`、`--head-scale`、`--tab-*`、`--btn-*`、`--cta-*`、`--card-elev`、`--grain-*`） |
| `css/skins.css` | **skin-author**（新建） | 6 套风格 × 暗/亮 的变量覆盖 + 风格选择器预览用 `--pv-*` + `html[data-scheme="light"]` 的状态色 / color-scheme |
| `css/base.css`、`css/components.css`、`css/pages.css` | **css-porcelain** | 把仍写死的风格相关值（字重/字距/大写/行高/圆角角色/线宽/时长/字号/投影/噪点/tab 指示/按钮填充）全部改为读 token；新增风格选择器 UI 的 CSS（`.sty-*`、`.seg-*`） |
| `index.html`、`app.js` | **app-wiring** | `<head>` 反 FOUC 引导脚本、`css/skins.css` 引入、设置页「界面风格」区块、`MP.style` 模块（读取/持久化/应用/系统明暗监听/刷新 meta theme-color） |
| 验证、截图、文档、部署 | **Lead** | 矩阵探针 + 截图 + 开发报告 + 上线 |

**规则**：只改自己名下的文件；需要别人文件配合时，写进 `deliverable.md` 的「需要 Lead 协调」段，不要直接改。

## 2. token 契约（必须逐字使用这些变量名）

### 2.1 颜色（9 核心 + 3 派生 + 8 状态）
`--color-paper`、`--color-paper-2`、`--color-ink`、`--color-ink-2`、`--color-ink-3`、`--color-line`、`--color-accent`、`--color-accent-soft`、`--color-cta-ink`
派生：`--color-scrim`（模态遮罩）、`--color-nav-bg`（导航底）
状态（8）：`--color-ok/-warn/-err/-info` + `*-soft`

### 2.2 形状 / 线宽 / 投影
`--r-card`、`--r-input`、`--r-btn`、`--r-pill`、`--bw`、`--bw-2`、`--card-elev`
（`--r-2` 仅保留给「必须硬 2px」的几何件：进度条、开关轨道、勾选框；圆点用 `50%` 或 `--r-pill`）

### 2.3 字形
`--font-sans`、`--font-title`、`--font-mono`、`--fw-title`、`--fw-body`、`--fw-label`、
`--lh-title`、`--lh-body`、`--lh-ui`、`--ls-title`、`--ls-label`、`--label-tt`（`uppercase`/`none`）、
`--type-scale`、`--head-scale`

### 2.4 间距 / 动效 / 形态开关
`--density`、`--s-05…--s-12`（已由 tokens.css 用 `calc(px × --density)` 派生）
`--dur-fast`、`--dur-base`、`--dur-slow`、`--dur-reveal`、`--dur-wipe`、`--ease-*`
`--grain-opacity`、`--grain-invert`
`--tab-bg`、`--tab-fg`、`--tab-line`、`--tab-line-h`
`--btn-bg`、`--btn-fg`、`--btn-bd`、`--btn-hover-bg`、`--btn-hover-filter`
`--cta-bg`、`--cta-fg`、`--cta-bd`

## 3. 六套风格规格表（源自 App StyleSpecs.kt，已换算成 CSS 值）

### 3.1 调色板 · 暗色
| token | awwwards | classic | material | cupertino | shadcn | brutal |
|---|---|---|---|---|---|---|
| paper | #0d0d0f | #121212 | #141218 | #000000 ⚠ | #09090b | #0a0a0a |
| paper-2 | #141417 | #1e1e1e | #211f26 | #1c1c1e | #18181b | #1a1a1a |
| ink | #f2f0ec | #f5f5f5 | #e6e0e9 | #ffffff ⚠ | #fafafa | #fafafa |
| ink-2 | rgba(242,240,236,.62) | rgba(245,245,245,.70) | rgba(230,224,233,.70) | rgba(255,255,255,.60) | rgba(250,250,250,.70) | rgba(250,250,250,.70) |
| ink-3 | rgba(242,240,236,.38) | rgba(245,245,245,.40) | rgba(230,224,233,.50) | rgba(255,255,255,.30) | rgba(250,250,250,.44) | rgba(250,250,250,.50) |
| line | rgba(242,240,236,.12) | rgba(255,255,255,.12) | rgba(202,196,208,.12) | rgba(84,84,88,.12) | #27272a | #fafafa |
| accent | #ff4d2e | #ff6b5e | #d0bcff | #0a84ff | #fafafa | #ff6b35 |
| accent-soft | rgba(255,77,46,.16) | rgba(255,107,94,.16) | rgba(208,188,255,.16) | rgba(10,132,255,.16) | rgba(250,250,250,.12) | rgba(255,107,53,.20) |
| cta-ink | #0d0d0f | #1a1a1a | #381e72 | #ffffff | #09090b | #0a0a0a |
| scrim | rgba(13,13,15,.70) | rgba(18,18,18,.70) | rgba(20,18,24,.70) | rgba(0,0,0,.60) | rgba(9,9,11,.70) | rgba(10,10,10,.70) |
| nav-bg | rgba(13,13,15,.72) | rgba(18,18,18,.72) | rgba(20,18,24,.72) | rgba(28,28,30,.72) | rgba(9,9,11,.72) | rgba(10,10,10,.72) |

⚠ Cupertino 暗色是 Apple HIG 的纯黑/纯白（App 也如此）。仅该风格允许，其余风格仍禁纯黑纯白。

### 3.2 调色板 · 亮色
| token | awwwards | classic | material | cupertino | shadcn | brutal |
|---|---|---|---|---|---|---|
| paper | #f4f2ed | #f9f9f9 | #fffbfe | #f2f2f7 | #ffffff | #f5f0e1 |
| paper-2 | #eae7e0 | #ffffff | #f3edf7 | #ffffff | #f4f4f5 | #ffffff |
| ink | #101012 | #1a1a1a | #1c1b1f | #000000 ⚠ | #09090b | #000000 ⚠ |
| ink-2 | rgba(16,16,18,.62) | rgba(26,26,26,.60) | rgba(28,27,31,.70) | rgba(60,60,67,.60) | rgba(9,9,11,.70) | rgba(0,0,0,.70) |
| ink-3 | rgba(16,16,18,.38) | rgba(26,26,26,.40) | rgba(28,27,31,.50) | rgba(60,60,67,.30) | rgba(9,9,11,.44) | rgba(0,0,0,.50) |
| line | rgba(16,16,18,.14) | rgba(0,0,0,.12) | rgba(73,69,79,.12) | rgba(60,60,67,.12) | #e4e4e7 | #000000 |
| accent | #ff4d2e | #e53935 | #6750a4 | #007aff | #18181b | #ff4911 |
| accent-soft | rgba(255,77,46,.16) | rgba(229,57,53,.12) | rgba(103,80,164,.12) | rgba(0,122,255,.12) | rgba(24,24,27,.12) | rgba(255,73,17,.20) |
| cta-ink | #ffffff | #ffffff | #ffffff | #ffffff | #fafafa | #ffffff |
| scrim | rgba(244,242,237,.72) | rgba(249,249,249,.75) | rgba(255,251,254,.72) | rgba(242,242,247,.75) | rgba(255,255,255,.80) | rgba(245,240,225,.80) |
| nav-bg | rgba(244,242,237,.80) | rgba(249,249,249,.82) | rgba(255,251,254,.80) | rgba(242,242,247,.82) | rgba(255,255,255,.82) | rgba(245,240,225,.85) |

**亮色状态色（所有风格共用一套，写在 `html[data-scheme="light"]` 块）**
`--color-ok:#2f7d5e; --color-warn:#8a6410; --color-err:#a13246; --color-info:#3f6285;`
soft：`rgba(47,125,94,.10) / rgba(138,100,16,.10) / rgba(161,50,70,.10) / rgba(63,98,133,.10)`
（暗色状态色沿用 tokens.css 的 :root 值，无需重写）

### 3.3 形状 / 投影
| 风格 | `--r-card` | `--r-input` | `--r-btn` | `--r-pill` | `--bw` | `--card-elev` |
|---|---|---|---|---|---|---|
| awwwards | 2px | 2px | 2px | 999px | 1px | none |
| classic | 12px | 10px | 999px | 999px | 0px | 暗 `0 2px 10px rgba(0,0,0,.35)` / 亮 `0 2px 10px rgba(0,0,0,.10)` |
| material | 16px | 4px | 999px | 999px | 0px | none |
| cupertino | 12px | 10px | 12px | 999px | 1px | none |
| shadcn | 6px | 6px | 6px | 999px | 1px | 暗 `0 1px 2px rgba(0,0,0,.40)` / 亮 `0 1px 2px rgba(0,0,0,.06)` |
| brutal | 0px | 0px | 0px | 0px | 2px | `4px 4px 0 var(--color-line)` |

> brutal 的 `--r-pill:0` 会让状态圆点变方块 —— 这是 Neubrutalism 的刻意形态（App 同款）。
> 自定义光标环（`.c-ring`/`.c-dot`）**永远用 50%**，不受 `--r-pill` 影响。

### 3.4 字形
| 风格 | `--font-sans` | `--font-mono` | `--fw-title` | `--fw-body` | `--fw-label` | `--lh-title` | `--lh-body` | `--ls-title` | `--ls-label` | `--label-tt` | `--type-scale` | `--head-scale` |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| awwwards | Space Grotesk 栈（tokens 现值） | JetBrains Mono 栈 | 500 | 400 | 500 | .98 | 1.8 | -.03em | .12em | uppercase | 1 | 1 |
| classic | system 栈（见下） | 同 sans | 600 | 400 | 500 | 1.15 | 1.5 | -.01em | .0125em | none | 1 | .62 |
| material | system 栈 | 同 sans | 500 | 400 | 500 | 1.15 | 1.5 | 0em | .031em | none | 1 | .62 |
| cupertino | Apple 栈（见下） | 同 sans | 600 | 400 | 500 | 1.12 | 1.4 | -.02em | 0em | none | 1 | .8 |
| shadcn | system 栈 | JetBrains Mono 栈 | 600 | 400 | 500 | 1.15 | 1.5 | -.025em | 0em | none | .95 | .62 |
| brutal | Space Grotesk 栈 | JetBrains Mono 栈 | 700 | 500 | 500 | 1.0 | 1.5 | -.03em | .094em | uppercase | 1 | 1 |

`system 栈`：`system-ui, -apple-system, "Segoe UI", Roboto, "HarmonyOS Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif`
`Apple 栈`：`-apple-system, BlinkMacSystemFont, "SF Pro Text", "Segoe UI", "HarmonyOS Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif`
（全部是本地系统字体，零下载；Space Grotesk / JetBrains Mono 已自托管，仅编辑风/粗野风/shadcn 数字用）

### 3.5 动效
| 风格 | `--dur-fast` | `--dur-base` | `--dur-slow` | `--dur-reveal` | `--dur-wipe` |
|---|---|---|---|---|---|
| awwwards | .2s | .45s | .8s | 1.2s | .6s |
| classic | .15s | .3s | .5s | .7s | .4s |
| material | .2s | .3s | .5s | .7s | .4s |
| cupertino | .25s | .35s | .6s | .8s | .45s |
| shadcn | .15s | .2s | .3s | .5s | .3s |
| brutal | .12s | .2s | .32s | .5s | .25s |

### 3.6 形态开关（chrome）
| 风格 | `--grain-opacity` | `--grain-invert`（亮色） | 导航选中态 | 按钮主形态 |
|---|---|---|---|---|
| awwwards | .05 | `invert(1)` | 下划线：`--tab-bg:transparent; --tab-fg:var(--color-accent); --tab-line:var(--color-accent)` | 描边（tokens 默认） |
| classic | 0 | `invert(1)` | 药丸：`--tab-bg:var(--color-accent-soft); --tab-fg:var(--color-accent); --tab-line:transparent` | 实心：`--btn-bg:var(--color-accent); --btn-fg:var(--color-cta-ink); --btn-bd:transparent; --btn-hover-bg:var(--color-accent); --btn-hover-filter:brightness(1.08)` |
| material | 0 | `invert(1)` | 药丸（同上） | 实心（同上） |
| cupertino | 0 | `invert(1)` | 仅着色：`--tab-bg:transparent; --tab-fg:var(--color-accent); --tab-line:transparent` | 实心（同上） |
| shadcn | 0 | `invert(1)` | 仅着色（同上） | 实心（同上） |
| brutal | .05 | `invert(1)` | 下划线（同 awwwards） | 实心（同上） |

暗色下 `--grain-invert: invert(0)`，亮色下 `invert(1)`（噪点图是白点，亮底要翻黑）。
`--density`：awwwards/classic/material/brutal = 1；cupertino = 1.05；shadcn = .9。

### 3.7 选择器预览用 token（`--pv-*`，供风格卡片画迷你预览）
在 `css/skins.css` 里为每个 id 出一组（**固定用暗色各风格主色**，仅 4+2 个值，允许与正式块重复）：
```css
[data-prev="classic"]{--pv-paper:#121212;--pv-ink:#f5f5f5;--pv-accent:#ff6b5e;--pv-line:rgba(255,255,255,.12);--pv-r:12px;--pv-bw:0px}
```
六个 id 都要有（awwwards: paper#0d0d0f ink#f2f0ec accent#ff4d2e line rgba(242,240,236,.12) r 2px bw 1px）。

## 4. 选择器 UI 契约（app-wiring 出结构，css-porcelain 出样式）

放在**设置页最顶部**（「界面风格」区块排在 123 网盘之前），结构固定：

```html
<section class="sect set__group set__g-style">
  <h2 class="sect__h">界面风格</h2>
  <p class="hint">与 App 同一套风格体系。选择即时生效并记住本机偏好。</p>
  <div class="sty-grid" role="radiogroup" aria-label="界面风格">
    <button type="button" class="sty" role="radio" :aria-checked="..." :data-prev="s.id" :class="{'sty--on': style === s.id}" @click="pickStyle(s.id)">
      <span class="sty__prev" aria-hidden="true">
        <span class="sty__pv-bar"></span><span class="sty__pv-line"></span><span class="sty__pv-btn">Aa</span>
      </span>
      <span class="sty__name">{{ s.name }}</span>
      <span class="sty__tag">{{ s.tagline }}</span>
    </button>
  </div>
  <div class="seg" role="group" aria-label="明暗模式">
    <button type="button" class="seg__i" :aria-pressed="..." @click="pickScheme('system')">跟随系统</button>
    <button type="button" class="seg__i" @click="pickScheme('dark')">深色</button>
    <button type="button" class="seg__i" @click="pickScheme('light')">浅色</button>
  </div>
</section>
```

app-wiring 需在设置页 include 里加 `style`/`scheme` 状态与 `pickStyle/pickScheme` 方法（页面组件各自独立，不走全局 store —— 读 `MP.style` 即可）。

## 5. 持久化与反 FOUC（app-wiring）

- localStorage key：`mp.style`（风格 id）、`mp.scheme`（`system|dark|light`）
- 应用方式：`document.documentElement.dataset.style = id`、`dataset.scheme = 解析后的 dark|light`
- `<head>` 里（**必须在 `<link rel=stylesheet>` 之前**）放内联脚本：
  读 localStorage → 解析 system → 同步设置两个 data 属性；同时 `<html data-style="awwwards" data-scheme="dark">` 作为无 JS 兜底
- 切换时：更新属性 + localStorage + `meta[name=theme-color]` 改为当前 `--color-paper` 计算值 + `MP.toast("ok","已切换为 Material You")（可选）`
- 监听 `matchMedia("(prefers-color-scheme: dark)")` 的 change：仅当偏好为 `system` 时重算
- 暴露 `window.MP.style = { list, get(){id,scheme,pref}, setStyle(id), setScheme(pref) }`
- `app.js` 中所有读字面量色值的地方（若有：光标、loader、GSAP 相关）改读 `getComputedStyle(document.documentElement).getPropertyValue('--color-accent')` 等；**优先用 CSS 变量，避免 JS 里出现色值**
- 切换风格后若 Grid 动效需要重算：`ScrollTrigger.refresh()`（切换后 nextTick 调用一次即可）

## 6. css-porcelain 的改造清单（硬要求）

1. **圆角角色化**：`.mp-table`/`.sect`/面板/卡片 → `--r-card`；`.inp`/`.sel`/`.ta`/`.num` 容器 → `--r-input`；`.btn`/`.btn--icon`/分页按钮 → `--r-btn`；tag/dot/开关 → `--r-pill`；进度条/开关轨道/勾选框保留 `--r-2`。
2. **线宽**：所有 `border: 1px`/`border-bottom: 1px` → `var(--bw)`；`.nav__link--active` 指示线、toast 左条、focus 用 `--bw-2`/`--tab-line-h`。
3. **字重**：标题类硬编码 500 → `var(--fw-title)`；正文 400 → `var(--fw-body)`；大写小标签 500 → `var(--fw-label)`。
4. **大写/字距/行高**：`text-transform: uppercase` → `var(--label-tt)`；`letter-spacing` 字面量 → `--ls-title`/`--ls-label`；`line-height` 字面量 → `--lh-title`/`--lh-body`/`--lh-ui`。
5. **字号**：`rem`/px 字面量换成 `--text-*` token（页面 h1/h2/h3 已用；补齐 `.stat__num`、`.hero`、按钮、表格、标签等；确实需要固定值的几何尺寸除外）。
6. **投影**：`.card`/`.sect` 若有 box-shadow → `var(--card-elev)`（默认 none，Material 不用投影靠 tonal 底色）。
7. **噪点**：`body::after` 的 `opacity: var(--grain-opacity)` 已就位；补 `filter: var(--grain-invert)`；`--grain-opacity: 0` 时不要留任何可见痕迹。
8. **导航选中态**：`.nav__link` hover/active 用 `--tab-bg`/`--tab-fg`/`--tab-line`（`::after` 高度 `--tab-line-h`，颜色 `--tab-line`；药丸风时 `::after` 因 transparent 自然消失，不需要额外类）。
9. **按钮**：`.btn` 用 `--btn-bg/--btn-fg/--btn-bd`；hover 用 `--btn-hover-bg` + `filter: var(--btn-hover-filter, none)`；`.btn--cta` 用 `--cta-bg/--cta-fg/--cta-bd`。
10. **表单控件原生色**：`.inp`/`.sel` 的 `color-scheme` 由 skin 的 `html[data-scheme]` 决定，不要写死；placeholder 色用 `--color-ink-3`。
11. **明暗安全**：不得出现「假定暗底」的写法（如 `rgba(255,255,255,.05)` 高光、白色描边）；一律走 token。检查所有 `background`/`color`/`border` 是否都是 token。
12. **选择器样式**：`.sty-grid` 响应式栅格（桌面 3×2、≤760px 1 列或 2 列）、`.sty` 卡片（用 `--pv-*` 画预览条）、`.sty--on` 选中态（`--bw-2` accent 描边 + accent 文字）、`.seg` 分段控件（三档，`[aria-pressed=true]` 用 `--tab-bg/--tab-fg`）。
13. **动效 token 化**：`transition` 里的字面量时长改成 `--dur-*`；reduced-motion 里保持现有全降级规则，并对新加的 `.sty` 生效。

## 7. 验收（Lead 跑矩阵探针，全员默认「未验收」）

- 6 风格 × 2 明暗 × 10 路由 × 2 视口（1440×900 / 390×844）：
  `scrollWidth === innerWidth`、0 console error、0 失败请求、`getComputedStyle(:root).getPropertyValue('--color-paper')` 与规格表逐字一致
- awwwards 暗色：与改造前视觉零差异（accent 像素占比 2.08–2.62%、h1 84.96px、grain .05）
- 切换即时生效、刷新后保持、跟随系统随 `prefers-color-scheme` 变化、无 FOUC（首帧即是所选风格）
- reduced-motion：所有风格下 loader 立即移除、Lenis 不启、光标不建
- 体积：首屏 JS gzip ≤200KB；CSS gzip 增量 ≤6KB
- 截图：6 风格 × 暗/亮 各 1 张概览 + 每风格 1 张登录页 = 18 张，存 `docs/screenshots/styles/`
