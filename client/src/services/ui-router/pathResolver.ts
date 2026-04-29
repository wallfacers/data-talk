// 判断实际 JSON Pointer 是否匹配 capability 声明的 pattern。
// 支持两种通配：
// 1. [key=<placeholder>] 形式 — 占位 key=value 中的 value，例：
//    实际 "/tables[id=t_42]" 匹配 "/tables[id=<id>]" 或 "/tables[id=<n>]"。
// 2. <placeholder> 形式 — 占位整个路径段，例：
//    实际 "/positions/users" 匹配 "/positions/<table>"。
// `<placeholder>` 中的具体名字不参与比较，只要形如 `<word>` 即可。
export function matchPathPattern(actualPath: string, pattern: string): boolean {
  const a = actualPath.split('/')
  const b = pattern.split('/')
  if (a.length !== b.length) return false
  for (let i = 0; i < a.length; i++) {
    if (a[i] === b[i]) continue

    // Whole-segment placeholder: e.g. "<table>".
    if (/^<\w+>$/.test(b[i]) && a[i].length > 0) continue

    // [key=value] segment with placeholder value: e.g. "tables[id=<id>]".
    const aMatch = a[i].match(/^([^[]+)\[([\w-]+)=([^\]]+)\]$/)
    const bMatch = b[i].match(/^([^[]+)\[([\w-]+)=<\w+>\]$/)
    if (aMatch && bMatch && aMatch[1] === bMatch[1] && aMatch[2] === bMatch[2]) {
      continue
    }
    return false
  }
  return true
}
