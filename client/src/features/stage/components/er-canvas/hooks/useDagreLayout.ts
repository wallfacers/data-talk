import { useCallback, useEffect, useRef } from 'react'
import type { LayoutInput, LayoutPositions } from '../workers/dagre-layout.worker'

export function useDagreLayout() {
  const workerRef = useRef<Worker | null>(null)

  useEffect(() => {
    workerRef.current = new Worker(
      new URL('../workers/dagre-layout.worker.ts', import.meta.url),
      { type: 'module' },
    )
    return () => {
      workerRef.current?.terminate()
      workerRef.current = null
    }
  }, [])

  const layout = useCallback(async (input: LayoutInput): Promise<LayoutPositions> => {
    const worker = workerRef.current
    if (!worker) {
      const { computeDagreLayout } = await import('../workers/dagre-layout.worker')
      return computeDagreLayout(input)
    }

    return new Promise((resolve, reject) => {
      const onMessage = (event: MessageEvent<{ positions: LayoutPositions; durationMs: number }>) => {
        worker.removeEventListener('message', onMessage)
        worker.removeEventListener('error', onError)
        resolve(event.data.positions)
      }
      const onError = (event: ErrorEvent) => {
        worker.removeEventListener('message', onMessage)
        worker.removeEventListener('error', onError)
        reject(event.error ?? new Error(event.message))
      }

      worker.addEventListener('message', onMessage)
      worker.addEventListener('error', onError)
      worker.postMessage(input)
    })
  }, [])

  return { layout }
}
