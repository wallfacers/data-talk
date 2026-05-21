import http from 'node:http'

const PORT = 3456

const server = http.createServer((req, res) => {
  const url = new URL(req.url ?? '/', `http://localhost:${PORT}`)

  // GET /api/actions — return demo action registry
  if (req.method === 'GET' && url.pathname === '/api/actions') {
    res.writeHead(200, { 'Content-Type': 'application/json', 'Access-Control-Allow-Origin': '*' })
    res.end(JSON.stringify({
      actions: [
        {
          id: 'datatalk.query',
          executor: 'SERVER',
          description: 'Execute a SQL query',
          inputSchema: { sql: 'string' },
          outputSchema: { columns: ['string'], rows: ['object'] },
          produces: ['datatalk.artifact'],
          sideEffects: [],
          requiresConnection: true,
          timeoutMs: 30000,
        },
      ],
    }))
    return
  }

  // POST /api/sessions/:id/channel — return SSE sequence
  if (req.method === 'POST' && url.pathname.startsWith('/api/sessions/')) {
    res.writeHead(200, {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      'Connection': 'keep-alive',
      'Access-Control-Allow-Origin': '*',
    })

    // Scripted SSE sequence
    const events = [
      { id: 1, event: 'connected', data: { sessionId: 'mock-1', serverRev: 1 } },
      { id: 2, event: 'message.created', data: { message: { id: 'm1', role: 'assistant', createdAt: Date.now() } } },
      { id: 3, event: 'message.part.created', data: { part: { type: 'text', id: 'p1', sessionID: 'mock-1', messageID: 'm1', text: 'Here is the users table:\n' } } },
      { id: 4, event: 'message.part.created', data: { part: { type: 'tool', id: 'p2', sessionID: 'mock-1', messageID: 'm1', tool: 'datatalk.query', state: { status: 'completed' } } } },
      { id: 5, event: 'ontology.updated', data: { objectType: 'datatalk.artifact', id: 'art-1', op: 'upsert', patch: { version: 1, kind: 'table', columns: ['id', 'name', 'email', 'created_at'], preview: [{ id: '1', name: 'Alice', email: 'alice@test.com', created_at: '2024-01-01' }, { id: '2', name: 'Bob', email: 'bob@test.com', created_at: '2024-01-02' }] } } },
      { id: 6, event: 'session.status', data: { status: 'idle' } },
    ]

    let i = 0
    const timer = setInterval(() => {
      if (i >= events.length) { clearInterval(timer); res.end(); return }
      const e = events[i++]
      res.write(`id: ${e.id}\nevent: ${e.event}\ndata: ${JSON.stringify(e.data)}\n\n`)
    }, 300)

    req.on('close', () => clearInterval(timer))
    return
  }

  res.writeHead(404, { 'Content-Type': 'text/plain' })
  res.end('Not Found')
})

server.listen(PORT, () => {
  console.log(`MockChannelServer listening on http://localhost:${PORT}`)
})

export { server, PORT }
