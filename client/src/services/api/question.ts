import { http } from '@/services/http'

export type QuestionOption = {
  label: string
  description?: string
}

export type QuestionInfo = {
  question: string
  header: string
  options: QuestionOption[]
  multiple?: boolean
  custom?: boolean
}

/** Normalized pending question request (one AI `question` tool invocation). */
export type QuestionRequest = {
  id: string
  questions: QuestionInfo[]
}

/** Raw shape returned by the list endpoint (OpenCode `Request`, sessionID re-stamped to DataTalk id by the backend). */
type RawQuestionRequest = {
  id: string
  sessionID?: string
  questions: QuestionInfo[]
}

export async function listQuestions(sessionId: string): Promise<QuestionRequest[]> {
  const raw = await http.get(`sessions/${sessionId}/questions`).json<RawQuestionRequest[]>()
  return raw.map((q) => ({ id: q.id, questions: q.questions ?? [] }))
}

/** `answers`: one selected-label array per sub-question, in order. */
export function replyQuestion(requestId: string, answers: string[][]): Promise<boolean> {
  return http.post(`questions/${requestId}/reply`, { json: { answers } }).json<boolean>()
}

export function rejectQuestion(requestId: string): Promise<boolean> {
  return http.post(`questions/${requestId}/reject`).json<boolean>()
}
