---
name: BUG-001-dashboard-kind-missing-from-files-library
description: Files library tab crashes when a file artifact has kind='dashboard'
type: project
status: open
createdAt: 2026-05-09
---

## BUG-001: Dashboard kind missing from KIND_ORDER in Files Library Tab

**Status:** open
**Module:** client - stage/files-library-tab
**Severity:** medium (crash when dashboard file exists in library)
**Date:** 2026-05-09

## Description

`KIND_ORDER` in `files-library-tab.tsx` is defined as:
```ts
const KIND_ORDER: FileArtifactKind[] = ['er_diagram', 'report', 'sql_script', 'dataset', 'other']
```

It does not include `'dashboard'`, even though `FileArtifactKind` type includes it and `KIND_ICON` has a dashboard icon.

When a file artifact with `kind: 'dashboard'` and `status: 'archived'` is rendered in the Files Library tab, the component crashes with:
```
TypeError: Cannot read properties of undefined (reading 'push')
```

This happens because `result[file.kind]` is `undefined` for `kind === 'dashboard'`.

## Reproduction

1. Have a file artifact with `kind: 'dashboard'` and `status: 'archived'` in the store
2. Open the Files Library tab
3. Component crashes on render

## Evidence

- Test file: `client/src/features/stage/components/__tests__/files-library-tab.test.tsx`
- Component: `client/src/features/stage/components/files-library-tab.tsx` line 66

## Fix

Add `'dashboard'` to `KIND_ORDER` array in `files-library-tab.tsx`.
