import cliente from './cliente'
import type { Conta } from '../types'

export const buscarContas = () => cliente.get<Conta[]>('/contas').then(r => r.data)

export const criarConta = (data: Omit<Conta, 'id'>) =>
  cliente.post<Conta>('/contas', data).then(r => r.data)

export const atualizarConta = (id: number, data: Omit<Conta, 'id'>) =>
  cliente.put<Conta>(`/contas/${id}`, data).then(r => r.data)

export const excluirConta = (id: number) =>
  cliente.delete(`/contas/${id}`)
