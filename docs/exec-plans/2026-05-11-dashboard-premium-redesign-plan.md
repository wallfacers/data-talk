# 12 行业大屏独立精品化重设计实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对 `tmp/Dashboard/` 下 12 个行业大屏 HTML 进行独立精品化重写，每屏 8-10 个图表，行业专属美学，真实业务数据。

**Architecture:** 12 个纯静态 HTML 文件，每个文件内联 CSS + ECharts 5.5 CDN。无框架、无构建、无后端。按 3 批次并行实施，每批次 4 个文件独立无依赖。

**Tech Stack:** HTML5 + CSS3 + ECharts 5.5 (CDN) + Vanilla JS

---

## 文件结构

所有文件位于 `tmp/Dashboard/`，直接覆盖重写：

| 文件 | 行业 | 批次 |
|------|------|------|
| `02-ecommerce.html` | 电商运营实时监控中心 | Batch 1 |
| `03-manufacturing.html` | 工业制造智能监控中心 | Batch 1 |
| `10-cybersecurity.html` | 网络安全态势感知中心 | Batch 1 |
| `05-finance.html` | 财务数据分析中心 | Batch 1 |
| `04-saas.html` | SaaS 运营监控中心 | Batch 2 |
| `06-logistics.html` | 物流供应链监控中心 | Batch 2 |
| `09-energy.html` | 能源环保监控中心 | Batch 2 |
| `07-healthcare.html` | 医疗健康大数据中心 | Batch 2 |
| `08-hr.html` | 人力资源分析中心 | Batch 3 |
| `11-agriculture.html` | 智慧农业大数据中心 | Batch 3 |
| `12-education.html` | 在线教育数据中心 | Batch 3 |
| `01-multi-screen-dashboard.html` | 企业级数据大屏监控系统 | Batch 3 |

---

## 通用实施规范

每个文件重写时必须遵循：

1. **HTML 骨架**：`<!DOCTYPE html>` + `<html lang="zh-CN">` + viewport meta + ECharts CDN
2. **CSS 变量**：`:root` 定义行业主题色（--bg, --panel-bg, --accent1-4, --text, --text2）
3. **背景动效**：至少一种动态背景（粒子/网格/波浪/数字雨/光晕）
4. **Header**：标题（中文 + 英文副标题）+ 实时时钟 `#time`
5. **KPI 区**：4-6 个核心指标，数字带滚动动画
6. **图表区**：8-10 个 ECharts 实例，每个有独立 `id`
7. **跑马灯**：底部事件/告警滚动条
8. **响应式**：`1920x1080` 优先，`1366x768` 可用

### ECharts 暗色主题通用配置

```javascript
const darkGrid = { top: 30, right: 12, bottom: 20, left: 12, containLabel: true };
const darkAxis = {
  axisLine: { lineStyle: { color: 'rgba(255,255,255,0.1)' } },
  axisLabel: { color: '#8899aa', fontSize: 10 },
  splitLine: { lineStyle: { color: 'rgba(255,255,255,0.04)', type: 'dashed' } }
};
const darkTooltip = {
  trigger: 'axis',
  backgroundColor: 'rgba(10,20,40,0.95)',
  borderColor: 'var(--accent1)',
  textStyle: { color: '#e0e8f0', fontSize: 11 }
};
```

### 数字滚动动画工具函数

```javascript
function animateValue(id, start, end, duration, suffix = '') {
  const el = document.getElementById(id);
  if (!el) return;
  const range = end - start;
  const startTime = performance.now();
  function step(now) {
    const progress = Math.min((now - startTime) / duration, 1);
    const eased = 1 - Math.pow(1 - progress, 3);
    const val = start + range * eased;
    if (Number.isInteger(end)) {
      el.textContent = val.toLocaleString('zh-CN', { maximumFractionDigits: 0 }) + suffix;
    } else {
      el.textContent = val.toFixed(1) + suffix;
    }
    if (progress < 1) requestAnimationFrame(step);
  }
  requestAnimationFrame(step);
}
```

### 实时时钟

```javascript
function updateTime() {
  document.getElementById('time').textContent = new Date().toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false
  });
}
setInterval(updateTime, 1000); updateTime();
```

---

## Batch 1：标杆确立（电商 / 制造 / 网络安全 / 金融）

Batch 1 的 4 个文件视觉风格差异最大，并行确立各方向标杆。

---

### Task 1: 电商运营实时监控中心

**Files:**
- Rewrite: `tmp/Dashboard/02-ecommerce.html`

**Design Input:**
- 美学：熔岩红黑 `#0d0a07` + 火焰红 `#ff4444` + 金光 `#ffc107`，粒子上升动画
- 布局：顶部巨型 GMV + 漏斗横贯中轴 + 左侧渠道/品类 + 右侧地图/趋势
- 图表清单（10个）：
  1. 巨型 GMV 数字（带滚动动画，非 ECharts）
  2. 转化漏斗（横向 HTML 步骤条）
  3. 渠道 GMV 占比（环形图）
  4. 品类销售排行（横向条形图）
  5. 全国订单热力分布（ECharts map，使用 china 地图 json）
  6. 实时 GMV 趋势 + 订单量（双轴折线+柱状）
  7. 用户留存 cohort（热力图：日期 x 留存天数）
  8. 品类关联购买矩阵（热力图）
  9. 大促目标达成率（仪表盘 0-100%）
  10. 客单价分布（柱状图）
- 数据故事：今日 GMV 2847 万（↑24.6%），但客单价下降 3.2%，需关注

