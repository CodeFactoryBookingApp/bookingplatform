import type { BookingStatus } from '@/api/types'

/**
 * The backend has no "my bookings" endpoint for clients yet, so the receipts a client gets in this
 * browser are kept here to let them find and cancel those bookings later. It is a convenience:
 * it may be empty (another device, cleared storage) and the status may be out of date.
 */
export interface SavedBooking {
  id: string
  code: string
  status: BookingStatus
  service: string
  organization: string
  location: string
  startsAt: string
  endsAt: string
  price: number
  currency: string
  freeCancellationWindowMinutes: number
  savedAt: string
}

const key = (userId: string) => `bp.bookings.${userId}`

export function readBookings(userId: string): SavedBooking[] {
  try {
    const raw = localStorage.getItem(key(userId))
    const parsed = raw ? (JSON.parse(raw) as SavedBooking[]) : []
    return Array.isArray(parsed) ? parsed : []
  } catch {
    return []
  }
}

function write(userId: string, bookings: SavedBooking[]) {
  try {
    localStorage.setItem(key(userId), JSON.stringify(bookings.slice(0, 100)))
  } catch {
    /* storage blocked: the list just stays empty */
  }
}

export function saveBooking(userId: string, booking: SavedBooking) {
  write(userId, [booking, ...readBookings(userId).filter((b) => b.id !== booking.id)])
}

export function updateBookingStatus(userId: string, id: string, status: BookingStatus) {
  write(
    userId,
    readBookings(userId).map((b) => (b.id === id ? { ...b, status } : b)),
  )
}

export function forgetBooking(userId: string, id: string) {
  write(
    userId,
    readBookings(userId).filter((b) => b.id !== id),
  )
}
