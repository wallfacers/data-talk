import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { SunIcon, MoonIcon, MonitorIcon } from 'lucide-react'
import { toast } from 'sonner'
import { cn } from '@/lib/utils'
import { Button } from '@/components/ui/button'
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { Switch } from '@/components/ui/switch'
import { useUISettingsStore } from '@/stores/ui-settings-store'
import { useChatPartsStore } from '@/stores/chat-parts-store'
import { useOntologyStore } from '@/stores/ontology-store'
import { useTimelineStore } from '@/stores/timeline-store'
import { useStageStore } from '@/stores/stage-store'
import { useSessionStore } from '@/stores/session-store'
import { useChannelStore } from '@/stores/channel-store'
import { useOpenBlankSession } from '@/features/session/hooks/use-open-blank-session'
import { clearAllSessions } from '@/services/api/session'
import { useI18n } from '@/i18n/use-i18n'
import { TimezoneSelector } from './timezone-selector'
import { DateFormatSelector } from './date-format-selector'
import { fetchPreferences, updatePreferences, type UserPreferencesDto } from './preferences-api'
import type { LanguageOption } from '@/i18n/messages'
import type { Theme } from '@/stores/theme-store'

const preferencesQueryKey = ['preferences'] as const

interface GeneralPanelProps {
  theme?: Theme
  language?: LanguageOption
  onThemeChange?: (theme: Theme) => void
  onLanguageChange?: (language: LanguageOption) => void
}

function clearAllLocalSessionResources() {
  useChatPartsStore.setState((state) => ({
    partsBySession: new Map(),
    infoBySession: new Map(),
    partIndexBySession: new Map(),
    streamingBySession: new Set(),
    pendingDeltasBySession: new Map(),
    version: state.version + 1,
  }))
  useOntologyStore.setState({ artifactsBySession: new Map() })
  useTimelineStore.setState({
    orderBySession: new Map(),
    activeBySession: new Map(),
    manualBySession: new Map(),
  })
  useStageStore.getState().resetSessionResources()
  useSessionStore.setState({
    activeSessionId: null,
    modeBySession: new Map(),
    hasEverSentBySession: new Map(),
    dataContextBySession: new Map(),
    pendingPrompt: null,
    composerRestoreDraft: null,
    pendingModelPrompt: false,
    pendingConnectionPrompt: false,
    pendingActionAfterConnectionPick: null,
  })
  useChannelStore.setState({ lastEventIdBySession: new Map() })
}

