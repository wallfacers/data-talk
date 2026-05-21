import type { JsonPatchOp } from './types'

// 最小 JSON Patch (RFC 6902) 子集：支持 add / remove / replace，带 [name=X] 寻址扩展。
// 返回新对象；不 mutate 入参。
export function applyPatch<T extends Record<string, unknown>>(state: T, ops: JsonPatchOp[]): T {
  let current = clone(state) as unknown
  for (const op of ops) {
    current = applyOne(current, op)
  }
  return current as T
}

function applyOne(root: unknown, op: JsonPatchOp): unknown {
  const segments = parsePath(op.path)
  return setAt(root, segments, op)
}

type Segment = { kind: 'key'; value: string } | { kind: 'index'; value: number } | { kind: 'tail' } | { kind: 'match'; key: string; value: string }

function parsePath(path: string): Segment[] {
  if (!path.startsWith('/')) throw new Error(`invalid json pointer: ${path}`)
  return path.slice(1).split('/').map((raw) => {
    if (raw === '-') return { kind: 'tail' } as Segment
    const m = raw.match(/^([^\[]+)\[(\w+)=([^\]]+)\]$/)
    if (m) {
      // e.g. "columns[name=email]" —— 先进 "columns"，再按 match 寻址
      // 这里 parsePath 只解析一段；调用点按顺序走两步
      // 简化：返回一个复合 segment，由 setAt 处理
      return { kind: 'match', key: m[2], value: m[3] } as Segment
    }
    if (/^\d+$/.test(raw)) return { kind: 'index', value: Number(raw) } as Segment
    return { kind: 'key', value: unescapePointer(raw) } as Segment
  })
}

function unescapePointer(s: string): string {
  return s.replace(/~1/g, '/').replace(/~0/g, '~')
}

function clone<T>(v: T): T {
  if (v === null || typeof v !== 'object') return v
  if (Array.isArray(v)) return v.map(clone) as unknown as T
  const out: Record<string, unknown> = {}
  for (const [k, val] of Object.entries(v as Record<string, unknown>)) out[k] = clone(val)
  return out as T
}

// 重新实现：支持 [name=X] 复合段
function setAt(root: unknown, _unused: Segment[], op: JsonPatchOp): unknown {
  const parts = op.path.slice(1).split('/')
  return walk(root, parts, 0, op)
}

function walk(node: unknown, parts: string[], i: number, op: JsonPatchOp): unknown {
  if (i === parts.length) {
    // 不应该到达（最后一段总由父节点处理）
    throw new Error('empty path')
  }
  const raw = parts[i]
  const isLast = i === parts.length - 1

  // [name=X] 复合段
  const matchKey = raw.match(/^([^\[]+)\[(\w+)=([^\]]+)\]$/)
  if (matchKey) {
    const [, arrKey, addrKey, addrValue] = matchKey
    const arr = (node as Record<string, unknown>)[arrKey]
    if (!Array.isArray(arr)) throw new Error(`${arrKey} is not an array`)
    const idx = arr.findIndex((el) => (el as Record<string, unknown>)[addrKey] === addrValue)
    if (idx < 0) {
      if (op.op === 'add' && isLast) {
        const nextArr = [...arr, { [addrKey]: addrValue, ...((op.value as object) ?? {}) }]
        return { ...(node as object), [arrKey]: nextArr }
      }
      throw new Error(`element ${addrKey}=${addrValue} not found in ${arrKey}`)
    }
    if (isLast) {
      if (op.op === 'remove') {
        const next = [...arr]; next.splice(idx, 1)
        return { ...(node as object), [arrKey]: next }
      }
      if (op.op === 'replace') {
        const next = [...arr]; next[idx] = op.value
        return { ...(node as object), [arrKey]: next }
      }
      // add 在命中时降级为 replace
      const next = [...arr]; next[idx] = { ...(arr[idx] as object), ...(op.value as object) }
      return { ...(node as object), [arrKey]: next }
    }
    const updated = walk(arr[idx], parts, i + 1, op)
    const next = [...arr]; next[idx] = updated
    return { ...(node as object), [arrKey]: next }
  }

  // tail: /arr/-
  if (raw === '-') {
    if (!Array.isArray(node)) throw new Error('tail on non-array')
    if (!isLast || op.op !== 'add') throw new Error('only add supports tail')
    return [...node, op.value]
  }

  // 数字索引
  if (/^\d+$/.test(raw)) {
    const idx = Number(raw)
    if (!Array.isArray(node)) throw new Error('index on non-array')
    if (isLast) {
      const next = [...node]
      if (op.op === 'remove') next.splice(idx, 1)
      else if (op.op === 'replace') next[idx] = op.value
      else next.splice(idx, 0, op.value) // add
      return next
    }
    const updated = walk(node[idx], parts, i + 1, op)
    const next = [...node]; next[idx] = updated
    return next
  }

  // 普通 key
  const key = unescapePointer(raw)
  const rec = node as Record<string, unknown>
  if (isLast) {
    const next = { ...rec }
    if (op.op === 'remove') delete next[key]
    else next[key] = op.value
    return next
  }
  const updated = walk(rec[key], parts, i + 1, op)
  return { ...rec, [key]: updated }
}
