import cliente from './cliente'
import type { Categoria, TipoTransacao } from '../types'

export const buscarCategorias = (tipo?: TipoTransacao) =>
  cliente.get<Categoria[]>('/categorias', { params: tipo ? { tipo } : {} }).then(r => r.data)

export const criarCategoria = (data: Omit<Categoria, 'id'>) =>
  cliente.post<Categoria>('/categorias', data).then(r => r.data)

export const atualizarCategoria = (id: number, data: Omit<Categoria, 'id'>) =>
  cliente.put<Categoria>(`/categorias/${id}`, data).then(r => r.data)

export const excluirCategoria = (id: number) =>
  cliente.delete(`/categorias/${id}`)
