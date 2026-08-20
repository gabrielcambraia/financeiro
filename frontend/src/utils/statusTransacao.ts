import type { StatusTransacao } from '../types'

// Cores/rótulos de status de fatura/transação usados nas telas de cartão
// (CartaoDetalhe, ComprasCartao) — extraído para não misturar constantes com
// componente em LinhaCompraCartao.tsx (quebra o fast refresh do Vite).
export const corStatusFatura: Record<StatusTransacao, string> = {
  PAGA: 'bg-sucesso-fundo text-sucesso',
  PENDENTE: 'bg-aviso-fundo text-aviso',
  ATRASADA: 'bg-perigo-fundo text-perigo',
  CANCELADA: 'bg-superficie-2 text-conteudo-suave',
}
export const rotuloStatusFatura: Record<StatusTransacao, string> = {
  PAGA: 'Paga', PENDENTE: 'Pendente', ATRASADA: 'Atrasada', CANCELADA: 'Cancelada',
}
