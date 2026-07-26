import cliente from './cliente'
import type { Transacao } from '../types'

export const buscarCalendario = (mes: string) =>
  cliente.get<Transacao[]>('/calendario', { params: { mes } }).then(r => r.data)
