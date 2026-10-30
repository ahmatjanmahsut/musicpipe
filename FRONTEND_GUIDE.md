# FRONTEND_GUIDE.md — 通用前端开发指南（融合 Apple HIG × Google Material 3 的自研规范）

> 版本 v1.0 · 2026-10-23 · 取代 DESIGN.md (Awwwards) 成为所有网页 / App / 软件前端项目的默认规范。
> 取材来源：Apple Human Interface Guidelines（含 Liquid Glass）、Google Material Design 3（Material You）、
> 以及 Apple / Google 官方产品页与系统应用的共同实践。Awwwards 风格仅作为营销站的显式备选。

## 0. 定位与气质

**一句话**：内容为主角，界面退到幕后；用 token 和系统级纪律换取一致性、可访问性与长期可维护性。

**关键词**：`clarity` `deference` `depth` `semantic` `adaptive` `calm`

**核心取舍**：
- 宁可少一个入口，不要一屏塞满控件。
- 宁可语义 token 与成熟组件库，不要自造控件外观与硬编码视觉值。
- 宁可动效朴素克制，不要动画抢戏；动效必须解释界面变化。
- 宁可 1 个强调色用对地方，不要 5 个颜色互相打架。

---

## 1. 设计原则（决策时逐条对照）

1. **清晰（Clarity）**：文字在任何尺寸可读，图标精确，层级靠排版 + 分组 + 间距表达，不靠描边与阴影堆叠。
2. **顺从（Deference）**：界面不与内容竞争；装饰让位于信息；一屏只保留一个主操作。
3. **深度（Depth）**：用材质（导航/浮层）与分层表面表达前后关系，而非大投影。
4. **语义化（Semantic）**：颜色、字号、圆角、阴影、缓动全部是"有名字的意图"，不是数值。
5. **自适应（Adaptive）**：布局随容器与设备连续变化，不为某一档断点单独硬编码。
6. **反馈完整**：每个异步操作都有 加载 → 成功/失败 的可感知反馈；每次点击有 pressed 态。

---

## 2. 设计令牌（Design Tokens）—— 一切视觉值必须引用 token

### 2.1 色彩（语义双层模型，亮/暗两套）

```css
:root {
  /* 表面层级（3 层，表达深度，不靠阴影堆叠） */
  --color-bg:            #fafafa;   /* 页面底（禁纯白 #fff） */
  --color-surface:       #ffffff;   /* 卡片/分组 */
  --color-surface-raised:#f4f4f5;   /* 浮层/次级面 */
  /* 墨色层级（3 级文字 + 1 线） */
  --color-ink:           #1a1a1c;   /* 主文字（禁纯黑 #000） */
  --color-ink-2:         rgba(26,26,28,.62);
  --color-ink-3:         rgba(26,26,28,.40);
  --color-line:          rgba(26,26,28,.10);
  /* 品牌强调色：全站仅此 1 个，面积 ≤5%（按钮、链接、选中态、徽标） */
  --color-accent:        #0057d8;
  --color-accent-soft:   rgba(0,87,216,.10);
  /* 状态色（仅语义场景，禁止当装饰色用） */
  --color-success: #188038;  --color-warning: #b06000;  --color-danger: #d93025;
}
[data-theme="dark"] {
  --color-bg:            #101012;   /* 禁纯黑 */
  --color-surface:       #1a1a1e;
  --color-surface-raised:#242429;
  --color-ink:           #ececea;   /* 禁纯白 */
  --color-ink-2:         rgba(236,236,234,.62);
  --color-ink-3:         rgba(236,236,234,.40);
  --color-line:          rgba(236,236,234,.12);
  --color-accent:        #7aa8ff;   /* 深色下提高明度保对比度 */
  --color-accent-soft:   rgba(122,168,255,.14);
}
```

**规则**
- 深色模式是独立映射，不是反色；切换用 `prefers-color-scheme` + 手动开关双通道。
- 对比度：正文 ≥4.5:1，大文本/粗体 ≥3:1（WCAG AA，深色模式下逐屏实测）。
- 禁止大面积 linear-gradient 背景与渐变文字；允许极轻微中性渐变（如卡片底，opacity ≤.12）。
- 图片在深色模式可 `filter: saturate(.9)` 统一色调。

### 2.2 排版

