import { forwardRef, useId, type InputHTMLAttributes, type ReactNode, type SelectHTMLAttributes, type TextareaHTMLAttributes } from 'react'
import clsx from 'clsx'

const control =
  'block w-full min-h-11 rounded-md border bg-surface px-3 py-2 text-ink placeholder:text-faint ' +
  'transition-colors duration-150 hover:border-faint focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/25 ' +
  'disabled:bg-sunken disabled:text-faint'

interface FieldProps {
  label: string
  error?: string
  hint?: string
  required?: boolean
  className?: string
  children: (ids: { id: string; describedBy?: string; invalid: boolean }) => ReactNode
}

/** Label above the control, hint and error below it, wired together for screen readers. */
export function Field({ label, error, hint, required, className, children }: FieldProps) {
  const id = useId()
  const hintId = hint ? `${id}-hint` : undefined
  const errorId = error ? `${id}-error` : undefined
  const describedBy = [hintId, errorId].filter(Boolean).join(' ') || undefined
  return (
    <div className={clsx('flex flex-col gap-1.5', className)}>
      <label htmlFor={id} className="text-sm font-medium text-ink">
        {label}
        {required && <span className="text-danger" aria-hidden="true"> *</span>}
      </label>
      {children({ id, describedBy, invalid: Boolean(error) })}
      {hint && !error && (
        <p id={hintId} className="text-xs text-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} role="alert" className="text-sm text-danger">
          {error}
        </p>
      )}
    </div>
  )
}

type Common = { label: string; error?: string; hint?: string; wrapperClassName?: string }

export const Input = forwardRef<HTMLInputElement, Common & InputHTMLAttributes<HTMLInputElement>>(function Input(
  { label, error, hint, wrapperClassName, className, required, ...rest },
  ref,
) {
  return (
    <Field label={label} error={error} hint={hint} required={required} className={wrapperClassName}>
      {({ id, describedBy, invalid }) => (
        <input
          ref={ref}
          id={id}
          required={required}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          spellCheck={rest.type === 'email' ? false : undefined}
          className={clsx(control, invalid ? 'border-danger' : 'border-line-strong', className)}
          {...rest}
        />
      )}
    </Field>
  )
})

export const Select = forwardRef<HTMLSelectElement, Common & SelectHTMLAttributes<HTMLSelectElement>>(function Select(
  { label, error, hint, wrapperClassName, className, required, children, ...rest },
  ref,
) {
  return (
    <Field label={label} error={error} hint={hint} required={required} className={wrapperClassName}>
      {({ id, describedBy, invalid }) => (
        <select
          ref={ref}
          id={id}
          required={required}
          aria-invalid={invalid || undefined}
          aria-describedby={describedBy}
          className={clsx(control, 'pr-8', invalid ? 'border-danger' : 'border-line-strong', className)}
          {...rest}
        >
          {children}
        </select>
      )}
    </Field>
  )
})

export const Textarea = forwardRef<HTMLTextAreaElement, Common & TextareaHTMLAttributes<HTMLTextAreaElement>>(
  function Textarea({ label, error, hint, wrapperClassName, className, required, ...rest }, ref) {
    return (
      <Field label={label} error={error} hint={hint} required={required} className={wrapperClassName}>
        {({ id, describedBy, invalid }) => (
          <textarea
            ref={ref}
            id={id}
            rows={3}
            required={required}
            aria-invalid={invalid || undefined}
            aria-describedby={describedBy}
            className={clsx(control, 'resize-y', invalid ? 'border-danger' : 'border-line-strong', className)}
            {...rest}
          />
        )}
      </Field>
    )
  },
)
