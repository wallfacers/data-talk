## 1. Core Context Model

- [x] 1.1 Extend `ErrorContext` interface in `error-to-ai-context.ts` with `tabTitle`, `availableDatabases`, `availableSchemas` fields
- [x] 1.2 Update `buildErrorMarkdown` to render message source block, available database/schema lists, and actionable guidance
- [x] 1.3 Add i18n messages in `messages.ts` for new markdown labels (zh-CN + en-US)

## 2. Props Threading

- [x] 2.1 Update `SqlErrorResultPanel` props and pass new fields into `ErrorContext`
- [x] 2.2 Update `SqlResultPanel` props to accept `tabTitle` and `availableTargets`, pass through to `SqlErrorResultPanel`
- [x] 2.3 Update `SqlWorkbenchTab` to pass `tab.title` and `connectionTargetsByConnectionId[effectiveContext.connectionId]` to `SqlResultPanel`

## 3. Verification

- [x] 3.1 Run `cd client && npx tsc --noEmit` to confirm zero type errors
- [x] 3.2 Manual smoke test: open a SQL editor tab, execute SQL that fails, click "问 AI", verify markdown includes tab source, available options, and guidance
