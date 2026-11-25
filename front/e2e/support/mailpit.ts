import { MAILPIT_URL } from './env'

export const SIGNUP_SUBJECT = 'Confirm Your Email'
export const RECOVERY_SUBJECT = 'Reset Your Password'

interface MailpitSummary {
  ID: string
  Subject: string
}

const sleep = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms))

async function getJson<T>(path: string): Promise<T> {
  const response = await fetch(`${MAILPIT_URL}${path}`)
  if (!response.ok) throw new Error(`Mailpit ${path} answered ${response.status}`)
  return (await response.json()) as T
}

/** The `token` query parameter of the first link in the message that carries one. */
export function tokenFromBody(text: string): string | null {
  for (const link of text.match(/https?:\/\/[^\s<>")]+/g) ?? []) {
    const token = new URL(link).searchParams.get('token')
    if (token) return token
  }
  return null
}

/**
 * Polls Mailpit until the newest message to `to` with the given subject arrives and returns the
 * `token` of its link: the value the API takes as `tokenHash` and the UI pages read from the URL.
 */
export async function waitForEmailToken(to: string, subject: string, timeoutMs = 15_000): Promise<string> {
  const deadline = Date.now() + timeoutMs
  do {
    // Mailpit lists newest first, so the first match is the latest email of that kind
    const { messages } = await getJson<{ messages: MailpitSummary[] }>(`/api/v1/search?query=${encodeURIComponent(`to:${to}`)}`)
    const match = messages.find((message) => message.Subject === subject)
    if (match) {
      const { Text } = await getJson<{ Text: string }>(`/api/v1/message/${match.ID}`)
      const token = tokenFromBody(Text)
      if (token) return token
    }
    await sleep(500)
  } while (Date.now() < deadline)
  throw new Error(`No "${subject}" email with a token reached ${to} within ${timeoutMs} ms (Mailpit: ${MAILPIT_URL})`)
}
