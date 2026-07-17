import cliente from './cliente'
import type { PapelUsuario } from '../store/lojaAutenticacao'

export interface RespostaAutenticacao {
  token: string
  usuarioId: number
  nome: string
  email: string
  espacoId: number
  papel: PapelUsuario
  precisaTrocarSenha: boolean
}

export interface RespostaConfigAuth {
  requerAutenticacao: boolean
}

export const registrar = (dados: { nome: string; email: string; senha: string }) =>
  cliente.post<RespostaAutenticacao>('/auth/register', dados).then(r => r.data)

export const entrar = (dados: { email: string; senha: string }) =>
  cliente.post<RespostaAutenticacao>('/auth/login', dados).then(r => r.data)

export const obterConfigAuth = () =>
  cliente.get<RespostaConfigAuth>('/auth/config').then(r => r.data)

export const trocarSenha = (dados: { senhaAtual: string; novaSenha: string }) =>
  cliente.post<RespostaAutenticacao>('/auth/trocar-senha', dados).then(r => r.data)

export const renovar = () =>
  cliente.post<RespostaAutenticacao>('/auth/renovar').then(r => r.data)

export const sair = () =>
  cliente.post('/auth/sair')
