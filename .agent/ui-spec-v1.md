
# 东软云科技单文档设计执行规范 V1

版本：V1.2  
日期：2026-04-13  
用途：把本文件作为唯一设计规范交给任意 AI 设计/开发软件，在没有第二轮补充对话的情况下生成符合东软云科技品牌、业务要求与前端实现约束的 UI。

---

## 0. 最高优先级执行规则

你正在为 `东软云科技` 生成企业级数字产品界面。你必须把本文件当作唯一设计来源，不能等待用户二次补充，不能输出通用模板，不能把本文件中的说明文字渲染到界面上。

本规范适用于所有 AI 设计/开发软件，包括但不限于 Figma Make、Cursor、Windsurf、Codex、Claude Code、v0、Bolt、Lovable、Replit Agent 等。无论工具名称是什么，都必须按本 Markdown 中的规则执行。

如果生成前端代码，必须使用 `Vue 3 + Naive UI` 作为实现基线，并把 `Naive UI` 作为唯一基础 UI 组件库。

如果用户只给出一个页面主题，你必须自行完成以下判断：

1. 判断页面类型：官网、解决方案页、中后台工作台、AI 工作台、组件库。
2. 判断主任务：用户进入页面最需要完成的 1 个核心动作。
3. 判断必要模块：围绕主任务补齐数据、状态、证据、操作和反馈。
4. 生成真实界面：必须有可见的信息结构、业务数据、组件状态、关键操作。
5. 默认支持浅色/深色主题切换和中英文语言切换，除非用户明确要求单主题或单语言。
6. 自检通过后再结束：不得留下占位符、实现备注或空白图表。

硬性禁止：

- 禁止出现“图表区域”“推荐使用 Recharts”“这里放图片”“示例数据”“待补充”等占位文案。
- 禁止出现英文乱码、无意义英文段落、Lorem ipsum、随机品牌名、随机技术名。
- 禁止只生成左侧导航 + 指标卡 + 空白图表的通用 SaaS 后台。
- 禁止只用黑白灰，必须体现东软云科技品牌色。
- 禁止把说明文档、组件说明、实现备注当成用户界面内容。
- 禁止移动端横向挤压桌面布局；移动端必须折叠侧栏并单列显示主内容。
- 禁止只设计单一颜色模式；必须能在浅色和深色主题下保持品牌一致和可读。
- 禁止只写单语言静态文案；必须支持 `zh-CN` 与 `en-US` 切换。
- 禁止使用 Naive UI 之外的基础组件库生成界面，例如 Ant Design Vue、Element Plus、Arco Design、TDesign、Vant、shadcn/ui、Material UI、Bootstrap、Chakra UI、Mantine。
- 禁止为了视觉效果绕开 Naive UI 重新手写一套通用按钮、表格、弹窗、表单组件；只能在 Naive UI 上做主题覆盖、包装组件和业务组件。

---

## 1. 品牌硬约束

```yaml
brand:
  name_cn: 东软云科技
  name_en: Neusoft Cloud Technology
  slogan_cn: 连接世界，智启卓越
  slogan_en: Connecting The World, Driving Next-Gen Excellence
  keywords:
    - 连接
    - 数字科技
    - 成长
  personality:
    - 全球化
    - 专业可信
    - 合规稳健
    - 业务导向
    - AI驱动
    - 共创长期价值
```

品牌表达必须体现：

- 用结构关系体现“连接”，例如流程、路径、上下游关系、人员与任务匹配、数据来源与结果联动。
- 用状态、数据、自动化和证据体现“数字科技”，不要只靠装饰光效。
- 用趋势、效率、长期价值和行动闭环体现“成长”。
- 默认是 ToB 企业级产品，不是消费级 App、社交产品或娱乐化工具。

---

## 2. 设计 Token

### 2.1 颜色

```yaml
color:
  brand_primary: "#00C26B"
  brand_primary_dark: "#00AD5F"
  brand_primary_light: "#00E37D"
  brand_primary_soft: "#DAF6EA"
  brand_accent: "#FF8209"
  brand_accent_soft: "#FFEEDE"
  black: "#000000"
  white: "#FFFFFF"
  neutral_bg: "#F5F7F6"
  neutral_border: "#CFD3D0"
  neutral_text_primary: "#191919"
  neutral_text_secondary: "#474C4A"
  neutral_text_muted: "#898E8B"
  success: "#00C26B"
  warning: "#FF8209"
  error: "#D14343"
  info: "#2F6FED"
```