export function GeneralSettingsPanel({
  theme = 'system',
  language = 'zh-CN',
  onThemeChange,
  onLanguageChange,
}: GeneralPanelProps) {
  const { t } = useI18n()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const autoExpandReasoning = useUISettingsStore((s) => s.autoExpandReasoning)
  const setAutoExpandReasoning = useUISettingsStore((s) => s.setAutoExpandReasoning)
  const splitResizable = useUISettingsStore((s) => s.splitResizable)
  const setSplitResizable = useUISettingsStore((s) => s.setSplitResizable)
  const queryClient = useQueryClient()
  const openBlankSession = useOpenBlankSession()
  const themeOptions: { value: Theme; label: string; icon: typeof SunIcon }[] = [
    { value: 'light', label: t('general.theme.light'), icon: SunIcon },
    { value: 'dark', label: t('general.theme.dark'), icon: MoonIcon },
    { value: 'system', label: t('general.theme.system'), icon: MonitorIcon },
  ]
  const languageOptions: { value: LanguageOption; label: string }[] = [
    { value: 'zh-CN', label: t('general.language.zh-CN') },
    { value: 'en-US', label: t('general.language.en-US') },
  ]
  const { data: prefs } = useQuery({
    queryKey: preferencesQueryKey,
    queryFn: fetchPreferences,
    staleTime: 5 * 60 * 1000,
  })
  const prefsMutation = useMutation({
    mutationFn: (patch: Partial<UserPreferencesDto>) => updatePreferences(patch),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: preferencesQueryKey })
    },
    onError: () => {
      toast.error(t('general.sessions.clearAllError'))
    },
  })
  const timezone = prefs?.timezone ?? 'UTC'
  const dateFormat = prefs?.dateFormat ?? 'yyyy-MM-dd HH:mm:ss'

  const clearAllMutation = useMutation({
    mutationFn: clearAllSessions,
    onSuccess: async () => {
      clearAllLocalSessionResources()
      queryClient.removeQueries({ queryKey: ['sessions'] })
      queryClient.removeQueries({ queryKey: ['session-history'] })
      await openBlankSession()
      setConfirmOpen(false)
      toast.success(t('general.sessions.clearAllSuccess'))
    },
    onError: () => {
      toast.error(t('general.sessions.clearAllError'))
    },
  })

  return (
    <div className="space-y-8">
      {/* Theme selection */}
      <div>
        <label className="text-sm font-medium text-foreground mb-3 block">{t('general.theme')}</label>
        <div className="grid grid-cols-3 gap-3">
          {themeOptions.map((option) => {
            const Icon = option.icon
            const active = theme === option.value
            return (
              <button
                key={option.value}
                type="button"
                onClick={() => onThemeChange?.(option.value)}
                className={cn(
                  'flex flex-col items-center justify-center gap-2 rounded-lg border p-4 text-sm transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-interaction-focusRing',
                  active
                    ? 'border-primary bg-primary/5 text-foreground'
                    : 'border-input bg-background text-muted-foreground hover:text-foreground hover:border-foreground/30'
                )}
              >
                <Icon className="size-5" />
                <span>{option.label}</span>
              </button>
            )
          })}
        </div>
      </div>

      {/* Language selection */}
      <div>
        <label className="text-sm font-medium text-foreground mb-3 block">{t('general.language')}</label>
        <Select value={language} onValueChange={(v) => onLanguageChange?.(v as LanguageOption)}>
          <SelectTrigger className="w-40">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            {languageOptions.map((option) => (
              <SelectItem key={option.value} value={option.value}>
                {option.label}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      {/* Timezone selection */}
      <div>
        <label className="text-sm font-medium text-foreground mb-3 block">{t('general.timezone')}</label>
        <p className="text-xs text-muted-foreground mb-2">{t('general.timezoneDesc')}</p>
        <TimezoneSelector
          value={timezone}
          onChange={(v) => prefsMutation.mutate({ timezone: v })}
        />
      </div>

      {/* Date format selection */}
      <div>
        <label className="text-sm font-medium text-foreground mb-3 block">{t('general.dateFormat')}</label>
        <DateFormatSelector
          value={dateFormat}
          onChange={(v) => prefsMutation.mutate({ dateFormat: v })}
        />
      </div>

      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-foreground">{t('general.autoExpandReasoning')}</p>
          <p className="mt-0.5 text-xs text-muted-foreground">{t('general.autoExpandReasoningDesc')}</p>
        </div>
        <Switch
          checked={autoExpandReasoning}
          onCheckedChange={setAutoExpandReasoning}
          aria-label={t('general.autoExpandReasoning')}
        />
      </div>

      {/* Split view resizable */}
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm font-medium text-foreground">{t('general.splitResizable')}</p>
          <p className="text-xs text-muted-foreground mt-0.5">{t('general.splitResizableDesc')}</p>
        </div>
        <Switch
          checked={splitResizable}
          onCheckedChange={setSplitResizable}
          aria-label={t('general.splitResizable')}
        />
      </div>

      {/* Session management */}
      <div>
        <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-sm font-medium text-foreground">{t('general.sessions.title')}</p>
            <p className="mt-0.5 text-xs text-muted-foreground">{t('general.sessions.desc')}</p>
          </div>
          <Button
            variant="destructive"
            onClick={() => setConfirmOpen(true)}
            disabled={clearAllMutation.isPending}
          >
            {t('general.sessions.clearAllAction')}
          </Button>
        </div>
      </div>

      <AlertDialog
        open={confirmOpen}
        onOpenChange={(open) => {
          if (!clearAllMutation.isPending) setConfirmOpen(open)
        }}
      >
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('general.sessions.confirmTitle')}</AlertDialogTitle>
            <AlertDialogDescription>{t('general.sessions.confirmDesc')}</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>
              {t('common.cancel')}
            </AlertDialogCancel>
            <AlertDialogAction
              variant="destructive"
              disabled={clearAllMutation.isPending}
              onClick={(event) => {
                event.preventDefault()
                clearAllMutation.mutate()
              }}
            >
              {clearAllMutation.isPending ? t('general.sessions.clearing') : t('general.sessions.confirmAction')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
