import cliente from './cliente'
import type { PapelUsuario } from '../store/lojaAutenticacao'

export interface RespostaMembroAdicionado {
  usuarioId: number
  nome: string
  email: string
  papel: PapelUsuario
  senhaTemporaria: string
}

export interface RespostaMembro {
  usuarioId: number
  nome: string
  email: string
  papel: PapelUsuario
  precisaTrocarSenha: boolean
}

export const adicionarMembro = (dados: { nome: string; email: string }) =>
  cliente.post<RespostaMembroAdicionado>('/espacos/membros', dados).then(r => r.data)

export const listarMembros = () =>
  cliente.get<RespostaMembro[]>('/espacos/membros').then(r => r.data)