```css
:root {
  --font-sans: -apple-system, "SF Pro Text", "Inter", "PingFang SC",
               "HarmonyOS Sans SC", "Noto Sans SC", "Segoe UI", sans-serif;
  --font-mono: "SF Mono", "JetBrains Mono", "Roboto Mono", monospace;

  --text-display: clamp(2.5rem, 5.5vw, 4.5rem);  /* 营销位大标题，可不用 */
  --text-h1: clamp(1.9rem, 3.2vw, 2.6rem);
  --text-h2: 1.5rem;    --text-h3: 1.25rem;
  --text-body: 1rem;    /* 16px；正文密集 App 可用 17px */
  --text-small: 0.875rem;
  --text-caption: 0.75rem;   /* 最小字号，低于 12px 禁用 */
}
```

**规则**
- 全站 ≤2 个字体家族（+1 mono 用于数字/编号/代码）。
- 标题字重 600–650 + `letter-spacing: -0.02em`；中文标题用 500–600 收字距，不用 800。
- 正文行高 1.6–1.8（中文取上限）；标题行高 1.1–1.25。
- 数字、金额、编号、时间戳一律 `var(--font-mono)` + `font-variant-numeric: tabular-nums`。
- 提示/告警/引导类关键文案左对齐，不居中。

### 2.3 间距 / 圆角 / 阴影 / 动效时长

```css
:root {
  --space-1: 4px;  --space-2: 8px;  --space-3: 12px; --space-4: 16px;
  --space-6: 24px; --space-8: 32px; --space-12: 48px; --space-16: 64px;
  --space-24: 96px; --space-32: 128px;

  --container: 1200px;
  --gutter: clamp(16px, 4vw, 48px);
  --section-block: clamp(64px, 9vw, 128px);

  /* 同心圆角：子半径 = 父半径 − 内边距 */
  --radius-1: 4px; --radius-2: 8px; --radius-3: 12px; --radius-4: 16px;
  --radius-pill: 999px;

  /* 只有两档阴影，禁止第三档 */
  --elev-1: 0 1px 2px rgba(0,0,0,.06), 0 1px 3px rgba(0,0,0,.08);        /* 卡片 */
  --elev-2: 0 4px 12px rgba(0,0,0,.10), 0 2px 4px rgba(0,0,0,.08);       /* 浮层/弹窗 */

  --dur-micro: .18s;  --dur-base: .3s;  --dur-slow: .5s;  --dur-reveal: .8s;
  --ease-standard: cubic-bezier(.2,0,0,1);
  --ease-decelerate: cubic-bezier(0,0,0,1);
  --ease-emphasized: cubic-bezier(.2,0,0,1);
}
```

**规则**
- 圆角：输入框/小按钮 4–8px；卡片/对话框 8–12px；标签栏/主按钮可用 pill；**禁止 >16px 的卡片圆角与"大圆角+大投影"的网页套路**。
- 阴影只有 `--elev-1/--elev-2` 两档；层级优先用表面色分层（bg → surface → surface-raised）而非加深阴影。
- 间距一律 4 的倍数；组件内边距 16/24px；列表项间距 8px；区块间距 ≥32px。

---

## 3. 布局

- 12 列栅格，内容区 `max-width: var(--container)`，左右边距 `var(--gutter)`（≥16px）。
- 正文阅读栏宽 60–75 字符（约 640–720px）；营销位可突破，但每屏只许一处"破格"。
- 区块垂直节奏 `padding-block: var(--section-block)`；满屏场景用 `min-height: 100svh`。
- 对称网格为默认；允许单点非对称（一个错位元素/一个出血图），禁止全页错乱。
- 断点参考（不写死，用容器查询/clamp 优先）：<600 手机 / 600–840 大手机 / 840–1200 平板 / >1200 桌面。
- 导航形态随宽度切换：手机底部 Tab（≤5 项）/ 平板侧边 Rail / 桌面顶栏或固定侧栏。
- App/套壳 Web 必须 `safe-area-inset` 适配刘海、灵动岛、Home 指示条，禁止硬编码。

---

## 4. 组件规范

**总则**：优先成熟组件库（Web：MUI / shadcn/ui / Ant Design / Radix；原生：SwiftUI 系统控件）。不自造按钮、开关、弹窗外观。

