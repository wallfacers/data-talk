import { isAllowedDirectStageStoreMutationFile } from './stage-store-mutation-allowlist.js'

const TARGETS = new Set(['useStageStore', 'useSqlWorkbenchStore'])

export default {
  meta: {
    type: 'problem',
    docs: { description: 'Forbid direct setState calls on stage stores from outside the store implementation files.' },
    messages: { forbidden: '{{store}}.setState may only be called inside the store implementation file. Add a mutation method instead.' },
    schema: [],
  },
  create(context) {
    if (isAllowedDirectStageStoreMutationFile(context.getFilename())) return {}
    return {
      MemberExpression(node) {
        if (
          node.object && node.object.type === 'Identifier' && TARGETS.has(node.object.name) &&
          node.property && node.property.type === 'Identifier' && node.property.name === 'setState'
        ) {
          context.report({ node, messageId: 'forbidden', data: { store: node.object.name } })
        }
      },
    }
  },
}
