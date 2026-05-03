import client from './client'
import type { Account } from '../types'

export const getAccounts = () => client.get<Account[]>('/accounts').then(r => r.data)

export const createAccount = (data: Omit<Account, 'id'>) =>
  client.post<Account>('/accounts', data).then(r => r.data)

export const updateAccount = (id: number, data: Omit<Account, 'id'>) =>
  client.put<Account>(`/accounts/${id}`, data).then(r => r.data)

export const deleteAccount = (id: number) =>
  client.delete(`/accounts/${id}`)