颜色规则：

- 默认比例：60% 中性 / 30% 绿色 / 10% 橙色或状态色。
- 页面中必须至少在主行动、激活导航、关键指标或状态提示中使用 `#00C26B`。
- `#FF8209` 只用于提醒、待确认、风险、局部高亮，不作为主色大面积铺底。
- 后台工作台默认浅色中性底，不默认全黑。
- 官网首屏或品牌型 AI 首页可以局部使用深色品牌区，但内容区必须保持高可读。
- 错误状态使用 `#D14343`，不要用橙色代替错误。

### 2.2 主题模式 Token

界面必须支持浅色和深色主题切换。默认主题为浅色，深色主题作为同一设计系统的等价模式，不是另一套视觉风格。

```yaml
theme:
  default: light
  supported:
    - light
    - dark
  light:
    bg_page: "#F5F7F6"
    bg_surface: "#FFFFFF"
    bg_surface_alt: "#EEF2EF"
    bg_sidebar: "#FFFFFF"
    bg_header: "#FFFFFF"
    border_subtle: "#CFD3D0"
    border_strong: "#B8BDBA"
    text_primary: "#191919"
    text_secondary: "#474C4A"
    text_muted: "#898E8B"
    brand_primary: "#00C26B"
    brand_primary_hover: "#00AD5F"
    brand_primary_soft: "#DAF6EA"
    brand_accent: "#FF8209"
    brand_accent_soft: "#FFEEDE"
  dark:
    bg_page: "#0E0E0E"
    bg_surface: "#191919"
    bg_surface_alt: "#1F1F1F"
    bg_sidebar: "#121412"
    bg_header: "#121412"
    border_subtle: "#2F3431"
    border_strong: "#474C4A"
    text_primary: "#F5F7F6"
    text_secondary: "#CFD3D0"
    text_muted: "#9AA39E"
    brand_primary: "#00C26B"
    brand_primary_hover: "#00E37D"
    brand_primary_soft: "rgba(0,194,107,0.16)"
    brand_accent: "#FF8209"
    brand_accent_soft: "rgba(255,130,9,0.16)"
```

主题规则：

- 所有组件必须使用语义 token，例如 `bg_surface`、`text_primary`、`brand_primary`，不要把浅色模式的固定颜色硬编码到组件里。
- 深色主题不是营销风格，不允许因为切到深色就变成赛博、霓虹、玻璃拟态。
- 深色模式中品牌绿仍是主识别色，橙色仍只用于提醒、待确认和局部高亮。
- 图表、状态标签、表格、弹窗、抽屉、导航、空状态、错误状态都必须定义浅色和深色表现。
- 主题切换控件必须出现在桌面端顶栏；移动端可收进顶部菜单或用户设置抽屉。

### 2.3 字体

```yaml
font:
  cn: "Source Han Sans CN / 思源黑体"
  en: "Roboto"
  fallback: "PingFang SC, Microsoft YaHei, system-ui, sans-serif"
```

字号建议：

```yaml
type_scale:
  display: "40/48"
  h1: "28/36"
  h2: "24/32"
  h3: "20/28"
  title: "18/26"
  body: "14/22"
  caption: "12/16"
```

字体规则：

- 中文界面必须优先保证中文可读性，不能使用随机英文乱码。
- 标题 600 或 700，正文 400，按钮和导航 500。
- 不使用花体、卡通字体、过度圆润的消费级字体。

### 2.4 语言与本地化 Token

界面必须支持中英文切换。默认语言为简体中文，备用语言为英文。

```yaml
locale:
  default: zh-CN
  supported:
    - zh-CN
    - en-US
  language_switcher:
    desktop_location: topbar_right
    mobile_location: top_menu_or_user_drawer
  copy_rules:
    zh-CN:
      brand_name: "东软云科技"
      tone: "专业、准确、克制、业务导向"
    en-US:
      brand_name: "Neusoft Cloud Technology"
      tone: "professional, precise, restrained, business-oriented"
```

本地化规则：

