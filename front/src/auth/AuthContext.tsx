import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { getSession, saveSession, setUnauthorizedHandler } from '@/api/client'
import { authApi } from '@/api/endpoints'
import type { Me } from '@/api/types'

interface AuthState {
  user: Me | null
  /** True while a stored session is being checked against /auth/me. */
  loading: boolean
  login: (email: string, password: string) => Promise<Me>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [user, setUser] = useState<Me | null>(null)
  const [loading, setLoading] = useState(() => getSession() !== null)
  const expiryTimer = useRef<number>(undefined)

  const clear = useCallback(() => {
    window.clearTimeout(expiryTimer.current)
    saveSession(null)
    setUser(null)
    queryClient.clear()
  }, [queryClient])

  // There is no refresh endpoint (AuthController), so the session ends when the access token does.
  const scheduleExpiry = useCallback(
    (expiresAt: number) => {
      window.clearTimeout(expiryTimer.current)
      const wait = Math.min(expiresAt - Date.now(), 2 ** 31 - 1)
      expiryTimer.current = window.setTimeout(() => {
        clear()
        toast.info('Tu sesión expiró. Inicia sesión de nuevo.')
      }, Math.max(wait, 0))
    },
    [clear],
  )

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clear()
      toast.info('Tu sesión expiró. Inicia sesión de nuevo.')
    })
  }, [clear])

  useEffect(() => {
    const stored = getSession()
    if (!stored) return
    let cancelled = false
    authApi
      .me()
      .then((me) => {
        if (cancelled) return
        setUser(me)
        scheduleExpiry(stored.expiresAt)
      })
      .catch(() => !cancelled && clear())
      .finally(() => !cancelled && setLoading(false))
    return () => {
      cancelled = true
    }
  }, [clear, scheduleExpiry])

  const login = useCallback(
    async (email: string, password: string) => {
      const tokens = await authApi.login(email, password)
      const expiresAt = Date.now() + tokens.expiresIn * 1000
      saveSession({ accessToken: tokens.accessToken, expiresAt })
      try {
        const me = await authApi.me()
        queryClient.clear()
        setUser(me)
        scheduleExpiry(expiresAt)
        return me
      } catch (error) {
        saveSession(null)
        throw error
      }
    },
    [queryClient, scheduleExpiry],
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout()
    } catch {
      /* the local session ends either way */
    }
    clear()
  }, [clear])

  const value = useMemo(() => ({ user, loading, login, logout }), [user, loading, login, logout])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

// eslint-disable-next-line react-refresh/only-export-components
export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside AuthProvider')
  return context
}

/** Where each role lands after signing in. */
export function homeFor(role: Me['role']): string {
  if (role === 'ADMIN') return '/admin/proveedores'
  if (role === 'PROVIDER') return '/negocio'
  return '/'
}
