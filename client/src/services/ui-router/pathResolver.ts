// 判断实际 JSON Pointer 是否匹配 capability 声明的 pattern。
// pattern 使用 <n> 作为 [key=value] 的占位通配。
// 例：实际 "/tables[id=5]/dataType" 匹配 "/tables[id=<n>]/dataType"
export function matchPathPattern(actualPath: string, pattern: string): boolean {
  const a = actualPath.split('/')
  const b = pattern.split('/')
  if (a.length !== b.length) return false
  for (let i = 0; i < a.length; i++) {
    if (a[i] === b[i]) continue
    // 规范化 [key=value] 段：pattern 里的 <n> 通配具体值
    const aMatch = a[i].match(/^([^\[]+)\[(\w+)=([^\]]+)\]$/)
    const bMatch = b[i].match(/^([^\[]+)\[(\w+)=([^\]]+)\]$/)
    if (aMatch && bMatch && aMatch[1] === bMatch[1] && aMatch[2] === bMatch[2] && bMatch[3] === '<n>') {
      continue
    }
    return false
  }
  return true
}