- 所有用户可见文案必须可切换，不允许把中文和英文随机混排。
- 品牌名可按语言切换：中文界面用 `东软云科技`，英文界面用 `Neusoft Cloud Technology`。
- 品牌口号可按语言切换：中文 `连接世界，智启卓越`；英文 `Connecting The World, Driving Next-Gen Excellence`。
- 导航、按钮、筛选、表格列名、状态、空状态、错误提示、确认弹窗、图表标题都必须具备中英文文案。
- 英文文案通常更长，布局必须预留 1.3 倍文字长度，不得溢出、重叠或被裁切。
- 日期、时间、百分比、数字单位必须按语言环境表达：中文可用 `2026年4月13日`、`晚班 20:00-22:00`；英文可用 `Apr 13, 2026`、`Night shift 20:00-22:00`。
- 语言切换控件必须出现在桌面端顶栏；移动端可收进顶部菜单或用户设置抽屉。

### 2.5 间距、圆角、阴影

```yaml
spacing: [4, 8, 12, 16, 24, 32, 40, 48, 64]
radius:
  small: 6
  default: 8
  large: 12
  panel: 16
  pill: 999
shadow:
  card: "0 1px 2px rgba(0,0,0,0.06)"
  floating: "0 8px 24px rgba(0,0,0,0.10)"
```

规则：

- 使用 8px 基础栅格。
- 工作台优先用浅背景层级、低对比边框和轻阴影，不做重玻璃拟态。
- 卡片不能无限堆叠；每个页面必须有明确主任务区。

---

## 3. Logo 与品牌资产规则

优先使用正式 Logo 资源：

- 白底或浅底：`完整版_横排_黑绿.png` 或 `完整版_横排_白绿.png`
- 深色底：`完整版_横排_反白.png`
- 空间很小时：`缩略版_黑绿.png`、`缩略版_白绿.png`、`缩略版_反白.png`

如果设计工具无法读取本地图片，也必须用准确文字锁定 `东软云科技`，并配合品牌绿色几何标识，不得使用随机“云”字圆形图标作为 Logo 替代。

Logo 禁止拉伸、改色、加描边、加阴影、拆分中英文组合。

---

## 4. 页面类型自动判断

如果用户没有说明页面类型，按以下规则自行判断：

```yaml
if_task_contains:
  AI|智能体|知识助手|客服助手|Copilot: AI工作台
  排班|工单|人员|运营|审批|管理|数据看板: 中后台工作台
  解决方案|行业|服务|能力|案例: 解决方案页
  官网|首页|品牌|介绍|落地页: 官网营销页
  组件|设计系统|变量|组件库: 组件库
default: 中后台工作台
```

无论判断结果如何，必须先明确主任务，再围绕主任务组织页面。不要默认生成“数据看板”，除非用户明确要求数据看板。

---

## 5. 场景执行规则

### 5.1 中后台工作台

适用于排班、工单、员工、运营、审批、数据管理等场景。

必须包含：

- 页面标题与业务说明
- 主任务区
- 关键指标摘要
- 筛选或搜索
- 主表格、日历、时间轴或工作流
- 异常/风险处理区
- 详情或辅助洞察区
- 主操作按钮
- Loading / Empty / Error / Disabled 状态

设计要求：

- 不要只做指标卡和空图表。
- 至少有一个可执行动作，例如“处理异常”“生成排班”“发起补位”“导出计划”“提交审批”。
- 指标必须有含义，例如趋势、环比、阈值、风险等级或处理建议。
- 风险或异常必须给下一步动作。

### 5.2 AI 工作台

适用于 AI 客服、知识助手、智能体、分析助手、AI 运维等场景。

必须包含：

- 任务输入区
- 上下文或知识范围
- AI 处理状态
- 结构化结果
- 来源与证据
- 风险或置信提示
- 下一步动作
- 人工复核或接管路径

AI 状态必须区分：

```yaml
ai_states:
  - 理解请求
  - 检索知识
  - 调用工具
  - 生成结果
  - 等待确认
  - 失败可重试
  - 权限受限
```

禁止只有聊天气泡和一个 loading。

### 5.3 解决方案页

必须按以下叙事组织：

```text
场景痛点 -> 解决方案框架 -> 核心能力 -> 实施路径 -> 价值结果 -> 可信背书 -> CTA
```

必须体现业务价值，不做纯视觉展示页。

### 5.4 官网营销页

必须包含：

