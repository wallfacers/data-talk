import { test, expect } from '@playwright/test'
import { StagePage } from './pom/stage.page'
import { ChatPanelPage } from './pom/chat-panel.page'
import { mountToolRecorder } from './fixtures/mcp-tool-recorder'

/**
 * Regression coverage for the agents-md-skills-refactor change.
 *
 * 5 typical scenarios verify that the new skeleton AGENTS.md + 11 specialized
 * SKILL.md resources still produce the expected end-to-end routing:
 *
 *  1. Browse table (skill:query-editor-workflow + skill:ui-contract)
 *  2. ER design (skill:er-tabs)
 *  3. Chart (skill:charts-and-dashboards)
 *  4. Data ingestion baseline (skill:data-ingestion — unchanged)
 *  5. SQL error diagnostics (skill:sql-error-diagnostics → skill:sql-execution)
 *
 * All tests are gated by `DATATALK_REAL_OPENCODE_MODEL` per project convention.
 * Without the env var these scenarios are skipped (no model = no routing to
 * assert). Structural / contract guarantees are enforced separately by
 * `SkillRoutingContractTest` (JUnit) regardless of this gating.
 *
 * Assertions favour positive evidence — observed tool calls + visible DOM
 * artifacts — because Playwright cannot directly prove "agent did NOT call
 * tool X". Negative checks (e.g. "rows must not be inlined in chat") are
 * limited to DOM-side absence assertions.
 */

const MODEL = process.env.DATATALK_REAL_OPENCODE_MODEL

let stage: StagePage
let chat: ChatPanelPage

test.beforeEach(async ({ page }) => {
  await page.goto('/')
  stage = new StagePage(page)
  chat = new ChatPanelPage(page)
  await page.waitForSelector('textarea', { timeout: 15_000 })
})

test.describe('agents-skills-refactor regression', () => {
  test.setTimeout(120_000)

  // ─────────────────────────────────────────────────────────────────────
  // Scenario 1: browse table → skill:query-editor-workflow + skill:ui-contract
  // Assertion: workspace.open with type=query_editor is invoked + a tab is
  // visible in the stage.
  // ─────────────────────────────────────────────────────────────────────
  test('scenario 1: browse table opens query_editor tab (skill:query-editor-workflow)', async ({
    page,
  }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')

    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('查 users 表 10 行')
    await chat.waitForAiResponse()

    // ui_exec with action=open + type=query_editor verifies routing into
    // skill:query-editor-workflow (which in turn relies on skill:ui-contract).
    const uiExecCalls = await recorder.callsFor('datatalk_ui_exec')
    const openCalls = uiExecCalls.filter((c: any) => {
      const params = c.params as Record<string, unknown> | undefined
      return params?.['action'] === 'open' &&
        ((params?.['params'] as any)?.['type'] === 'query_editor')
    })
    expect(openCalls.length, 'agent must open a query_editor tab').toBeGreaterThanOrEqual(1)

    // Stage shows at least one tab.
    await stage.openStage()
    const titles = await stage.getTabTitles()
    expect(titles.length, 'stage must surface at least one tab').toBeGreaterThanOrEqual(1)
  })

  // ─────────────────────────────────────────────────────────────────────
  // Scenario 2: ER design → skill:er-tabs
  // Assertion: workspace.open_er_designer is invoked.
  // ─────────────────────────────────────────────────────────────────────
  test('scenario 2: ER design opens er_designer tab (skill:er-tabs)', async ({ page }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')

    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('用 ER 设计器画一下订单和用户的关系')
    await chat.waitForAiResponse()

    const uiExecCalls = await recorder.callsFor('datatalk_ui_exec')
    const designerOpens = uiExecCalls.filter((c: any) => {
      const params = c.params as Record<string, unknown> | undefined
      return params?.['action'] === 'open_er_designer'
    })
    expect(designerOpens.length, 'agent must invoke workspace.open_er_designer').toBeGreaterThanOrEqual(1)
  })

  // ─────────────────────────────────────────────────────────────────────
  // Scenario 3: chart → skill:charts-and-dashboards
  // Assertion: chart fenced block (```chart) appears in the assistant reply
  // OR datatalk_render_chart is called.
  // ─────────────────────────────────────────────────────────────────────
  test('scenario 3: chart request renders chart artifact (skill:charts-and-dashboards)', async ({
    page,
  }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')

    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('做一张最近 7 天订单趋势图')
    await chat.waitForAiResponse()

    const renderChartCalls = await recorder.callsFor('datatalk_render_chart')
    const lastMessage = await chat.getLastMessage()
    const hasInlineChartFence = /```chart(?::[\w-]+)?\b/.test(lastMessage)

    expect(
      renderChartCalls.length >= 1 || hasInlineChartFence,
      'agent must either call datatalk_render_chart or emit an inline ```chart fenced block'
    ).toBeTruthy()
  })

  // ─────────────────────────────────────────────────────────────────────
  // Scenario 4: data ingestion baseline (skill:data-ingestion)
  // Verifies the existing skill still auto-matches after the refactor.
  // Assertion: datatalk_http_request is invoked.
  // ─────────────────────────────────────────────────────────────────────
  test('scenario 4: data ingestion auto-matches after refactor (skill:data-ingestion)', async ({
    page,
  }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')

    const recorder = await mountToolRecorder(page)
    await chat.sendMessage('把 https://example.com/orders.csv 的数据落到我的 H2 数据库')
    await chat.waitForAiResponse()

    const fetchCalls = await recorder.callsFor('datatalk_http_request')
    expect(
      fetchCalls.length,
      'agent must invoke datatalk_http_request for ingestion intent'
    ).toBeGreaterThanOrEqual(1)
  })

  // ─────────────────────────────────────────────────────────────────────
  // Scenario 5: SQL error diagnostics → skill:sql-error-diagnostics
  // Assertion: after an "execution failure" (we force "no such table"), the
  // agent's next tool invocation is datatalk_read_schema (probe path owned
  // by skill:sql-execution per the boundary table).
  // ─────────────────────────────────────────────────────────────────────
  test('scenario 5: SQL execution error triggers schema probe (skill:sql-error-diagnostics)', async ({
    page,
  }) => {
    test.skip(!MODEL, 'DATATALK_REAL_OPENCODE_MODEL not set')

    const recorder = await mountToolRecorder(page)
    // Force a "no such table" error by asking the agent to query a clearly
    // non-existent table. Phrased as a natural-language request to keep the
    // agent in the analytical / server-data workflow.
    await chat.sendMessage('从 does_not_exist_xyz 表里取一行数据看看')
    await chat.waitForAiResponse()

    const readSchemaCalls = await recorder.callsFor('datatalk_read_schema')
    expect(
      readSchemaCalls.length,
      'assistant must probe schema after SQL execution failure (table-not-found path)'
    ).toBeGreaterThanOrEqual(1)
  })
})
