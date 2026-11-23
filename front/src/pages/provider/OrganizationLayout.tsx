import { createContext, useContext } from 'react'
import { Link, Outlet, useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { CaretLeft } from '@phosphor-icons/react'
import { organizationApi } from '@/api/endpoints'
import type { Organization } from '@/api/types'
import { TabNav } from '@/components/layout/Shell'
import { StatusBadge } from '@/components/ui/Badge'
import { Alert, ErrorState } from '@/components/ui/Blocks'
import { PageLoader } from '@/components/ui/Spinner'
import { ORGANIZATION_STATUS } from '@/lib/format'
import { ApiError } from '@/api/client'

const OrganizationContext = createContext<Organization | null>(null)

export function useOrganization(): Organization {
  const org = useContext(OrganizationContext)
  if (!org) throw new Error('useOrganization must be used inside OrganizationLayout')
  return org
}

export default function OrganizationLayout() {
  const { orgId = '' } = useParams()
  const org = useQuery({ queryKey: ['organization', orgId], queryFn: () => organizationApi.one(orgId) })

  if (org.isPending) return <PageLoader label="Cargando tu negocio…" />
  if (org.isError) {
    // Outside the provider every path answers 404 (ADR-0008), so it reads as "not yours or not there".
    const missing = org.error instanceof ApiError && org.error.status === 404
    return missing ? (
      <Alert tone="warn" title="No encontramos ese negocio">
        <Link to="/negocio" className="underline underline-offset-4">
          Volver a mis negocios
        </Link>
      </Alert>
    ) : (
      <ErrorState error={org.error} onRetry={() => org.refetch()} />
    )
  }

  const data = org.data
  const base = `/negocio/${data.id}`
  return (
    <OrganizationContext.Provider value={data}>
      <div className="animate-in">
        <Link to="/negocio" className="mb-3 inline-flex items-center gap-1 text-sm text-muted hover:text-ink">
          <CaretLeft size={16} aria-hidden="true" /> Mis negocios
        </Link>
        <div className="mb-5 flex flex-wrap items-center gap-3">
          <h1 className="text-2xl sm:text-3xl">{data.name}</h1>
          <StatusBadge map={ORGANIZATION_STATUS} value={data.status} />
        </div>
        {data.status === 'PENDING_APPROVAL' && (
          <div className="mb-5">
            <Alert tone="warn" title="Tu negocio está en revisión">
              Puedes ir configurando todo. Los clientes lo verán cuando un administrador lo apruebe.
            </Alert>
          </div>
        )}
        {(data.status === 'SUSPENDED' || data.status === 'REJECTED') && (
          <div className="mb-5">
            <Alert tone="danger" title={data.status === 'SUSPENDED' ? 'Tu negocio está suspendido' : 'Tu solicitud fue rechazada'}>
              Revisa tu bandeja de correo o contacta al soporte de la plataforma.
            </Alert>
          </div>
        )}
        <TabNav
          items={[
            { to: base, label: 'Resumen', end: true },
            { to: `${base}/tipos`, label: 'Tipos de recurso' },
            { to: `${base}/recursos`, label: 'Recursos' },
            { to: `${base}/servicios`, label: 'Servicios' },
            { to: `${base}/agendas`, label: 'Agendas' },
          ]}
        />
        <Outlet />
      </div>
    </OrganizationContext.Provider>
  )
}