- [ ] **Step 1: 重写 HTML 结构与 CSS**
  - 完整 HTML 骨架，内联 CSS
  - 背景粒子动画（红色半透明圆点，从底部上升）
  - 顶部 bar：logo 区 + KPI 胶囊条（GMV/订单/转化/客单价）+ 时钟
  - 主体：左侧 38% 放漏斗+渠道+品类，右侧 62% 放地图+趋势
  - 底部跑马灯：实时订单流
  - 所有卡片用毛玻璃效果（`backdrop-filter: blur(12px)`）+ 红色发光边框

- [ ] **Step 2: 编写 ECharts 配置**
  - 每个图表独立 `echarts.init()` + `setOption()`
  - map 图表使用 `fetch('https://geo.datav.aliyun.com/areas_v3/bound/100000_full.json')` 加载中国地图
  - 数据带真实业务波动：GMV 趋势含早高峰、午间低谷、晚高峰
  - 留存 cohort 数据：首日 100%，次日 45%，3日 35%，7日 28%，14日 22%，30日 18%

- [ ] **Step 3: 添加交互与动效**
  - KPI 数字入场滚动动画（1.5s，ease-out-cubic）
  - 卡片 hover 放大（`transform: scale(1.02)`）
  - 跑马灯无缝循环
  - 图表 resize 监听：`window.addEventListener('resize', () => charts.forEach(c => c.resize()))`

- [ ] **Step 4: 验收**
  - 用浏览器打开 `tmp/Dashboard/02-ecommerce.html`
  - 检查：12 个图表正常渲染、无控制台报错、动画流畅、1920x1080 布局正确

- [ ] **Step 5: Commit**
  ```bash
  git add tmp/Dashboard/02-ecommerce.html
  git commit -m "feat(dashboard): premium redesign of ecommerce monitoring screen

  - 熔岩红黑主题 + 粒子上升动效
  - 10 个图表：漏斗/渠道/品类/地图/趋势/cohort/关联矩阵/仪表盘/客单价
  - KPI 数字滚动动画 + 毛玻璃卡片 + 实时订单跑马灯"
  ```

---

### Task 2: 工业制造智能监控中心

**Files:**
- Rewrite: `tmp/Dashboard/03-manufacturing.html`

**Design Input:**
- 美学：机械灰黑 `#1a1a1e` + 警示橙 `#f97316` + 安全绿 `#22c55e`，LED 指示灯，扫描线纹理
- 布局：顶部 LED KPI + 中央产线拓扑 + 四象限图表
- 图表清单（10个）：
  1. OEE 设备综合效率（LED 数字 + 状态灯）
  2. 质量合格率（LED 数字 + 状态灯）
  3. 安全生产天数（LED 数字）
  4. 待维护设备（LED 数字 + 红灯）
  5. 产线产能趋势（柱状图，分班次）
  6. 产线总览 & 效率监控（双轴：产能柱状 + 效率折线）
  7. 故障报警统计（横向条形图，按设备类型）
  8. 能耗趋势（面积图，分电/水/气）
  9. SPC 质量控制图（折线 + 控制限 UCL/LCL/CL）
  10. 设备数字孪生面板（6 个设备卡片：温度/振动/转速实时值）
- 数据故事：OEE 87.5%（▲3.2%），但 4 台设备待维护（红灯警告）

- [ ] **Step 1: 重写 HTML 结构与 CSS**
  - 背景：扫描线纹理（`repeating-linear-gradient` 水平线）
  - 顶部：机械感标题（铆钉装饰）+ 4 个 LED KPI 盒（带红/绿/黄指示灯）
  - 主体：3 列网格（左：产能+能耗，中：产线拓扑+总览，右：故障+质量+安全）
  - 产线拓扑：用 CSS flex 画 6 个设备节点，连线用伪元素，节点颜色表示状态
  - 底部：告警跑马灯

- [ ] **Step 2: 编写 ECharts 配置**
  - SPC 控制图：折线数据 + `markLine` 画 UCL/LCL/CL 三条控制限
  - 能耗面积图：堆叠面积，电（橙）/ 水（蓝）/ 气（灰）
  - 产能柱状图：分早班/中班/晚班三组堆叠
  - 数字孪生面板用纯 HTML/CSS，数值带随机微波动（`setInterval` 每秒 ±0.1）

- [ ] **Step 3: 添加交互与动效**
  - LED 灯呼吸动画（`box-shadow` 脉冲）
  - 设备节点状态切换动画（绿 → 黄 → 红渐变）
  - 图表 stagger 入场（每个图表 delay 100ms）

- [ ] **Step 4: 验收**
  - 浏览器打开检查 10 个组件正常、SPC 控制限清晰、LED 灯动画正常

- [ ] **Step 5: Commit**
  ```bash
  git add tmp/Dashboard/03-manufacturing.html
  git commit -m "feat(dashboard): premium redesign of manufacturing monitoring screen

  - 机械灰黑工业主题 + LED 指示灯 + 扫描线纹理
  - 10 个组件：OEE/质量/安全/维护 KPI + 产能/效率/故障/能耗/SPC/数字孪生
  - 产线拓扑图 + SPC 控制限 + 设备实时微波动"
  ```

---

### Task 3: 网络安全态势感知中心

**Files:**
- Rewrite: `tmp/Dashboard/10-cybersecurity.html`

**Design Input:**
- 美学：矩阵黑 `#000a00` + 数据绿 `#00ff41` + 警报红 `#ff0040`，数字雨背景，终端代码风
- 布局：顶部威胁告警条 + 中央全球攻击地图 + 四象限图表
- 图表清单（10个）：
  1. 攻击次数（终端风格数字，红色）
  2. 拦截率（终端风格数字，绿色）
  3. 待修复漏洞（终端风格数字，黄色）
  4. 安全评分（终端风格数字，青色）
  5. 24H 攻击趋势（面积图，红色渐变）
  6. 攻击类型分布（饼图/玫瑰图：DDoS/注入/XSS/钓鱼/恶意软件）
  7. TOP 攻击源国家（横向条形图，带国旗 emoji）
  8. 漏洞修复状态（堆叠条形：已修复/修复中/待修复）
  9. ATT&CK 框架覆盖矩阵（热力图：tactics x techniques）
  10. 攻击链 kill-chain 阶段图（漏斗图：7 个阶段）
