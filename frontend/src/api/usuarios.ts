import cliente from './cliente'
import type { PapelUsuario } from '../types'

export interface UsuarioDoEspaco {
  id: number
  nome: string
  email: string
  telefone: string | null
  papel: PapelUsuario
  criadoEm: string
}

export interface UsuarioCriado extends UsuarioDoEspaco {
  senhaTemporaria: string
}

export const listarUsuarios = () =>
  cliente.get<UsuarioDoEspaco[]>('/usuarios').then(r => r.data)

export const criarUsuario = (dados: { nome: string; email: string; telefone?: string; papel: PapelUsuario }) =>
  cliente.post<UsuarioCriado>('/usuarios', dados).then(r => r.data)

export const atualizarUsuario = (
  id: number,
  dados: { nome: string; email: string; telefone?: string; papel: PapelUsuario }
) =>
  cliente.put<UsuarioDoEspaco>(`/usuarios/${id}`, dados).then(r => r.data)
