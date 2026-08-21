import cliente from './cliente'
import type { PapelUsuario } from '../types'

export interface UsuarioDoEspaco {
  id: number
  nome: string
  email: string
  papel: PapelUsuario
  criadoEm: string
}

export const listarUsuarios = () =>
  cliente.get<UsuarioDoEspaco[]>('/usuarios').then(r => r.data)

export const alterarPapel = (id: number, papel: PapelUsuario) =>
  cliente.patch<UsuarioDoEspaco>(`/usuarios/${id}/papel`, { papel }).then(r => r.data)
