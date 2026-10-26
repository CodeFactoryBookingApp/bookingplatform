import { useQuery } from '@tanstack/react-query'
import { inboxApi } from '@/api/endpoints'
import { EmptyState, ErrorState, PageHeader } from '@/components/ui/Blocks'
import { Skeleton } from '@/components/ui/Spinner'
import { formatDateTime } from '@/lib/format'

export default function InboxPage() {
  const emails = useQuery({ queryKey: ['inbox'], queryFn: inboxApi.emails })

  return (
    <div className="animate-in max-w-3xl">
      <PageHeader title="Bandeja" description="Los correos que la plataforma te envía, como la aprobación de tu negocio." />
      {emails.isPending ? (
        <div className="flex flex-col gap-3" aria-hidden="true">
          {Array.from({ length: 3 }, (_, i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : emails.isError ? (
        <ErrorState error={emails.error} onRetry={() => emails.refetch()} />
      ) : emails.data.length === 0 ? (
        <EmptyState title="Sin correos por ahora" description="Cuando la plataforma te escriba, el mensaje aparecerá aquí." />
      ) : (
        <ul className="flex flex-col gap-3">
          {emails.data.map((mail) => (
            <li key={mail.id}>
              <details className="group rounded-lg border border-line bg-surface open:border-line-strong">
                <summary className="flex min-h-14 cursor-pointer list-none flex-wrap items-center justify-between gap-2 px-4 py-3">
                  <span className="font-medium">{mail.subject}</span>
                  <time dateTime={mail.sentAt} className="text-sm text-muted">
                    {formatDateTime(mail.sentAt)}
                  </time>
                </summary>
                <div className="whitespace-pre-wrap break-words border-t border-line px-4 py-4 text-sm text-muted">{mail.body}</div>
              </details>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
