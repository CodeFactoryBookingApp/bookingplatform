import { forwardRef, type ButtonHTMLAttributes } from 'react'
import { Link, type LinkProps } from 'react-router-dom'
import clsx from 'clsx'
import { Spinner } from './Spinner'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
type Size = 'sm' | 'md' | 'lg'

const base =
  'inline-flex items-center justify-center gap-2 rounded-md font-semibold whitespace-nowrap transition-colors duration-150 ' +
  'disabled:opacity-50 disabled:pointer-events-none select-none'

const variants: Record<Variant, string> = {
  primary: 'bg-accent text-white hover:bg-accent-hover',
  secondary: 'bg-surface text-ink border border-line-strong hover:bg-sunken',
  ghost: 'text-muted hover:bg-sunken hover:text-ink',
  danger: 'bg-danger text-white hover:bg-[#8f1c13]',
}
// min-h-11 = 44px touch target on md and lg
const sizes: Record<Size, string> = {
  sm: 'min-h-9 px-3 text-sm',
  md: 'min-h-11 px-4 text-sm',
  lg: 'min-h-12 px-6 text-base',
}

export const buttonClass = (variant: Variant = 'primary', size: Size = 'md', className?: string) =>
  clsx(base, variants[variant], sizes[size], className)

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant
  size?: Size
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant, size, loading, disabled, className, children, type = 'button', ...rest },
  ref,
) {
  return (
    <button
      ref={ref}
      type={type}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={buttonClass(variant, size, className)}
      {...rest}
    >
      {loading && <Spinner className="size-4" />}
      {children}
    </button>
  )
})

export function LinkButton({
  variant,
  size,
  className,
  ...rest
}: LinkProps & { variant?: Variant; size?: Size }) {
  return <Link className={buttonClass(variant, size, className)} {...rest} />
}