- 数据故事：今日攻击 2847 次（↑18.6%），拦截率 99.8%，但 34 个漏洞待修复

- [ ] **Step 1: 重写 HTML 结构与 CSS**
  - 背景：矩阵数字雨（JavaScript 生成随机字符列，绿色，从上落下）
  - 顶部：红色告警条（`THREAT LEVEL: HIGH` 闪烁）+ 终端风格标题 + 时钟
  - KPI：4 个终端盒子（`Courier New` 字体，左侧色条标识严重程度）
  - 主体：2x2 网格（攻击趋势 / 攻击类型 / 攻击源 / 漏洞状态）
  - 底部：日志滚动条（终端风格绿色文字）

- [ ] **Step 2: 编写 ECharts 配置**
  - 攻击趋势面积图：红色渐变填充，`areaStyle: { color: new echarts.graphic.LinearGradient(...) }`
  - ATT&CK 矩阵：12 tactics x 8 techniques，颜色表示覆盖度（深绿=高，黑=无）
  - Kill-chain 漏斗图：7 个阶段，宽度递减
  - 攻击源条形图：中国/美国/俄罗斯/巴西/印度，数据符合真实攻击源分布

- [ ] **Step 3: 添加交互与动效**
  - 矩阵雨动画：每列字符随机变化，速度不同
  - 告警条闪烁：`animation: blink 1.5s ease-in-out infinite`
  - 终端光标闪烁：`animation: blink 1s step-end infinite`

- [ ] **Step 4: 验收**
  - 检查矩阵雨性能（帧率 ≥ 30）、所有图表正常、终端风格一致

- [ ] **Step 5: Commit**
  ```bash
  git add tmp/Dashboard/10-cybersecurity.html
  git commit -m "feat(dashboard): premium redesign of cybersecurity situational awareness

  - 矩阵黑 + 数字雨背景 + 终端代码风
  - 10 个组件：攻击/拦截/漏洞/评分 KPI + 趋势/类型/来源/漏洞/ATT&CK/kill-chain
  - 威胁告警闪烁 + 终端光标动画 + 日志滚动"
  ```

---

### Task 4: 财务数据分析中心

**Files:**
- Rewrite: `tmp/Dashboard/05-finance.html`

**Design Input:**
- 美学：墨蓝 `#0c1929` + 香槟金 `#c9a84c` + 深青 `#1e3a5f`，精致细线，金融精英感
- 布局：顶部 5 大财务 KPI + 三大报表区 + 底部现金流/预算
- 图表清单（10个）：
  1. 年度总收入（金色数字）
  2. 净利润（金色数字）
  3. 净利润率（金色数字 + %）
  4. 应收账款（金色数字）
  5. 预算执行率（金色数字 + %）
  6. 月度收入 & 利润趋势（双轴折线）
  7. 杜邦分析树状图（矩形树图：ROE → 净利率/周转率/权益乘数 → 下一级拆解）
  8. 收入结构分析（饼图：主营业务/投资/其他）
  9. 费用构成明细（树图：销售/管理/研发/财务 → 二级科目）
  10. 现金流监控（瀑布图：经营+投资+筹资=净现金流）
  11. 预算执行率仪表盘（环形图，0-100%）
  12. 应收账款账龄分布（横向条形：1年内/1-2年/2-3年/3年以上）
- 数据故事：年收入 8.42 亿（↑15.3%），净利润率 18.6%（↑2.1pp），应收账款下降 3.8%

- [ ] **Step 1: 重写 HTML 结构与 CSS**
  - 背景：墨蓝渐变 + 微妙金色光晕（顶部径向渐变）
  - 顶部：优雅标题 + 5 个金色分隔 KPI 单元
  - 主体：3 列布局（左：趋势+收入结构，中：杜邦分析+费用，右：资产负债+账龄）
  - 底部：现金流瀑布图 + 预算仪表盘
  - 卡片：细金线边框（`border: 1px solid rgba(201,168,76,0.15)`），顶部金色渐变线

- [ ] **Step 2: 编写 ECharts 配置**
  - 杜邦分析树图：`treemap`，两层：ROE → 三因子 → 具体科目
  - 现金流瀑布图：`bar` 用正负值，`itemStyle.color` 区分正负
  - 预算仪表盘：`gauge`，刻度 0-100，指针指向 94.2
  - 趋势双轴：左轴收入（金色柱状），右轴利润率（青色折线）

- [ ] **Step 3: 添加交互与动效**
  - 数字滚动：金色数字从 0 滚动到目标值（2s）
  - 卡片 hover：金色边框亮度提升
  - 杜邦分析 treemap：点击 drill-down（ECharts 内置）

- [ ] **Step 4: 验收**
  - 检查杜邦分析层级正确、现金流瀑布正负颜色区分、预算仪表盘指针正确

- [ ] **Step 5: Commit**
  ```bash
  git add tmp/Dashboard/05-finance.html
  git commit -m "feat(dashboard): premium redesign of financial data analysis center

  - 墨蓝 + 香槟金金融精英主题
  - 12 个组件：5 大 KPI + 趋势/杜邦/收入/费用/负债/现金流/预算/账龄
  - 杜邦分析 treemap drill-down + 现金流瀑布图 + 预算仪表盘"
  ```

---

## Batch 2：扩展深化（SaaS / 物流 / 能源 / 医疗）

