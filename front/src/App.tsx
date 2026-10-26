import { lazy, Suspense } from 'react'
import { Link, Navigate, Route, Routes } from 'react-router-dom'
import { RequireAuth, PublicOnly } from '@/auth/guards'
import { Shell } from '@/components/layout/Shell'
import { WakeNotice } from '@/components/layout/WakeNotice'
import { LinkButton } from '@/components/ui/Button'
import { PageLoader } from '@/components/ui/Spinner'

// Routes are split per screen so the catalog does not ship the admin and provider code.
const CatalogPage = lazy(() => import('@/pages/public/CatalogPage'))
const LoginPage = lazy(() => import('@/pages/public/LoginPage'))
const RegisterClientPage = lazy(() => import('@/pages/public/RegisterClientPage'))
const RegisterProviderPage = lazy(() => import('@/pages/public/RegisterProviderPage'))
const VerifyEmailPage = lazy(() => import('@/pages/public/VerifyEmailPage'))
const ForgotPasswordPage = lazy(() => import('@/pages/public/RecoveryPages').then((m) => ({ default: m.ForgotPasswordPage })))
const ResetPasswordPage = lazy(() => import('@/pages/public/RecoveryPages').then((m) => ({ default: m.ResetPasswordPage })))
const InboxPage = lazy(() => import('@/pages/account/InboxPage'))
const OrganizationsPage = lazy(() => import('@/pages/provider/OrganizationsPage'))
const OrganizationLayout = lazy(() => import('@/pages/provider/OrganizationLayout'))
const SummaryPage = lazy(() => import('@/pages/provider/SummaryPage'))
const ResourceTypesPage = lazy(() => import('@/pages/provider/ResourceTypesPage'))
const ResourcesPage = lazy(() => import('@/pages/provider/ResourcesPage'))
const ServicesPage = lazy(() => import('@/pages/provider/ServicesPage'))
const AgendasPage = lazy(() => import('@/pages/provider/AgendasPage'))
const AdminLayout = lazy(() => import('@/pages/admin/AdminLayout'))
const AdminProvidersPage = lazy(() => import('@/pages/admin/AdminProvidersPage'))
const AdminClientsPage = lazy(() => import('@/pages/admin/AdminClientsPage'))

function NotFound() {
  return (
    <div className="flex flex-col items-start gap-4 py-10">
      <p className="text-sm font-semibold text-accent-ink">Error 404</p>
      <h1 className="text-3xl">No encontramos esta página</h1>
      <p className="max-w-prose text-muted">La dirección puede estar mal escrita o la página ya no existe.</p>
      <LinkButton to="/">Volver al catálogo</LinkButton>
      <Link to="/ingresar" className="text-sm text-muted underline underline-offset-4">
        Ir a ingresar
      </Link>
    </div>
  )
}

export default function App() {
  return (
    <>
      <Suspense fallback={<PageLoader />}>
        <Routes>
          <Route element={<Shell />}>
            <Route index element={<CatalogPage />} />

            {/* Links in the emails Supabase sends land here (Site URL), signed in or not */}
            <Route path="verificar-correo" element={<VerifyEmailPage />} />
            <Route path="restablecer-clave" element={<ResetPasswordPage />} />

            <Route element={<PublicOnly />}>
              <Route path="ingresar" element={<LoginPage />} />
              <Route path="registro" element={<RegisterClientPage />} />
              <Route path="registro-negocio" element={<RegisterProviderPage />} />
              <Route path="recuperar" element={<ForgotPasswordPage />} />
            </Route>

            <Route element={<RequireAuth />}>
              <Route path="bandeja" element={<InboxPage />} />
            </Route>

            <Route element={<RequireAuth roles={['PROVIDER', 'ADMIN']} />}>
              <Route path="negocio" element={<OrganizationsPage />} />
              <Route path="negocio/:orgId" element={<OrganizationLayout />}>
                <Route index element={<SummaryPage />} />
                <Route path="tipos" element={<ResourceTypesPage />} />
                <Route path="recursos" element={<ResourcesPage />} />
                <Route path="servicios" element={<ServicesPage />} />
                <Route path="agendas" element={<AgendasPage />} />
              </Route>
            </Route>

            <Route element={<RequireAuth roles={['ADMIN']} />}>
              <Route path="admin" element={<AdminLayout />}>
                <Route index element={<Navigate to="proveedores" replace />} />
                <Route path="proveedores" element={<AdminProvidersPage />} />
                <Route path="clientes" element={<AdminClientsPage />} />
              </Route>
            </Route>

            <Route path="*" element={<NotFound />} />
          </Route>
        </Routes>
      </Suspense>
      <WakeNotice />
    </>
  )
}
