import cliente from './cliente'
import type { Assinatura, Filial, TipoPessoa } from '../types'

export interface DadosFilial {
  tipoPessoa: TipoPessoa
  nome: string
  nomeFantasia?: string
  documento: string
  inscricaoEstadual?: string
  dataNascimento?: string
  email?: string
  telefone?: string
  cep?: string
  logradouro?: string
  numero?: string
  complemento?: string
  bairro?: string
  cidade?: string
  uf?: string
}

export const listarFiliais = () =>
  cliente.get<Filial[]>('/filiais').then(r => r.data)

export const buscarFilial = (id: number) =>
  cliente.get<Filial>(`/filiais/${id}`).then(r => r.data)

export const criarFilial = (dados: DadosFilial) =>
  cliente.post<Filial>('/filiais', dados).then(r => r.data)

export const atualizarFilial = (id: number, dados: DadosFilial) =>
  cliente.put<Filial>(`/filiais/${id}`, dados).then(r => r.data)

export const excluirFilial = (id: number) =>
  cliente.delete(`/filiais/${id}`)

export const buscarAssinatura = () =>
  cliente.get<Assinatura>('/filiais/assinatura').then(r => r.data)
