import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowRight } from '@phosphor-icons/react'
import { organizationApi } from '@/api/endpoints'
import { StatusBadge } from '@/components/ui/Badge'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/Blocks'
import { LinkButton } from '@/components/ui/Button'
import { Skeleton } from '@/components/ui/Spinner'
import { ORGANIZATION_STATUS } from '@/lib/format'

export default function OrganizationsPage() {
  const orgs = useQuery({ queryKey: ['organizations'], queryFn: organizationApi.mine })

  return (
    <div className="animate-in">
      <PageHeader title="Mis negocios" description="Elige un negocio para configurar sus servicios, recursos y horarios." />
      {orgs.isPending ? (
        <div className="grid gap-4 sm:grid-cols-2" aria-hidden="true">
          <Skeleton className="h-28 w-full" />
          <Skeleton className="h-28 w-full" />
        </div>
      ) : orgs.isError ? (
        <ErrorState error={orgs.error} onRetry={() => orgs.refetch()} />
      ) : orgs.data.length === 0 ? (
        <EmptyState
          title="Aún no tienes negocios"
          description="Registra tu negocio para empezar a publicar servicios."
          action={<LinkButton to="/registro-negocio">Registrar negocio</LinkButton>}
        />
      ) : (
        <ul className="grid gap-4 sm:grid-cols-2">
          {orgs.data.map((org) => (
            <li key={org.id}>
              <Link
                to={`/negocio/${org.id}`}
                className="group flex h-full flex-col gap-3 rounded-lg border border-line bg-surface p-5 transition-colors hover:border-accent"
              >
                <div className="flex items-start justify-between gap-3">
                  <h2 className="text-lg">{org.name}</h2>
                  <StatusBadge map={ORGANIZATION_STATUS} value={org.status} />
                </div>
                <p className="text-sm text-muted">
                  {org.locations.length} {org.locations.length === 1 ? 'sede' : 'sedes'}, contacto {org.contactEmail}
                </p>
                <span className="mt-auto inline-flex items-center gap-1.5 text-sm font-medium text-accent-ink">
                  Administrar
                  <ArrowRight size={16} className="transition-transform group-hover:translate-x-0.5" aria-hidden="true" />
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
