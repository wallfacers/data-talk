export const RECOMMENDED_PROVIDERS: ReadonlySet<string> = new Set([
  'opencode-zen',
  'opencode-go',
  'anthropic',
])

export const PROVIDER_DESCRIPTIONS: Record<string, string> = {
  'opencode-zen': '使用 OpenCode Zen 或 API 密钥连接',
  'opencode-go': '适合所有人的低成本订阅',
  'anthropic': '使用 Claude Pro/Max 或 API 密钥连接',
  'github-copilot': '使用 Copilot 或 API 密钥连接',
  'openai': '使用 ChatGPT Pro/Plus 或 API 密钥连接',
  'google': '使用 Gemini 或 API 密钥连接',
}
