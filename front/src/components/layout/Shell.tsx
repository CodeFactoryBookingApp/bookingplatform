import { useState } from 'react'
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import clsx from 'clsx'
import { Envelope, List, SignOut, X } from '@phosphor-icons/react'
import { toast } from 'sonner'
import { useAuth } from '@/auth/AuthContext'
import { buttonClass } from '@/components/ui/Button'

function Logo() {
  return (
    <Link to="/" className="flex items-center gap-2.5 rounded-md" aria-label="Reservas, ir al inicio">
      <span className="grid size-8 place-items-center rounded-md bg-accent" aria-hidden="true">
        <svg viewBox="0 0 32 32" className="size-5" fill="none" stroke="#fff" strokeWidth="2.6" strokeLinecap="round">
          <path d="M9 12h14M9 16h14M9 20h8" />
        </svg>
      </span>
      <span className="font-display text-lg font-semibold tracking-tight">Reservas</span>
    </Link>
  )
}

const navLink = ({ isActive }: { isActive: boolean }) =>
  clsx(
    'inline-flex min-h-11 items-center rounded-md px-3 text-sm font-medium transition-colors',
    isActive ? 'bg-accent-soft text-accent-ink' : 'text-muted hover:bg-sunken hover:text-ink',
  )

function Header() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()
  const [open, setOpen] = useState(false)

  const handleLogout = async () => {
    setOpen(false)
    await logout()
    toast.success('Cerraste sesión')
    navigate('/')
  }
  const close = () => setOpen(false)

  const links = (
    <>
      <NavLink to="/" end className={navLink} onClick={close}>
        Catálogo
      </NavLink>
      {user?.role === 'PROVIDER' && (
        <NavLink to="/negocio" className={navLink} onClick={close}>
          Mi negocio
        </NavLink>
      )}
      {user?.role === 'ADMIN' && (
        <NavLink to="/admin" className={navLink} onClick={close}>
          Administración
        </NavLink>
      )}
      {user && (
        <NavLink to="/bandeja" className={navLink} onClick={close}>
          <Envelope size={18} className="mr-1.5" aria-hidden="true" />
          Bandeja
        </NavLink>
      )}
      {!user && (
        <NavLink to="/registro-negocio" className={navLink} onClick={close}>
          Para negocios
        </NavLink>
      )}
    </>
  )

  return (
    <header className="sticky top-0 z-30 border-b border-line bg-canvas/95 backdrop-blur-sm">
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6">
        <div className="flex items-center gap-6">
          <Logo />
          <nav aria-label="Principal" className="hidden items-center gap-1 md:flex">
            {links}
          </nav>
        </div>

        <div className="hidden items-center gap-2 md:flex">
          {user ? (
            <>
              <span className="max-w-48 truncate text-sm text-muted" title={user.email}>
                {user.email}
              </span>
              <button type="button" onClick={handleLogout} className={buttonClass('secondary', 'md')}>
                <SignOut size={18} aria-hidden="true" /> Salir
              </button>
            </>
          ) : (
            <>
              <Link to="/ingresar" className={buttonClass('ghost', 'md')}>
                Ingresar
              </Link>
              <Link to="/registro" className={buttonClass('primary', 'md')}>
                Crear cuenta
              </Link>
            </>
          )}
        </div>

        <button
          type="button"
          className="-mr-2 inline-flex size-11 items-center justify-center rounded-md text-ink hover:bg-sunken md:hidden"
          aria-expanded={open}
          aria-controls="mobile-menu"
          aria-label={open ? 'Cerrar menú' : 'Abrir menú'}
          onClick={() => setOpen((v) => !v)}
        >
          {open ? <X size={24} aria-hidden="true" /> : <List size={24} aria-hidden="true" />}
        </button>
      </div>

      {open && (
        <div id="mobile-menu" className="animate-in border-t border-line bg-canvas px-4 pb-4 pt-2 md:hidden">
          <nav aria-label="Principal" className="flex flex-col">
            {links}
          </nav>
          <div className="mt-3 flex flex-col gap-2 border-t border-line pt-3">
            {user ? (
              <>
                <p className="truncate text-sm text-muted">{user.email}</p>
                <button type="button" onClick={handleLogout} className={buttonClass('secondary', 'md')}>
                  <SignOut size={18} aria-hidden="true" /> Salir
                </button>
              </>
            ) : (
              <>
                <Link to="/ingresar" onClick={close} className={buttonClass('secondary', 'md')}>
                  Ingresar
                </Link>
                <Link to="/registro" onClick={close} className={buttonClass('primary', 'md')}>
                  Crear cuenta
                </Link>
              </>
            )}
          </div>
        </div>
      )}
    </header>
  )
}

function Footer() {
  return (
    <footer className="mt-16 border-t border-line">
      <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-3 px-4 py-6 text-sm text-muted sm:px-6">
        <p>© {new Date().getFullYear()} Reservas. Todos los precios en pesos colombianos (COP).</p>
        <Link to="/registro-negocio" className="underline-offset-4 hover:text-ink hover:underline">
          ¿Ofreces servicios? Registra tu negocio
        </Link>
      </div>
    </footer>
  )
}

export function Shell() {
  return (
    <div className="flex min-h-dvh flex-col">
      <a
        href="#contenido"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:rounded-md focus:bg-surface focus:px-4 focus:py-2 focus:shadow-lg"
      >
        Saltar al contenido
      </a>
      <Header />
      <main id="contenido" className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6 sm:py-10">
        <Outlet />
      </main>
      <Footer />
    </div>
  )
}

/** Centered narrow column for the sign-in and sign-up forms. */
export function AuthPage({ title, description, children }: { title: string; description?: string; children: React.ReactNode }) {
  return (
    <div className="animate-in mx-auto w-full max-w-md py-4 sm:py-8">
      <h1 className="text-2xl sm:text-3xl">{title}</h1>
      {description && <p className="mt-2 text-muted">{description}</p>}
      <div className="mt-6 rounded-lg border border-line bg-surface p-5 sm:p-6">{children}</div>
    </div>
  )
}

export function TabNav({ items }: { items: { to: string; label: string; end?: boolean }[] }) {
  return (
    <nav aria-label="Secciones" className="-mx-4 mb-6 overflow-x-auto border-b border-line px-4 sm:mx-0 sm:px-0">
      <ul className="flex min-w-max gap-1">
        {items.map((item) => (
          <li key={item.to}>
            <NavLink
              to={item.to}
              end={item.end}
              className={({ isActive }) =>
                clsx(
                  '-mb-px inline-flex min-h-11 items-center border-b-2 px-3 text-sm font-medium transition-colors',
                  isActive ? 'border-accent text-accent-ink' : 'border-transparent text-muted hover:text-ink',
                )
              }
            >
              {item.label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}
