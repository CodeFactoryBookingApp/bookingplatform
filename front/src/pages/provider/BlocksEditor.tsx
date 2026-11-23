import { Plus, Trash } from '@phosphor-icons/react'
import type { Block, DayOfWeek } from '@/api/types'
import { Button } from '@/components/ui/Button'
import { DAYS } from '@/lib/format'

/** The API answers "08:00:00"; time inputs and the request use "08:00". */
export const hhmm = (time: string) => time.slice(0, 5)
export const normalizeBlocks = (blocks: Block[]): Block[] =>
  blocks.map((b) => ({ ...b, startTime: hhmm(b.startTime), endTime: hhmm(b.endTime) }))

/** First problem found in a weekly schedule, in plain words, or null when it is valid. */
export function blocksProblem(blocks: Block[]): string | null {
  if (blocks.length === 0) return 'Agrega al menos un horario.'
  for (const day of DAYS) {
    const ofDay = blocks.filter((b) => b.dayOfWeek === day.value).sort((a, b) => a.startTime.localeCompare(b.startTime))
    for (let i = 0; i < ofDay.length; i++) {
      const block = ofDay[i]
      if (!block.startTime || !block.endTime) return `${day.label}: completa la hora de inicio y de fin.`
      if (block.endTime <= block.startTime) return `${day.label}: la hora de fin debe ser posterior a la de inicio.`
      if (i > 0 && block.startTime < ofDay[i - 1].endTime) return `${day.label}: hay horarios que se cruzan.`
    }
  }
  return null
}

const timeClass =
  'min-h-11 rounded-md border border-line-strong bg-surface px-2 text-ink focus:border-accent focus:outline-none focus:ring-2 focus:ring-accent/25'

interface Props {
  value: Block[]
  onChange: (blocks: Block[]) => void
}

/** One row per day of the week with its opening ranges. */
export function BlocksEditor({ value, onChange }: Props) {
  const add = (day: DayOfWeek) => onChange([...value, { dayOfWeek: day, startTime: '08:00', endTime: '12:00' }])
  const patch = (target: Block, changes: Partial<Block>) => onChange(value.map((b) => (b === target ? { ...b, ...changes } : b)))
  const remove = (target: Block) => onChange(value.filter((b) => b !== target))
  const copyDay = (from: DayOfWeek, to: DayOfWeek[]) => {
    const source = value.filter((b) => b.dayOfWeek === from)
    onChange([...value.filter((b) => !to.includes(b.dayOfWeek)), ...to.flatMap((day) => source.map((b) => ({ ...b, dayOfWeek: day })))])
  }

  return (
    <div className="flex flex-col divide-y divide-line rounded-md border border-line">
      {DAYS.map((day) => {
        const ofDay = value.filter((b) => b.dayOfWeek === day.value)
        return (
          <div key={day.value} className="flex flex-col gap-2 p-3 sm:flex-row sm:items-start sm:gap-4">
            <p className="w-24 shrink-0 pt-2.5 text-sm font-medium">{day.label}</p>
            <div className="flex flex-1 flex-col gap-2">
              {ofDay.length === 0 && <p className="pt-2.5 text-sm text-muted">Cerrado</p>}
              {ofDay.map((block, i) => (
                <div key={i} className="flex flex-wrap items-center gap-2">
                  <input
                    type="time"
                    aria-label={`${day.label}, inicio del horario ${i + 1}`}
                    value={block.startTime}
                    onChange={(e) => patch(block, { startTime: e.target.value })}
                    className={timeClass}
                  />
                  <span aria-hidden="true" className="text-muted">a</span>
                  <input
                    type="time"
                    aria-label={`${day.label}, fin del horario ${i + 1}`}
                    value={block.endTime}
                    onChange={(e) => patch(block, { endTime: e.target.value })}
                    className={timeClass}
                  />
                  <Button variant="ghost" size="sm" aria-label={`Quitar horario ${i + 1} del ${day.label}`} onClick={() => remove(block)}>
                    <Trash size={16} aria-hidden="true" />
                  </Button>
                </div>
              ))}
              <div className="flex flex-wrap gap-2">
                <Button variant="ghost" size="sm" onClick={() => add(day.value)}>
                  <Plus size={14} aria-hidden="true" /> Horario
                </Button>
                {ofDay.length > 0 && (
                  <>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => copyDay(day.value, DAYS.filter((d) => d.value !== day.value && d.value !== 'SATURDAY' && d.value !== 'SUNDAY').map((d) => d.value))}
                    >
                      Copiar a días hábiles
                    </Button>
                  </>
                )}
              </div>
            </div>
          </div>
        )
      })}
    </div>
  )
}
