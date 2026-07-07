import cliente from './cliente'
import type { DadosPainel } from '../types'

export const buscarPainel = (month: string, contaId?: number) =>
  cliente.get<DadosPainel>('/painel', { params: { month, contaId } }).then(r => r.data)
