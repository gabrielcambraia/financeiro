import cliente from './cliente'
import type { PapelUsuario } from '../store/lojaAutenticacao'

export interface RespostaMembroAdicionado {
  usuarioId: number
  nome: string
  email: string
  papel: PapelUsuario
  senhaTemporaria: string
}

export const adicionarMembro = (dados: { nome: string; email: string }) =>
  cliente.post<RespostaMembroAdicionado>('/espacos/membros', dados).then(r => r.data)
