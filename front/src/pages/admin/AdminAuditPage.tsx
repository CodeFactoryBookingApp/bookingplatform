import { adminApi } from '@/api/endpoints'
import { AuditView } from '@/components/audit/AuditView'

export default function AdminAuditPage() {
  return <AuditView scope="admin" showOrganization load={adminApi.audit} exportCsv={adminApi.exportAudit} />
}
