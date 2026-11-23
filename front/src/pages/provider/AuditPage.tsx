import { organizationApi } from '@/api/endpoints'
import { AuditView } from '@/components/audit/AuditView'
import { useOrganization } from './OrganizationLayout'

export default function AuditPage() {
  const org = useOrganization()
  return (
    <AuditView
      scope={`org-${org.id}`}
      load={(query) => organizationApi.audit(org.id, query)}
      exportCsv={(query) => organizationApi.exportAudit(org.id, query)}
    />
  )
}