| 组件 | 规则 |
|---|---|
| 按钮 | 主按钮填充 `--color-accent`，**每屏最多 1 个**；次按钮描边/纯文字；高度 ≥44px；pressed 态 `scale(.97)` + 加深（不只变色）；disabled 降不透明度且不只靠颜色区分；破坏性操作用 `--color-danger` + 二次确认 |
| 卡片 | `--color-surface` + `--radius-2/3` + `--elev-1` 或 1px `--color-line` 描边（二选一，不叠加）；内边距 16/24px；**禁止卡片套卡片** |
| 导航栏 | 固定顶部，透明/轻微模糊（`backdrop-filter: blur(8px)`）+ 滚动后加底线；标题左、操作右；移动端抽屉或底部 Tab |
| 表单 | 标签左对齐；输入框高 ≥44px、底色 `--color-surface-raised` 或描边；错误用文字 + `--color-danger`，不只红框；键盘类型匹配（数字/邮箱/电话） |
| 列表 | 行高 ≥48px；分组用圆角卡片包裹；分割线 1px `--color-line` 且与文字左对齐 |
| 弹层 | Web 用对话框/抽屉；App 用 sheet/Alert；模态保持单一任务焦点；禁止 `alert()` |
| 反馈 | Snackbar（可撤销）/ Banner / 进度条 / 骨架屏；空状态 = 图标 + 一句说明 + 一个主操作 |
| 图标 | 统一一套线性图标（Lucide 1.5px / SF Symbols / Material Symbols），禁混用、禁 emoji 当功能图标；尺寸 16/20/24/32 |
| 材质 | 模糊/玻璃材质**仅限导航栏与浮层**；禁止玻璃叠玻璃；内容层不铺模糊 |

---

## 5. 动效

- **优先级**：CSS transition / spring → Framer Motion（微交互）→ GSAP ScrollTrigger（仅滚动编排场景）。不默认引入 GSAP。
- **时长**：微交互 .15–.25s；常规转场 .25–.5s；编排揭示 ≤.8s；禁止 >1s 的 UI 过渡。
- **缓动**：入场/出现用 decelerate，离场用 standard/加速，展开收起用 emphasized。
- **只动画** `transform / opacity / clip-path`；禁止动画 `width/height/top/left/margin`。
- **转场语义**：同级页面横向滑入；子任务从下升起；临时任务用卡片式 sheet；不滥用无意义淡入缩放。
- **降级（强制）**：

```css
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after {
    animation-duration: .01ms !important;
    transition-duration: .01ms !important;
    scroll-behavior: auto !important;
  }
}
```
JS 侧 `matchMedia('(prefers-reduced-motion: reduce)')` 直接跳终态。

---

## 6. 可访问性（底线，非加分项）

- 触控热区 ≥44×44px（App 44pt / Material 48dp），相邻目标留足间距。
- 对比度正文 ≥4.5:1、大文本 ≥3:1；不以颜色作为唯一信息载体（配文字/图标）。
- 键盘可完整 Tab 遍历，`:focus-visible` 有清晰描边（`outline: 2px solid var(--color-accent); outline-offset: 2px`）。
- 所有可交互元素有 accessible name（`aria-label`/`role`）；路由切换后焦点落到标题。
- 尊重系统偏好：减弱动态效果、减弱透明度、增加对比度、大字号（200% 缩放不截断不重叠）。

---

## 7. 图像与媒体

- 图片统一 `object-fit: cover` + 固定 `aspect-ratio` + `loading="lazy"` + `decoding="async"` + `srcset/sizes`。
- 占位用 LQIP/BlurHash，**禁止灰色空块**；素材统一色调（可统一 `filter: saturate(.9) contrast(1.05)`）。
- 视频：静音、循环、`playsinline`、必带 `poster`，移动端可降级静态图，首屏视频 ≤4MB。
- WebGL/重型动效可选且必须有 `webglcontextlost` 与不支持时的 CSS 降级；移动端默认不渲染。

---

## 8. 性能预算

| 指标 | 目标 |
|---|---|
| LCP | < 2.5s（首屏图/视频预加载或降级） |
| CLS | < 0.1（所有媒体写死宽高比） |
| INP | < 200ms |
| 首屏 JS | < 200KB gzip（三方库按需分包、动态 import） |
| 图片 | 首屏 < 1.5MB，单页 < 4MB，WebP/AVIF + JPG 兜底 |
| 字体 | 自托管 + `font-display: swap`，≤2 字族 × 3 字重 |
| 滚动帧率 | 桌面 ≥55fps，移动端 ≥40fps |
| Lighthouse | Performance ≥90、Accessibility ≥95、Best Practices ≥95 |

---

## 9. 负面清单（生成时逐条自检）

