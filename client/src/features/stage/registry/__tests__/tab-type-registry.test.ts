import { describe, it, expect } from 'vitest'
import {
  getTabTypeDescriptor,
  isPersistent,
  getScope,
} from '../tab-type-registry'

describe('tab-type-registry', () => {
  it('query_editor is persistent and workspace-scoped', () => {
    const desc = getTabTypeDescriptor('query_editor')
    expect(desc.persistent).toBe(true)
    expect(desc.scope).toBe('workspace')
    expect(desc.type).toBe('query_editor')
  })

  it('artifact_preview is persistent and session-scoped', () => {
    const desc = getTabTypeDescriptor('artifact_preview')
    expect(desc.persistent).toBe(true)
    expect(desc.scope).toBe('session')
    expect(desc.type).toBe('artifact_preview')
  })

  it('file_preview is ephemeral', () => {
    const desc = getTabTypeDescriptor('file_preview')
    expect(desc.persistent).toBe(false)
  })

  it('extractContent on query_editor returns sqlText', () => {
    const desc = getTabTypeDescriptor('query_editor')
    expect(desc.extractContent({ sqlText: 'SELECT 1' })).toBe('SELECT 1')
  })

  it('extractContent is total — null/undefined/empty returns empty string', () => {
    const queryDesc = getTabTypeDescriptor('query_editor')
    expect(queryDesc.extractContent(null)).toBe('')
    expect(queryDesc.extractContent(undefined)).toBe('')
    expect(queryDesc.extractContent({})).toBe('')
    expect(queryDesc.extractContent({ sqlText: 42 })).toBe('')

    const artifactDesc = getTabTypeDescriptor('artifact_preview')
    expect(artifactDesc.extractContent(null)).toBe('')
    expect(artifactDesc.extractContent({})).toBe('')
  })

  it('unknown type falls back to noop descriptor', () => {
    const desc = getTabTypeDescriptor('totally_unknown')
    expect(desc.type).toBe('unknown')
    expect(desc.persistent).toBe(false)
    expect(desc.extractContent({})).toBe('')
    expect(isPersistent('totally_unknown')).toBe(false)
    expect(getScope('totally_unknown')).toBeUndefined()
  })
})
