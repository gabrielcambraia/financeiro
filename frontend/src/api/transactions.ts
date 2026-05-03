import client from './client'
import type { Transaction, TransactionType } from '../types'

interface TransactionFilters {
  month: string
  accountId?: number
  type?: TransactionType
  categoryId?: number
}

interface CreateTransactionPayload {
  accountId: number
  categoryId?: number
  type: TransactionType
  paymentType: 'DEBIT' | 'CREDIT'
  amount: number
  description?: string
  date: string
  fixed: boolean
  installmentTotal?: number
}

export const getTransactions = (filters: TransactionFilters) =>
  client.get<Transaction[]>('/transactions', { params: filters }).then(r => r.data)

export const createTransaction = (data: CreateTransactionPayload) =>
  client.post<Transaction[]>('/transactions', data).then(r => r.data)

export const updateTransaction = (id: number, data: CreateTransactionPayload) =>
  client.put<Transaction>(`/transactions/${id}`, data).then(r => r.data)

export type DeleteScope = 'SINGLE' | 'GROUP' | 'FUTURE'

export const deleteTransaction = (id: number, scope: DeleteScope = 'SINGLE') =>
  client.delete(`/transactions/${id}`, { params: { scope } })
