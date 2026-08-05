import cliente from './cliente'
import type { CodigoPlano, PlanoAdmin } from '../types'

export const listarPlanos = () =>
  cliente.get<PlanoAdmin[]>('/admin/planos').then(r => r.data)

export const atualizarPlano = (id: number, dados: { limiteEntidades?: number; ativo?: boolean }) =>
  cliente.patch<PlanoAdmin>(`/admin/planos/${id}`, dados).then(r => r.data)

export const alterarPlanoEspaco = (espacoId: number, plano: CodigoPlano) =>
  cliente.patch(`/admin/espacos/${espacoId}/plano`, { plano })
