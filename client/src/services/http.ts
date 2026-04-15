import ky from 'ky'

export const http = ky.create({
  prefixUrl: import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api',
  timeout: 30_000,
  retry: { limit: 1 },
  hooks: {
    beforeError: [
      async (error) => {
        try {
          const body = (await error.response.clone().json()) as { message?: string }
          if (body?.message) error.message = body.message
        } catch {
          // response 不是 JSON，忽略
        }
        return error
      },
    ],
  },
})