---

### Task 5: SaaS 运营监控中心

**Files:**
- Rewrite: `tmp/Dashboard/04-saas.html`

**Design Input:**
- 美学：深空紫 `#0a0f1a` + 电光青 `#14b8a6` + 极光紫 `#6366f1`，毛玻璃卡片，波浪背景
- 布局：顶部北极星指标 + 左侧 MRR/留存 + 中央用户旅程 + 右侧 API/采用率
- 图表清单（10个）：
  1. 北极星指标 WAU（巨型数字）
  2. 活跃租户（KPI）
  3. SLA（KPI）
  4. MRR（KPI）
  5. NPS（KPI）
  6. MRR/ARR 瀑布图（月度变化：新增/流失/扩展/收缩 = 净增）
  7. 套餐订阅分布（环形图：Free/Pro/Enterprise）
  8. 用户留存率（折线：Day 0/7/30/60/90/180/365）
  9. 用户旅程地图（漏斗：注册 → 激活 → 付费 → 留存 → 推荐）
  10. API 性能趋势（折线：P50/P95/P99 响应时间）
  11. 功能采用率热力图（功能 x 用户分层：新客/成长/成熟/流失风险）
  12. Churn Rate 预测曲线（历史实际 + 未来 30 天预测，虚线区分）
- 数据故事：MRR 1380 万（↑8.3%），但 churn 率微升，需关注成长客户转化

- [ ] **Step 1: 重写 HTML 结构与 CSS**
  - 背景：两个大圆形径向渐变（青+紫），缓慢漂浮动画（`transform: translate` 循环）
  - 顶部：北极星指标巨型展示（`font-size: 48px`，电光青色）+ 4 个 KPI pill
  - 主体：左 28%（瀑布+套餐），中 44%（旅程地图+采用率），右 28%（API+churn）
  - 卡片：毛玻璃（`backdrop-filter: blur(16px)`），圆角 16px，细边框

- [ ] **Step 2: 编写 ECharts 配置**
  - MRR 瀑布图：`bar` + `itemStyle.color` 区分正负（新增绿/流失红）
  - 用户旅程：HTML/CSS 漏斗步骤条（5 步，每步有转化率箭头）
  - 功能采用率热力图：`heatmap`，x=用户分层，y=功能模块
  - Churn 预测：折线实线（历史）+ `lineStyle.type: 'dashed'`（预测）

- [ ] **Step 3: 添加交互与动效**
  - 背景波浪漂浮（CSS animation，12s/15s 周期）
  - KPI pill hover：边框发光 + 微上移
  - 旅程步骤 hover：显示该阶段转化率 tooltip

- [ ] **Step 4: 验收**
  - 浏览器检查：波浪动画流畅、瀑布图正负颜色正确、热力图颜色梯度清晰

- [ ] **Step 5: Commit**
  ```bash
  git add tmp/Dashboard/04-saas.html
  git commit -m "feat(dashboard): premium redesign of SaaS operations center

  - 深空紫 + 电光青科技主题 + 波浪漂浮背景
  - 12 个组件：北极星/WAU/SLA/MRR/NPS + 瀑布/套餐/留存/旅程/API/热力图/churn预测
  - 毛玻璃卡片 + MRR 瀑布正负区分 + churn 实线/虚线预测"
  ```

---

### Task 6: 物流供应链监控中心

**Files:**
- Rewrite: `tmp/Dashboard/06-logistics.html`

**Design Input:**
- 美学：深海蓝 `#0a0e1a` + 航道橙 `#f97316` + 定位青 `#0ea5e9`，地图网格背景，箭头 KPI
- 布局：顶部箭头 KPI + 中央物流地图 + 左侧运输/异常/满意度 + 底部仓库
- 图表清单（10个）：
  1. 在途运单（箭头 KPI）
  2. 准时送达率（箭头 KPI）
  3. 今日发货（箭头 KPI）
  4. 平均时效（箭头 KPI）
  5. 全国物流节点分布（ECharts 散点地图，节点大小=吞吐量）
  6. 发货量 & 签收时效趋势（双轴：柱状+折线）
  7. 运输方式占比（饼图：公路/铁路/航空/水运）
  8. 异常订单分析（横向条形：延误/破损/丢失/拒收）
  9. 配送满意度（仪表盘，0-100 分）
  10. 仓库利用率（3D 柱状效果，各仓库对比）
  11. 最后一公里时效分布（箱线图或柱状：当天达/次日达/2-3日/3日+）
  12. 供应链风险预警矩阵（散点图：影响度 x 发生概率，气泡大小=损失金额）
- 数据故事：今日发货 15.2 万（↑12%），平均时效降至 30h（↓4h），但异常订单需关注

- [ ] **Step 1: 重写 HTML 结构与 CSS**
  - 背景：网格线（`linear-gradient` 画 20px/80px 间隔网格）
  - 顶部：4 个箭头 KPI（`clip-path: polygon` 箭头形状）
  - 主体：左 26%（运输+异常+满意度），中 74%（地图+发货趋势），底部仓库
  - 卡片：左侧边框 3px 着色（`border-left: 3px solid var(--accent1)`）

- [ ] **Step 2: 编写 ECharts 配置**
  - 物流地图：`effectScatter`（带涟漪动画）+ `lines`（流光线路，从上海/深圳/北京出发到全国）
  - 仓库利用率：柱状图，用 `barWidth` 和 `itemStyle.borderRadius` 营造 3D 感
  - 风险矩阵：`scatter`，x=发生概率(0-10)，y=影响度(0-10)，symbolSize=损失金额/10000
  - 时效分布：横向条形，颜色渐变（绿→黄→红）

