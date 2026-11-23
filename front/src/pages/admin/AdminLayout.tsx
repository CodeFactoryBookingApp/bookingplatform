import { Outlet } from 'react-router-dom'
import { TabNav } from '@/components/layout/Shell'

export default function AdminLayout() {
  return (
    <div className="animate-in">
      <h1 className="mb-4 text-2xl sm:text-3xl">Administración</h1>
      <TabNav
        items={[
          { to: '/admin/proveedores', label: 'Proveedores' },
          { to: '/admin/clientes', label: 'Clientes' },
        ]}
      />
      <Outlet />
    </div>
  )
}
