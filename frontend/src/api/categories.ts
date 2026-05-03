import client from './client'
import type { Category, TransactionType } from '../types'

export const getCategories = (type?: TransactionType) =>
  client.get<Category[]>('/categories', { params: type ? { type } : {} }).then(r => r.data)

export const createCategory = (data: Omit<Category, 'id'>) =>
  client.post<Category>('/categories', data).then(r => r.data)

export const updateCategory = (id: number, data: Omit<Category, 'id'>) =>
  client.put<Category>(`/categories/${id}`, data).then(r => r.data)

export const deleteCategory = (id: number) =>
  client.delete(`/categories/${id}`)