- Hero 主张区
- 业务价值区
- 解决方案区
- 行业与客户场景区
- AI 能力区
- 全球交付/质量/合规背书
- 行动转化区

可以更有视觉冲击，但必须专业可信、信息清楚。

---

## 6. 客服排班场景专用规则

如果用户主题包含“客服排班”“排班系统”“客服运营排班”，必须设计为 `客服排班运营工作台`，不要设计成普通数据看板。

核心任务：

```text
帮助客服主管发现今日排班缺口、处理异常班次、完成补位或调班。
```

必须包含：

- 今日排班主舞台：服务覆盖率、缺口岗位、异常班次、主行动按钮。
- 班次时间轴或日历：早班、中班、晚班、夜班，展示人员覆盖与空缺。
- 异常处理区：请假、缺勤、超时、技能不匹配、待审批。
- 可补位人员池：姓名、技能标签、当前负载、可用时段、推荐原因。
- 运营指标：出勤率、排班完成度、异常数、服务覆盖率、平均响应保障。
- 操作闭环：发起补位、调整班次、通知员工、提交审批、导出排班。

推荐布局：

```yaml
layout:
  left_sidebar: 模块导航，可在移动端折叠
  topbar: 面包屑、系统状态、主题切换、语言切换、用户信息
  main_stage: 今日排班运营中心，突出当前风险和主行动
  schedule_timeline: 班次时间轴或排班日历
  right_panel: 异常处理与可补位人员池
  bottom_section: 趋势分析和处理记录
```

必须避免：

- 只输出“今日出勤率 / 排班完成度 / 异常班次”三张卡片。
- 图表为空白占位。
- 没有具体员工、班次、异常原因和处理动作。
- 页面标题叫“数据看板”但内容主题是排班。

示例业务数据可使用：

```yaml
metrics:
  service_coverage: "96.8%"
  attendance_rate: "95.4%"
  schedule_completion: "100%"
  abnormal_shifts: 2
  open_gap: "晚班 20:00-22:00 缺 1 人"
staff_pool:
  - name: "王珊"
    skills: ["售后", "英文"]
    load: "72%"
    available: "20:00-22:00"
    reason: "技能匹配且当前负载较低"
  - name: "刘明"
    skills: ["VIP客户", "投诉处理"]
    load: "68%"
    available: "18:00-22:00"
    reason: "可覆盖高风险工单"
exceptions:
  - type: "请假"
    shift: "晚班"
    owner: "赵倩"
    impact: "售后队列缺口 1 人"
    action: "发起补位"
  - type: "技能不匹配"
    shift: "中班"
    owner: "陈宇"
    impact: "英文队列响应保障不足"
    action: "调整人员"
```

---

## 7. 主题与语言切换硬约束

所有页面必须具备 `theme` 和 `locale` 两个全局状态：

```yaml
global_state:
  theme: light | dark
  locale: zh-CN | en-US
```

必须设计：

- 主题切换控件：浅色 / 深色。
- 语言切换控件：中文 / English。
- 当前状态反馈：用户能看出当前处于哪个主题和哪个语言。
- 设置持久化提示：如果是原型，可在交互说明中标注应记住用户选择。

组件要求：

- `BrThemeToggle`：支持 `light`、`dark`、`system_optional` 三种状态；如果空间不足，至少支持 light/dark。
- `BrLanguageSwitcher`：支持 `zh-CN` 和 `en-US`；桌面端可用文本按钮或下拉菜单，移动端放入菜单或抽屉。
- 所有页面模板都必须在顶栏或设置入口包含这两个组件。

深色主题验收：

- 背景、卡片、表格、弹层、输入框、图表和状态标签都必须换成深色语义 token。
- 文本对比度必须足够，长文本不得使用纯白刺眼大段铺排，可使用 `text_secondary`。
- 品牌绿和品牌橙在深色中必须降低面积，不要形成霓虹夜店感。

语言切换验收：

- 中文版和英文版必须表达同一业务含义，不得只翻译标题而漏掉表格、按钮、状态和错误提示。
- 不得出现中文页面夹杂未翻译英文按钮，或英文页面夹杂中文业务状态。
- 切换语言后，导航、表格列名、指标卡、异常原因、操作按钮、空状态和错误提示都必须同步变化。
- 英文长文本不得破坏卡片、按钮、导航和移动端布局。

---

## 8. 响应式硬约束

桌面端：