- ❌ 硬编码色值/字号/间距/圆角（必须引用 token），内联 style 魔法数字
- ❌ 渐变背景、渐变文字（bg-clip-text）、彩虹配色
- ❌ 大面积毛玻璃卡片、玻璃叠玻璃、内容层铺模糊
- ❌ 大圆角（>16px）+ 大投影（shadow-xl）的"网页卡片"套路
- ❌ emoji 当功能图标；图标风格混用
- ❌ 三列图标卡片阵列 + "✨ 强大能力"式营销套话
- ❌ 卡片套卡片、边框套边框
- ❌ 全站同一字号层级、标题不做大
- ❌ 一屏多个主按钮
- ❌ Lorem ipsum、灰占位图、假数据"用户A/123"、`alert()`
- ❌ 无深色模式 / 深色模式直接反色塌陷
- ❌ 无 `prefers-reduced-motion` 降级、无键盘焦点态
- ❌ 动画 width/height/top/left；UI 过渡 >1s
- ❌ 触控热区 <44px、字号 <12px、忽略安全区
- ❌ 无加载/空/错误/权限被拒状态

---

## 10. 交付验收清单

- [ ] 全部视觉值引用 token，无硬编码色值/字号/间距，无内联 style
- [ ] 深色模式逐屏检查，对比度无塌陷；强调色全站仅 1 个、面积 ≤5%
- [ ] 4/8pt 网格；页面边距 ≥16px；区块间距 ≥32px；正文栏宽 60–75 字符
- [ ] ≤2 字族(+1 mono)；数字 mono+tabular-nums；中文正文行高 1.6–1.8；字号 ≥12px
- [ ] 所有可交互元素热区 ≥44×44px；同心圆角无"捏角"；阴影仅 elev-1/2 两档
- [ ] 每屏最多 1 个主按钮；按钮 pressed/disabled/loading 态齐全
- [ ] 动效只动 transform/opacity/clip-path；时长 ≤.8s；reduced-motion 全量降级（CSS+JS）
- [ ] 键盘可完整遍历，focus-visible 清晰；aria 标签完整；200% 缩放不破版
- [ ] 加载骨架/空状态/错误态/权限被拒态齐全；无 alert()
- [ ] 图片真实素材、固定比例、懒加载、无灰占位块；视频带 poster
- [ ] 玻璃/模糊仅出现在导航/浮层，无玻璃叠玻璃
- [ ] LCP<2.5s、CLS<0.1、首屏 JS<200KB gzip；Lighthouse Perf≥90 / A11y≥95
- [ ] 1440 / 768 / 375 三档截图自查「层级 / 留白 / 状态 / 质感」四项
- [ ] 无 console 报错；无 Lorem ipsum / 假数据 / emoji 图标

---

## 11. 给 harness 的任务 Prompt 模板

```
任务：实现「仪表盘」页面（默认自研规范 FRONTEND_GUIDE.md）。

必读：/FRONTEND_GUIDE.md（token 与负面清单为硬约束）。
所有颜色/字号/间距/圆角/阴影/缓动必须引用 CSS 变量 token，禁止魔法数字与内联 style。

技术栈：Next.js + TypeScript + Tailwind（token 全部映射进 theme）+ shadcn/ui + Framer Motion（仅微交互）。

结构要求：
1) 顶栏：logo + 搜索 + 通知/头像，滚动后加底线；移动端底部 Tab（≤5 项）
2) 主区：欢迎语（h2）+ 数据卡片网格（1/2/4 列随宽度），卡片 = surface + radius-2 + elev-1
3) 图表区统一语义配色；最近活动列表行高 ≥48px、分割线 1px
4) 每屏最多 1 个主按钮；破坏性操作 danger 色 + 二次确认
5) 状态齐全：骨架屏 / 空状态（图标+说明+主操作）/ 错误态（可重试）
6) 深色模式跟随系统 + 手动开关；prefers-reduced-motion 降级

约束：禁止渐变背景/玻璃卡片/大圆角大投影/emoji 图标/三列图标卡片阵列；
     热区 ≥44px；对比度 ≥4.5:1；动画只动 transform/opacity/clip-path。

完成后：Playwright 截 1440/768/375 三图到 docs/screenshots/，
对照 §10 验收清单逐项自检，说明改动了哪 3 处。
```

---

## 12. 附：备选体系路由

- 纯营销/作品集站，且用户**明确**要求 Awwwards 风格 → 切回 `DESIGN.md`（C:/Users/demo\Downloads\DESIGN.md）
- 需要严格 Material 3 组件语义（如 Android 跨端）→ `DESIGN_Google.md`
- Apple 生态原生 App / 追求 Liquid Glass 观感 → `DESIGN_Apple.md`
- 三者细节速查存于长期记忆（技法坊）："Material 3 前端要点" / "Apple HIG 前端要点" / "Awwwards 施工要点"
