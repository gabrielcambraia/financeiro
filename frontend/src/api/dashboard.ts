import client from './client'
import type { DashboardData } from '../types'

export const getDashboard = (month: string, accountId?: number) =>
  client.get<DashboardData>('/dashboard', { params: { month, accountId } }).then(r => r.data)
