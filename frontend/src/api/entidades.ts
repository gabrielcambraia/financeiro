import cliente from './cliente'
import type { Assinatura, Entidade, TipoPessoa } from '../types'

export interface DadosEntidade {
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

export const listarEntidades = () =>
  cliente.get<Entidade[]>('/entidades').then(r => r.data)

export const buscarEntidade = (id: number) =>
  cliente.get<Entidade>(`/entidades/${id}`).then(r => r.data)

export const criarEntidade = (dados: DadosEntidade) =>
  cliente.post<Entidade>('/entidades', dados).then(r => r.data)

export const atualizarEntidade = (id: number, dados: DadosEntidade) =>
  cliente.put<Entidade>(`/entidades/${id}`, dados).then(r => r.data)

export const excluirEntidade = (id: number) =>
  cliente.delete(`/entidades/${id}`)

export const buscarAssinatura = () =>
  cliente.get<Assinatura>('/entidades/assinatura').then(r => r.data)
