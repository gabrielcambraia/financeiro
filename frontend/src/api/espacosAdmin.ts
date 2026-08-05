import cliente from './cliente'
import type { PapelUsuario, CodigoPlano } from '../types'
import type { RespostaPaginada, TipoEspaco, PlanoEspaco, ModuloEspaco } from '../types'

export interface VinculoEspaco {
  espacoId: number
  usuarioId: number
  nome: string
  email: string
  papel: PapelUsuario
}

export interface EspacoAdmin {
  id: number
  nome: string
  tipo: TipoEspaco
  plano: PlanoEspaco
  codigoPlano?: CodigoPlano
  criadoEm: string
  emailDono: string | null
  totalMembros: number
  vinculos: VinculoEspaco[]
  modulosHabilitados: ModuloEspaco[]
}

export const listarEspacosAdmin = (pagina: number) =>
  cliente.get<RespostaPaginada<EspacoAdmin>>('/admin/espacos', { params: { pagina } }).then(r => r.data)

export const alterarTipoEspaco = (id: number, tipo: TipoEspaco) =>
  cliente.patch<EspacoAdmin>(`/admin/espacos/${id}/tipo`, { tipo }).then(r => r.data)

export const alterarModulosEspaco = (id: number, modulos: ModuloEspaco[]) =>
  cliente.patch<EspacoAdmin>(`/admin/espacos/${id}/modulos`, { modulos }).then(r => r.data)
