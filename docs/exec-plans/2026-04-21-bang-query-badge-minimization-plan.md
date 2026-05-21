# Bang Query Badge Minimization Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 bang-query 用户气泡从显式文字 badge 改成右上角低存在感小图标，减少视觉打扰。

**Architecture:** 仅调整前端渲染层，不改消息数据结构和判定逻辑。`bang_query_user` 仍由 metadata 驱动，只修改 `UserBubble` 的展示方式和对应测试。

**Tech Stack:** React 19 + TypeScript + Vitest + lucide-react。

---

**Execution Notes (2026-04-21):**
- 最终方案使用右上角低存在感小图标，不显示正文文字前缀。
- 定向验证已通过：`session-turn.test.tsx` 通过。
- `npx tsc --noEmit` 被现有无关错误阻塞：`client/src/features/stage/components/query-editor-tab.tsx:109` 的 `useRef<EditorView>()` 缺少初始参数。

## File Structure Map

### Modify

- `client/src/features/chat/components/turn/user-bubble.tsx`
- `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx`
- `client/src/i18n/messages.ts`
- `docs/product-specs/index.md`
- `docs/exec-plans/index.md`

## Task 1: Replace Visible Badge With Subtle Icon

**Files:**
- Modify: `client/src/features/chat/components/turn/__tests__/session-turn.test.tsx`
- Modify: `client/src/features/chat/components/turn/user-bubble.tsx`
- Modify: `client/src/i18n/messages.ts`

- [x] **Step 1.1: Write the failing test**
- [x] **Step 1.2: Run the targeted test and confirm it fails**
- [x] **Step 1.3: Replace the visible `SQL 直查` badge with a small top-right icon**
- [x] **Step 1.4: Keep an accessibility label for the icon**
- [x] **Step 1.5: Re-run the targeted test**

## Task 2: Verification And Housekeeping

- [x] **Step 2.1: Run frontend type-check（被现有无关错误阻塞，见 Execution Notes）**
- [x] **Step 2.2: Mark this plan complete**
- [x] **Step 2.3: Register plan/spec indices**
