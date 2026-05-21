import { describe, expect, it } from 'vitest'
import { getClientHandler } from '../registry'
import '../client-handlers'

describe('client handler registration', () => {
  it.each([
    'datatalk.pin_artifact',
    'datatalk.ui.read',
    'datatalk.ui.patch',
    'datatalk.ui.exec',
  ])('registers %s', (actionId) => {
    expect(getClientHandler(actionId)).toBeTypeOf('function')
  })
})
