import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from '@/api/types'
import { PageLoader } from '@/components/ui/Spinner'
import { homeFor, useAuth } from './AuthContext'

/** Needs a signed-in user, optionally of one of `roles`. The backend enforces the same (SecurityConfig). */
export function RequireAuth({ roles }: { roles?: Role[] }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <PageLoader label="Verificando tu sesión…" />
  if (!user) return <Navigate to="/ingresar" replace state={{ from: location.pathname + location.search }} />
  if (roles && (!user.role || !roles.includes(user.role))) return <Navigate to={homeFor(user.role)} replace />
  return <Outlet />
}

/** Sign-in and sign-up screens make no sense once signed in. */
export function PublicOnly() {
  const { user, loading } = useAuth()
  if (loading) return <PageLoader label="Verificando tu sesión…" />
  if (user) return <Navigate to={homeFor(user.role)} replace />
  return <Outlet />
}