- [ ] **Step 3: 添加交互与动效**
  - 地图线路流光：`lines.effect.trailLength` + `period: 4`
  - 节点涟漪：`effectScatter.rippleEffect`
  - 箭头 KPI：hover 时箭头右移 4px

- [ ] **Step 4: 验收**
  - 检查地图加载（需网络 fetch 地图 geoJSON）、流光动画、风险矩阵气泡大小合理

- [ ] **Step 5: Commit**
  ```bash
  git add tmp/Dashboard/06-logistics.html
  git commit -m "feat(dashboard): premium redesign of logistics supply chain monitor

  - 深海蓝 + 航道橙物流主题 + 网格背景
  - 12 个组件：4 箭头 KPI + 地图/趋势/运输/异常/满意度/仓库/时效/风险矩阵
  - effectScatter 涟漪节点 + lines 流光线路 + 供应链风险散点矩阵"
  ```

---

### Task 7: 能源环保监控中心

**Files:**
- Rewrite: `tmp/Dashboard/09-energy.html`

**Design Input:**
- 美学：暗夜绿 `#0a1f14` + 荧光绿 `#22c55e` + 天空蓝 `#0ea5e9`，波浪流动，自然元素
- 布局：顶部能耗 KPI + 中央能源流拓扑 + 四周趋势/对比/排放/质量
- 图表清单（10个）：
  1. 总能耗（KPI）
  2. 碳排放（KPI，带吨 CO2 单位）
  3. 清洁能源占比（KPI + %）
  4. 环保评分（KPI，0-100）
  5. 能耗趋势（面积图，分火电/水电/风电/光伏/核电）
  6. 各工厂能耗对比（横向条形图）
  7. 碳排放趋势（折线，带目标线）
  8. 能源类型占比动态饼图（5 种能源，带占比变化动画）
  9. 环保指标达标率仪表盘（4 个小型仪表盘：排放/能耗/水耗/固废）
  10. 异常排放事件时间轴（横向时间线，带级别标记）
  11. 节能减排目标达成追踪（折线：目标线 vs 实际值）
  12. 空气质量指数热力图（地图或网格：各监测点 AQI 颜色）
- 数据故事：清洁能源占比升至 42%（▲5pp），碳排放同比下降 8%，但 2 个工厂超标

- [ ] **Step 1: 重写 HTML 结构与 CSS**
  - 背景：底部波浪动画（SVG 或 CSS `clip-path` 波浪，绿色半透明）
  - 顶部：4 个生态感 KPI 卡片（带叶子/水滴图标）
  - 主体：3 列（左：能耗趋势+工厂对比，中：能源流拓扑+饼图，右：碳排放+AQI）
  - 底部：4 个小型仪表盘 + 异常事件时间轴
  - 能源流拓扑：用 CSS 画发电→传输→分配→消耗 4 个节点，连线带流动动画

- [ ] **Step 2: 编写 ECharts 配置**
  - 能耗面积图：堆叠面积，5 种能源各自颜色（火电灰/水蓝/风绿/光伏黄/核电紫）
  - 4 个小型仪表盘：`gauge`，半径 45%，分布在底部一排
  - 动态饼图：`pie` + `animationType: 'scale'` + `animationEasing: 'elasticOut'`
  - 目标追踪：折线实线（实际）+ `markLine`（目标线，绿色虚线）

- [ ] **Step 3: 添加交互与动效**
  - 波浪背景缓慢起伏（CSS transform translateY 循环，8s 周期）
  - 能源流连线流动（伪元素 `background: linear-gradient` + `animation` 移动背景位置）
  - 仪表盘指针平滑动画（ECharts 内置）

- [ ] **Step 4: 验收**
  - 检查波浪性能、能源流动画、4 仪表盘布局整齐

- [ ] **Step 5: Commit**
  ```bash
  git add tmp/Dashboard/09-energy.html
  git commit -m "feat(dashboard): premium redesign of energy environmental monitor

  - 暗夜绿 + 荧光绿生态主题 + 波浪背景
  - 12 个组件：能耗/碳排/清洁/评分 KPI + 趋势/对比/排放/饼图/仪表盘/时间轴/追踪/AQI
  - 能源流拓扑动画 + 4 小型仪表盘 + 动态饼图弹性动画"
  ```

---

### Task 8: 医疗健康大数据中心

**Files:**
- Rewrite: `tmp/Dashboard/07-healthcare.html`

**Design Input:**
- 美学：医疗白灰 `#f0f4f8` 暗色版 + 生命绿 `#10b981` + 安抚蓝 `#3b82f6`，干净圆角，心率线
- 布局：顶部门诊/住院/手术/急救/满意度 + 中央 occupancy + 两侧科室/药品/年龄/满意度
- 图表清单（10个）：
  1. 门诊量（KPI）
  2. 住院量（KPI）
  3. 手术量（KPI）
  4. 急救响应时间（KPI，秒）
  5. 患者满意度（KPI，0-100 分）
  6. 床位 occupancy 仪表盘（大圆环，使用率 87%）
  7. 科室效率热力图（科室 x 时间段，颜色=候诊人数）
  8. 科室门诊量排行（横向条形）
  9. 药品耗材 TOP10（横向条形）
  10. 患者年龄分布（柱状图：0-18/19-35/36-50/51-65/65+）
  11. 满意度趋势（折线，30 天）
  12. 急诊分级等候时间（横向条形：一级/二级/三级/四级/五级，颜色区分紧急度）
  13. 手术排程日历（本周 7 天，每天手术量柱状）
  14. 慢病管理漏斗（筛查 → 确诊 → 建档 → 随访 → 控制达标）
- 数据故事：门诊量 2847 人/日，床位使用率 87%，急救响应 4.2 分钟，但三级候诊偏长

