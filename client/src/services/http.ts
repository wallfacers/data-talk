import ky from 'ky'

export const http = ky.create({
  prefixUrl: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: 30_000,
  retry: { limit: 1 },
  hooks: {
    beforeError: [
      async (error) => {
        try {
          const body = (await error.response.clone().json()) as { message?: string }
          if (body?.message) error.message = body.message
        } catch {
          // Non-JSON responses have no error message to extract
        }
        return error
      },
    ],
  },
})
