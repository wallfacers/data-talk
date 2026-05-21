export type ApiResponse<T> = {
  data: T
}

export type ApiError = {
  code: string
  message: string
  details?: unknown
}

export type Paginated<T> = {
  items: T[]
  total: number
  page: number
  pageSize: number
}