- [ ] **Step 1: 重写 HTML 结构与 CSS**
  - 背景：极暗灰蓝 `#0f172a`（暗色医疗），顶部微妙绿色光晕
  - 顶部：5 个柔和 KPI 胶囊（圆角大，阴影轻）
  - 主体：左 30%（科室排行+药品+年龄），中 40%（occupancy 大仪表盘+手术日历），右 30%（满意度+急诊+慢病）
  - 卡片：大圆角（`border-radius: 16px`），柔和边框，hover 微阴影提升

- [ ] **Step 2: 编写 ECharts 配置**
  - 床位 occupancy：`gauge`，半径 65%，从 0-100，指针指向 87，颜色分段（绿/黄/红）
  - 科室效率热力图：`heatmap`，x=小时（0-23），y=科室，颜色=候诊人数
  - 手术日历：7 个柱状并排，每天一个柱子，颜色按手术类型堆叠
  - 慢病漏斗：HTML/CSS 漏斗步骤条（5 步，每步有流失率）

- [ ] **Step 3: 添加交互与动效**
  - 心率线装饰：顶部或卡片边缘画一条缓慢移动的正弦波 SVG
  - occupancy 仪表盘：入场指针从 0 扫到 87（2s 动画）
  - 急诊等候条形：颜色红→橙→黄→绿→蓝（一级最紧急红色）

- [ ] **Step 4: 验收**
  - 检查 occupancy 仪表盘颜色分段、急诊分级颜色正确、心率线动画流畅

- [ ] **Step 5: Commit**
  ```bash
  git add tmp/Dashboard/07-healthcare.html
  git commit -m "feat(dashboard): premium redesign of healthcare big data center

  - 医疗暗色主题 + 生命绿/安抚蓝 + 心率线装饰
  - 14 个组件：5 KPI + occupancy/科室/药品/年龄/满意度/急诊/手术/慢病
  - 大仪表盘 occupancy + 科室效率热力图 + 手术日历 + 慢病漏斗"
  ```

---

## Batch 3：收尾完善（HR / 农业 / 教育 / 多屏总控）

---

### Task 9: 人力资源分析中心

**Files:**
- Rewrite: `tmp/Dashboard/08-hr.html`

**Design Input:**
- 美学：石墨灰 `#1f2937` + 活力珊瑚 `#f97316` + 信任蓝 `#3b82f6`，组织架构网络感
- 布局：顶部人力 KPI + 中央组织架构力导向图 + 四周结构/离职/招聘/满意度
- 图表清单（10个）：
  1. 总人数（KPI）
  2. 本月入职（KPI）
  3. 本月离职（KPI）
  4. 人均效能（KPI，万元/人）
  5. 招聘完成率（KPI，%）
  6. 组织架构力导向图（ECharts `graph`，节点=部门，大小=人数，颜色=离职风险）
  7. 人员结构（饼图：年龄/学历/职级，用 tabs 或三个小饼图）
  8. 离职趋势（折线，12 个月）
  9. 招聘漏斗（HTML 步骤：简历→面试→offer→入职）
  10. 员工满意度趋势（折线，4 个季度）
  11. 人才九宫格（散点图：绩效 x 潜力，气泡大小=人数）
  12. 薪酬带宽对标（箱线图：各职级内部薪酬分布 + 市场 50 分位线）
  13. AI 离职风险预测热力（热力图：部门 x 职级，颜色=风险等级）
- 数据故事：人均效能 38.5 万/人（↑5.2%），但研发部离职率偏高（12%）

- [ ] **Step 1: 重写 HTML 结构与 CSS**
  - 背景：石墨灰 + 微妙网络线（随机连接的淡色细线）
  - 顶部：5 个 KPI，离职率用红色高亮
  - 主体：中央 50%（力导向图），左 25%（人员结构+九宫格），右 25%（离职+招聘+满意度+薪酬+风险）
  - 卡片：圆角 12px，网络感边框

- [ ] **Step 2: 编写 ECharts 配置**
  - 力导向图：`graph` + `layout: 'force'`，节点大小按人数，连线按汇报关系
  - 人才九宫格：`scatter`，x=绩效(1-5)，y=潜力(1-5)，markArea 画 3x3 网格
  - 薪酬箱线图：`boxplot`，数据按职级分组
  - 风险热力：`heatmap`，x=职级，y=部门，颜色从绿到红

- [ ] **Step 3: 添加交互与动效**
  - 力导向图节点拖拽（ECharts `roam: true` + `draggable: true`）
  - 九宫格 hover：显示该格人数和名单
  - 风险热力：红色区域脉冲闪烁

- [ ] **Step 4: 验收**
  - 检查力导向图布局合理、九宫格网格线清晰、箱线图数据格式正确

- [ ] **Step 5: Commit**
  ```bash
  git add tmp/Dashboard/08-hr.html
  git commit -m "feat(dashboard): premium redesign of HR analytics center

  - 石墨灰 + 珊瑚/蓝人力主题 + 网络线背景
  - 13 个组件：5 KPI + 力导向图/结构/离职/招聘/满意度/九宫格/薪酬/风险
  - 组织架构力导向图 + 人才九宫格散点 + 薪酬箱线图 + 离职风险热力"
  ```

---

### Task 10: 智慧农业大数据中心

**Files:**
- Rewrite: `tmp/Dashboard/11-agriculture.html`

