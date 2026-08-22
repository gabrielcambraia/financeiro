import cliente from './cliente'
import type { FilialResumo, NivelAcesso, PapelUsuario, TipoPessoa } from '../types'

export interface RespostaAutenticacao {
  token: string
  usuarioId: number
  nome: string
  email: string
  espacoId: number
  papel: PapelUsuario
  precisaTrocarSenha: boolean
  precisaCadastrarTelefone: boolean
  nivelAcesso: NivelAcesso
  filiais?: FilialResumo[]
}

export interface RespostaConfigAuth {
  requerAutenticacao: boolean
}

export interface DadosPrimeiraFilial {
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

export const registrar = (dados: {
  nome: string
  email: string
  senha: string
  telefone?: string
  entidade: DadosPrimeiraFilial
}) =>
  cliente.post<RespostaAutenticacao>('/auth/register', dados).then(r => r.data)

export const entrar = (dados: { email: string; senha: string }) =>
  cliente.post<RespostaAutenticacao>('/auth/login', dados).then(r => r.data)

export const obterConfigAuth = () =>
  cliente.get<RespostaConfigAuth>('/auth/config').then(r => r.data)

export const trocarSenha = (dados: { senhaAtual: string; novaSenha: string }) =>
  cliente.post<RespostaAutenticacao>('/auth/trocar-senha', dados).then(r => r.data)

export const cadastrarTelefone = (dados: { telefone: string }) =>
  cliente.post<RespostaAutenticacao>('/auth/telefone', dados).then(r => r.data)

export const renovar = () =>
  cliente.post<RespostaAutenticacao>('/auth/renovar').then(r => r.data)

export const sair = () =>
  cliente.post('/auth/sair')