- 可以使用左侧导航 + 主内容 + 右侧辅助面板。
- 内容最大宽度要可控，主任务区优先可见。
- 顶栏必须容纳主题切换、语言切换和用户入口，空间不足时可合并为设置菜单。

移动端：

- 左侧导航必须折叠为顶部菜单或抽屉。
- 主内容必须单列显示。
- 指标卡必须改为纵向堆叠或横向可滑动，但不能挤成窄列。
- 表格必须转为卡片列表或提供横向滚动容器。
- 任何文本不得溢出、重叠、被裁切或竖排挤压。
- 主题切换和语言切换必须收进顶部菜单或用户设置抽屉，不能占满首屏。

如果无法完成移动端完整布局，也必须至少保证首屏可读、主操作可点击、内容不横向崩坏。

---

## 9. AI 开发软件通用识别与 Naive UI 实现规则

本规范不是 Figma Make 专用文件。任何 AI 开发软件读取本文件后，都必须输出可被设计师和前端工程师共同使用的结果。

通用识别规则：

- 先读 `最高优先级执行规则`，再读品牌、token、场景、响应式、主题语言和实现规则。
- 不要把本规范当作页面文案。
- 不要只输出设计说明；如果任务要求页面，就直接生成页面结构或代码。
- 如果工具支持设计稿，输出可维护设计结构；如果工具支持代码，输出可运行前端结构。
- 如果工具无法使用某个能力，必须保留设计约束，不得自动换成其他组件库或其他品牌风格。

前端实现硬约束：

```yaml
frontend_stack:
  framework: Vue 3
  base_component_library: Naive UI
  component_library_policy: Naive UI only
  allowed_supporting_libraries:
    - "@vicons/* 或项目已有图标库"
    - "ECharts 或 Recharts 类图表库，仅用于图表，不作为 UI 组件库"
    - "项目已有 CSS 方案，仅用于布局和局部样式，不替代 Naive UI 组件"
  forbidden_component_libraries:
    - Ant Design Vue
    - Element Plus
    - Arco Design
    - TDesign
    - Vant
    - shadcn/ui
    - Material UI
    - Bootstrap
    - Chakra UI
    - Mantine
```

Naive UI 实现层级：

```yaml
implementation_layers:
  - themeOverrides
  - wrapper_components
  - business_components
```

Naive UI 基础组件映射：

```yaml
naive_ui_base_components:
  layout:
    - NLayout
    - NLayoutSider
    - NLayoutHeader
    - NLayoutContent
    - NGrid
    - NGridItem
    - NSpace
  navigation:
    - NMenu
    - NBreadcrumb
    - NDropdown
    - NDrawer
  input:
    - NInput
    - NSelect
    - NDatePicker
    - NTimePicker
    - NForm
    - NFormItem
    - NSwitch
  data:
    - NDataTable
    - NPagination
    - NStatistic
    - NProgress
    - NTag
    - NBadge
  feedback:
    - NAlert
    - NMessageProvider
    - NNotificationProvider
    - NModal
    - NPopconfirm
    - NSpin
    - NEmpty
  actions:
    - NButton
    - NButtonGroup
    - NIcon
```

品牌包装组件：

```yaml
wrapper_components:
  - BrButton
  - BrCard
  - BrInput
  - BrSelect
  - BrTable
  - BrPageHeader
  - BrMetricCard
  - BrSearchForm
  - BrEmptyState
  - BrResultState
  - BrThemeToggle
  - BrLanguageSwitcher
business_components:
  - BrScheduleStage
  - BrShiftTimeline
  - BrExceptionQueue
  - BrStaffPool
  - BrCoverageGauge
ai_components:
  - BrAgentPanel
  - BrReasoningTimeline
  - BrCitationList
  - BrToolCallCard
  - BrConfidenceBadge
  - BrHumanHandoffBar
```

主题与语言必须使用 Naive UI 能力对齐：

```yaml
naive_ui_theme_locale:
  provider: NConfigProvider
  theme:
    light: null
    dark: darkTheme
  locale:
    zh-CN:
      locale: zhCN
      dateLocale: dateZhCN
    en-US:
      locale: enUS
      dateLocale: dateEnUS
  themeOverrides:
    source: 本文件的 color/theme/radius/font token
```

执行要求：

