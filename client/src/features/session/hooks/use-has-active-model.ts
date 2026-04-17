import { useQuery } from '@tanstack/react-query'
import { aiQueryKeys, getCurrentModel } from '@/features/settings/shared/api'

/**
 * 返回是否有已启用且当前选中的模型。
 * 门控逻辑：没有配置模型时不允许创建会话或发送消息。
 */
export function useHasActiveModel(): boolean {
  const { data } = useQuery({
    queryKey: aiQueryKeys.currentModel,
    queryFn: getCurrentModel,
    staleTime: Infinity,
  })
  return !!data?.modelId
}
