#!/usr/bin/env node

/**
 * Wrapper for `pnpm tauri dev` that assigns a random port to the Vite dev server.
 *
 * Flow:
 *   1. Find a free TCP port on 127.0.0.1
 *   2. Set VITE_PORT + TAURI_DEV_SERVER_URL env vars
 *   3. Exec `pnpm tauri dev`
 *
 * Tauri CLI reads TAURI_DEV_SERVER_URL to open the webview at the correct URL.
 * Vite reads VITE_PORT (via vite.config.ts) to bind the same port.
 */

import { createServer } from 'node:net'
import { execSync } from 'node:child_process'

const server = createServer()
server.listen(0, '127.0.0.1', () => {
  const port = server.address().port
  server.close()

  console.log(`[tauri-dev] assigning frontend port ${port}`)
  try {
    execSync('pnpm tauri dev', {
      stdio: 'inherit',
      env: {
        ...process.env,
        VITE_PORT: String(port),
        TAURI_DEV_SERVER_URL: `http://localhost:${port}`,
      },
    })
  } catch {
    process.exit(1)
  }
})
