import { writeFileSync, mkdirSync } from 'fs'
import { join, dirname } from 'path'

export type PreflightCheck = {
  name: string
  tag: '@preflight-api' | '@preflight-e2e'
  status: 'pass' | 'fail' | 'skip'
  detail: string
}

const checks: PreflightCheck[] = []

export function recordCheck(check: PreflightCheck): void {
  checks.push(check)
}

export function getChecks(): ReadonlyArray<PreflightCheck> {
  return checks
}

/**
 * Write a Markdown preflight report to `../tmp/playwright/<run-id>/preflight.md`
 * relative to the `client/` working directory.
 */
export function writePreflightReport(runId: string): string {
  const outDir = join(process.cwd(), '..', 'tmp', 'playwright', runId)
  mkdirSync(outDir, { recursive: true })

  const filePath = join(outDir, 'preflight.md')

  const total = checks.length
  const passed = checks.filter((c) => c.status === 'pass').length
  const failed = checks.filter((c) => c.status === 'fail').length
  const skipped = checks.filter((c) => c.status === 'skip').length

  let md = `# Preflight Report\n\n`
  md += `**Run ID:** \`${runId}\`\n`
  md += `**Timestamp:** ${new Date().toISOString()}\n\n`
  md += `## Summary\n\n`
  md += `| Status | Count |\n`
  md += `|--------|-------|\n`
  md += `| Pass   | ${passed} |\n`
  md += `| Fail   | ${failed} |\n`
  md += `| Skip   | ${skipped} |\n`
  md += `| **Total** | **${total}** |\n\n`

  md += `## Checks\n\n`
  for (const c of checks) {
    const icon = c.status === 'pass' ? ':white_check_mark:' : c.status === 'fail' ? ':x:' : ':fast_forward:'
    md += `- ${icon} **${c.name}** \`${c.tag}\` — **${c.status.toUpperCase()}**\n`
    if (c.detail) {
      md += `  > ${c.detail}\n`
    }
  }

  writeFileSync(filePath, md, 'utf-8')
  return filePath
}
