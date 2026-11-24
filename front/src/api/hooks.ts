import { useQuery } from '@tanstack/react-query'
import { referenceApi } from './endpoints'

// Catalogs change rarely, so one fetch per session is enough.
const STATIC = { staleTime: Infinity, gcTime: Infinity }

export const useCategories = () => useQuery({ queryKey: ['categories'], queryFn: referenceApi.categories, ...STATIC })
export const useCities = () => useQuery({ queryKey: ['cities'], queryFn: referenceApi.cities, ...STATIC })
