/** Where the local stack listens; `scripts/e2e/stack.sh env` prints the same variables. */
export const SUPABASE_URL = process.env.SUPABASE_URL ?? 'http://127.0.0.1:54321'
export const MAILPIT_URL = process.env.MAILPIT_URL ?? 'http://127.0.0.1:54324'
export const API_URL = process.env.E2E_API_URL ?? 'http://localhost:8080'

// There is no safe default for the key: fail before any test runs instead of with a 401 halfway through one.
export const SUPABASE_SECRET_KEY = process.env.SUPABASE_SECRET_KEY ?? ''
if (!SUPABASE_SECRET_KEY) {
  throw new Error(
    'SUPABASE_SECRET_KEY is not set. Start the stack with `scripts/e2e/stack.sh up` and export its variables: ' +
      'eval "$(scripts/e2e/stack.sh env)"',
  )
}