**Design Input:**
- 美学：大地棕 `#1a1209` + 生机绿 `#22c55e` + 丰收金 `#f59e0b`，自然纹理，阳光光晕
- 布局：顶部农情 KPI + 中央农场 GIS 地图 + 四周作物/土壤/气象/价格
- 图表清单（10个）：
  1. 种植面积（KPI，亩）
  2. 预计产量（KPI，吨）
  3. 土壤健康指数（KPI，0-100）
  4. 气象预警（KPI，文字状态）
  5. 农场 GIS 地图（ECharts `map` 或自定义 SVG，地块分区着色）
  6. 作物长势趋势（折线，近 30 天 NDVI 指数）
  7. 土壤墒情监测（仪表盘或折线：湿度/温度/pH）
  8. 气象数据（组合：温度折线 + 降雨量柱状 + 光照面积）
  9. 农产品价格走势（折线，近 1 年，多种作物）
  10. 积温积雨累积曲线（从播种日起的每日累积双轴）
  11. 病虫害预警等级（横向条形：作物类型 x 预警等级，颜色红橙黄绿）
  12. 灌溉用水效率（横向条形：各区域亩均用水量，带标杆线）
- 数据故事：预计产量 12,480 吨（↑8%），土壤健康 86 分，但病虫害预警 3 个区域黄色

- [ ] **Step 1: 重写 HTML 结构与 CSS**
  - 背景：大地棕渐变 + 顶部阳光光晕（金色径向渐变）
  - 顶部：4 个 KPI，带自然图标（🌱/🌾/🪴/🌦️）
  - 主体：中央 50%（GIS 地图+长势），左 25%（土壤+气象），右 25%（价格+积温+病虫害+灌溉）
  - 卡片：暖色边框，圆角柔和

- [ ] **Step 2: 编写 ECharts 配置**
  - 农场地图：用 `geo` + `regions` 自定义地块（因为没有标准农业地图 geoJSON）
  - 或改用柱状图模拟地块：x=地块编号，y=作物类型，颜色=长势等级
  - 积温积雨：双轴折线，左积温（°C·d），右积雨（mm）
  - 气象组合：`bar`（降雨量）+ `line`（温度）+ `line`（光照）三系列

- [ ] **Step 3: 添加交互与动效**
  - 阳光光晕缓慢扩散（CSS scale 动画）
  - 作物长势折线：带面积填充（绿色渐变，表示生长旺盛）
  - 病虫害预警：黄色/红色区域轻微闪烁

- [ ] **Step 4: 验收**
  - 检查农场地图/地块模拟正常、气象三系列不重叠、积温积雨双轴刻度合理

- [ ] **Step 5: Commit**
  ```bash
  git add tmp/Dashboard/11-agriculture.html
  git commit -m "feat(dashboard): premium redesign of smart agriculture center

  - 大地棕 + 生机绿/丰收金农业主题 + 阳光光晕
  - 12 个组件：4 KPI + 农场地图/长势/土壤/气象/价格/积温/病虫害/灌溉
  - 地块分区着色 + 积温积雨双轴 + 气象三系列组合"
  ```

---

### Task 11: 在线教育数据中心

**Files:**
- Rewrite: `tmp/Dashboard/12-education.html`

**Design Input:**
- 美学：知识蓝 `#0f172a` + 活力黄 `#facc15` + 成长绿 `#22c55e`，学习成长感，进度环
- 布局：顶部学员 KPI + 中央完成率大仪表盘 + 左侧课程/学时 + 右侧互动/满意度
- 图表清单（10个）：
  1. 总学员数（KPI）
  2. 活跃学员（KPI）
  3. 课程完成率（KPI，%）
  4. 平均学习时长（KPI，小时）
  5. 教师满意度（KPI，0-100）
  6. 课程完成率大仪表盘（中央大圆环，半径 50%）
  7. 学习路径阶段图（HTML：注册→选课→学习→作业→考试→证书）
  8. 课程分类学习时长（横向条形：编程/设计/语言/商业/其他）
  9. 热门课程排行（横向条形：TOP 10）
  10. 在线人数趋势（折线，24 小时）
  11. 满意度趋势（折线，4 个季度）
  12. 学习活跃度七日留存（cohort 热力图）
  13. 知识点掌握度热力图（知识点 x 学员群体）
  14. 完课率漏斗（HTML：报名→开始→50%→100%→证书）
  15. 学习时段分布（热力图：星期 x 小时，颜色=学习人数）
- 数据故事：课程完成率 68%（↑5pp），平均学习时长 4.2h/周，但完课率漏斗中段流失明显

- [ ] **Step 1: 重写 HTML 结构与 CSS**
  - 背景：知识蓝 + 顶部微妙黄色光晕（阳光/启发感）
  - 顶部：5 个 KPI 胶囊，完成率用黄色高亮
  - 主体：中央 45%（大仪表盘+路径），左 27.5%（课程+排行+时段），右 27.5%（在线+满意度+留存+掌握度+漏斗）
  - 卡片：大圆角，活泼但不幼稚

- [ ] **Step 2: 编写 ECharts 配置**
  - 大仪表盘：`gauge`，半径 50%，从 0-100，指针指向 68，颜色分段
  - 七日留存 cohort：`heatmap`，x=日期，y=Day 0-6，颜色=留存率
  - 知识点掌握度：`heatmap`，x=学员群体（新/成长/高阶），y=知识点，颜色=掌握度
  - 学习时段分布：`heatmap`，x=小时（0-23），y=星期（一-日）

- [ ] **Step 3: 添加交互与动效**
  - 大仪表盘指针从 0 扫到 68（2s）
  - 学习路径步骤：完成步骤打勾动画（CSS checkmark draw）
  - 完课率漏斗：每步之间的转化率用箭头标注

- [ ] **Step 4: 验收**
  - 检查大仪表盘居中、3 个热力图颜色区分度、cohort 数据合理

