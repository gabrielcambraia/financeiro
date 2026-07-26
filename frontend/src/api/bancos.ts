import cliente from './cliente'
import type { Banco } from '../types'

type PayloadBanco = {
  nome: string
  corPrimaria: string
  sigla: string
}

export const buscarBancos = () => cliente.get<Banco[]>('/bancos').then(r => r.data)

export const criarBanco = (data: PayloadBanco) =>
  cliente.post<Banco>('/bancos', data).then(r => r.data)

export const atualizarBanco = (id: number, data: PayloadBanco) =>
  cliente.put<Banco>(`/bancos/${id}`, data).then(r => r.data)

export const excluirBanco = (id: number) =>
  cliente.delete(`/bancos/${id}`)

export const enviarLogoBanco = (id: number, arquivo: File) => {
  const formData = new FormData()
  formData.append('arquivo', arquivo)
  return cliente.post<Banco>(`/bancos/${id}/logo`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then(r => r.data)
}

export const removerLogoBanco = (id: number) =>
  cliente.delete<Banco>(`/bancos/${id}/logo`).then(r => r.data)

// Usado direto em <img src>, fora do axios — a rota é pública (ver
// ConfiguracaoSeguranca), pois uma tag <img> não manda o header Authorization.
export const logoBancoUrl = (id: number) => `/api/bancos/${id}/logo`
