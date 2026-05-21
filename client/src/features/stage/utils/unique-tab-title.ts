export function resolveUniqueTabTitle(baseTitle: string, existingTitles: Iterable<string>): string {
  const titles = new Set(existingTitles)
  if (!titles.has(baseTitle)) return baseTitle

  let index = 2
  while (titles.has(`${baseTitle}${index}`)) {
    index += 1
  }

  return `${baseTitle}${index}`
}