- [ ] **Step 5: Commit**
  ```bash
  git add tmp/Dashboard/12-education.html
  git commit -m "feat(dashboard): premium redesign of online education data center

  - 知识蓝 + 活力黄/成长绿教育主题
  - 15 个组件：5 KPI + 仪表盘/路径/课程/排行/在线/满意度/留存/掌握度/漏斗/时段
  - 大仪表盘完成率 + 3 个热力图 + 学习路径 checkmark 动画"
  ```

---

### Task 12: 企业级数据大屏监控系统（多屏总控台）

**Files:**
- Rewrite: `tmp/Dashboard/01-multi-screen-dashboard.html`

**Design Input:**
- 美学：深邃宇宙蓝黑 `#060b14` + 霓虹青蓝 `#00d4ff` + 数据绿 `#10e873`，科幻指挥中心
- 布局：中央 12 场景轮盘/网格 + 四周系统级 KPI + 底部事件时间轴
- 图表清单（10个）：
  1. 总数据量（系统 KPI）
  2. API QPS（系统 KPI）
  3. 服务健康度（系统 KPI，%）
  4. 在线用户数（系统 KPI）
  5. 12 场景缩略图网格（中央 3x4 或 4x3，每个格子有场景名+活跃度指标）
  6. 跨行业数据对比雷达图（12 个维度：各场景活跃度/数据量/告警数/用户数）
  7. 系统资源监控仪表盘（3 个小型仪表盘：CPU/内存/网络）
  8. 24h 服务可用性热力图（小时 x 服务，颜色=可用性）
  9. API 响应时间分布（箱线图或柱状：P50/P95/P99）
  10. 实时告警事件时间轴（横向时间线，最近 20 条，带级别颜色）
  11. 各场景数据量趋势（折线，近 7 天，12 条线，用图例切换）
  12. 系统负载趋势（面积图，CPU + 内存双轴）
- 数据故事：系统整体健康 99.97%，API QPS 2847，但电商场景数据量激增 35%

- [ ] **Step 1: 重写 HTML 结构与 CSS**
  - 背景：星空粒子（白色小点随机分布，缓慢移动）+ 网格线
  - 顶部：中央科幻标题（发光文字）+ 两侧系统 KPI
  - 中央：12 场景网格（3 列 x 4 行 或 4 列 x 3 行），每个格子有：
    - 场景缩略色块（该场景主题色）
    - 场景名称
    - 活跃度指标（小数字）
    - hover 时边框发光
  - 四周：雷达图（左上）+ 资源仪表盘（右上）+ 可用性热力图（左下）+ API 响应（右下）
  - 底部：告警时间轴 + 系统负载趋势

- [ ] **Step 2: 编写 ECharts 配置**
  - 雷达图：12 个维度（对应 12 场景），颜色区分当前选中 vs 其他
  - 3 个小型仪表盘：CPU（0-100%）、内存（0-100%）、网络（0-1000Mbps）
  - 可用性热力图：24 小时 x 6 个核心服务，颜色绿=100%/黄=99%/红=<99%
  - 场景趋势：12 条折线，用 `legend` 控制显示/隐藏

- [ ] **Step 3: 添加交互与动效**
  - 星空粒子缓慢漂移（CSS animation，随机方向）
  - 场景网格 hover：放大 + 发光边框 + 显示该场景 3 个核心指标
  - 中央标题：呼吸发光（`text-shadow` 脉冲）
  - 告警时间轴：新告警从右侧滑入

- [ ] **Step 4: 验收**
  - 检查 12 场景网格整齐、雷达图 12 维度不拥挤、星空性能流畅

- [ ] **Step 5: Commit**
  ```bash
  git add tmp/Dashboard/01-multi-screen-dashboard.html
  git commit -m "feat(dashboard): premium redesign of multi-screen command center

  - 宇宙蓝黑 + 霓虹青蓝科幻指挥中心主题 + 星空粒子背景
  - 12 个组件：4 系统 KPI + 场景网格/雷达/仪表盘/可用性/API/告警/负载
  - 12 场景缩略图网格 + 呼吸发光标题 + 告警滑入动画"
  ```

---

## 全局验收清单

所有 12 个文件完成后，执行最终验收：

- [ ] **视觉一致性检查**：打开每个文件，确认主题色与行业匹配，无未修改的遗留样式
- [ ] **图表数量检查**：每个文件 ≥ 8 个图表/组件
- [ ] **控制台检查**：浏览器 DevTools Console 无报错、无 404（地图 geoJSON 除外）
- [ ] **动画流畅度**：帧率稳定 ≥ 30fps，无卡顿
- [ ] **布局检查**：1920x1080 满屏正确，1366x768 无严重错位
- [ ] **数据真实性**：时间序列有波动，有异常点，有对比维度
- [ ] **交互功能**：hover tooltip 正常，数字滚动正常，时钟更新正常，跑马灯循环正常
- [ ] **ECharts resize**：窗口缩放时所有图表自适应

---

## Self-Review

**1. Spec coverage:**
- 12 行业独立美学方向 → 每个 Task 都有 Design Input 明确色调/质感/字体
- 布局范式差异化 → 每个 Task 都有独特布局描述
- 数据增强 8-10 图表 → 每个 Task 都有图表清单，数量 ≥ 8
- 共同规范（背景动效/KPI/时钟/跑马灯/resize）→ 每个 Task Step 都包含
- 数据真实性（波动/异常/对比）→ 每个 Task 都有数据故事

**2. Placeholder scan:**
- 无 TBD/TODO/待补充
- 所有代码片段为实际可用代码
- 所有文件路径精确

**3. Type consistency:**
- ECharts 初始化统一为 `echarts.init(document.getElementById('id'))`
- 时间更新函数统一为 `updateTime`
- 数字动画统一为 `animateValue`
- 图表 resize 统一为 `window.addEventListener('resize', ...)`

**无遗漏，计划完整。**