- 视觉设计工具必须先建立变量，再建立组件，再生成页面。
- 代码生成工具必须先建立 Naive UI 主题配置，再建立品牌 wrapper components，再建立业务组件和页面。
- 所有基础组件必须具备 `theme=light/dark` 与 `locale=zh-CN/en-US` 的适配说明。
- 页面不得直接大面积裸用 Naive UI 默认样式，必须通过 `themeOverrides + Br* wrapper + business components` 体现东软云科技品牌。
- 不要为了还原视觉稿而引入第二套组件库。

---

## 10. 输出自检

生成结束前必须通过以下检查：

```yaml
brand_check:
  - 页面是否明显使用 #00C26B
  - 橙色是否只用于提醒或风险
  - 是否避免通用黑白灰后台
  - Logo 或品牌名称是否准确
business_check:
  - 页面标题是否匹配任务主题
  - 是否有明确主任务区
  - 是否有真实业务数据和操作动作
  - 是否没有占位文案
interaction_check:
  - 是否包含 loading/empty/error/disabled 等状态
  - 风险或异常是否有下一步动作
responsive_check:
  - 移动端侧栏是否折叠
  - 文本是否不溢出、不重叠、不竖向挤压
  - 主内容是否单列可读
theme_check:
  - 是否支持 light/dark 两套主题
  - 主题切换控件是否可见或可从设置入口访问
  - 深色主题是否仍然符合东软云科技品牌，而不是赛博霓虹风
  - 所有组件是否使用语义颜色 token，而非硬编码浅色颜色
locale_check:
  - 是否支持 zh-CN/en-US
  - 语言切换控件是否可见或可从设置入口访问
  - 导航、按钮、表格、状态、错误提示是否都有双语表达
  - 英文长文案是否不会破坏布局
component_library_check:
  - 是否使用 Vue 3 + Naive UI 作为前端实现基线
  - 是否把 Naive UI 作为唯一基础 UI 组件库
  - 是否没有引入 Ant Design Vue、Element Plus、Arco Design、TDesign、Vant、shadcn/ui、Material UI 等其他组件库
  - 是否提供 themeOverrides、Br* 包装组件和业务组件分层
  - 是否使用 NConfigProvider 对接 light/dark 与 zh-CN/en-US
ai_check_if_needed:
  - 是否有处理状态
  - 是否有来源证据
  - 是否有风险提示和人工接管
```

如果任一检查不通过，必须自行修正后再输出最终设计。

---

## 11. 单次投喂提示词模板

将本文件作为唯一上下文后，只需要追加一句任务即可，例如：

```text
请基于本规范，设计一个客服排班系统的企业级 Web 工作台页面，并支持浅色/深色主题切换与中英文语言切换。前端实现必须使用 Vue 3 + Naive UI，且 Naive UI 是唯一基础组件库。
```

如果设计代理要求更明确的输出目标，可以使用：

```text
请基于本规范，直接生成一个高保真 UI 页面。不要输出说明文档，不要输出实现备注，不要渲染本规范文本到页面中。页面主题：客服排班系统。系统必须支持 light/dark 主题切换，以及 zh-CN/en-US 语言切换。若生成前端代码，必须使用 Vue 3 + Naive UI，不得使用其他基础组件库。
```

---

## 12. 变更记录

```yaml
v1.2:
  date: "2026-04-13"
  changes:
    - 将文档定位从 Figma Make 专用调整为所有 AI 设计/开发软件通用
    - 增加 Vue 3 + Naive UI 前端实现硬约束
    - 限定 Naive UI 为唯一基础 UI 组件库
    - 增加 Naive UI 基础组件映射、NConfigProvider、themeOverrides、locale/dateLocale 规则
    - 增加 component_library_check 自检项
v1.1:
  date: "2026-04-13"
  changes:
    - 增加 light/dark 主题切换硬约束
    - 增加 zh-CN/en-US 语言切换硬约束
    - 增加主题语义 token 和本地化规则
    - 增加 BrThemeToggle 与 BrLanguageSwitcher 组件要求
    - 更新输出自检与单次投喂模板
v1.0:
  date: "2026-04-13"
  changes:
    - 建立单文档执行版
    - 增加无二次对话的自动判断与硬性防错规则
    - 增加客服排班场景专用结构
    - 增加占位文案、响应式、品牌缺失、通用后台等失败拦截规则
```
