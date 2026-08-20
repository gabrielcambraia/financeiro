import { Pencil, Trash2, Ban, Tag, ExternalLink } from 'lucide-react'
import { format, parseISO } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { corStatusFatura, rotuloStatusFatura } from '../utils/statusTransacao'
import type { ItemFatura, StatusTransacao } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

// Linha de uma compra de cartão (ItemFatura), usada tanto no detalhe de um
// cartão específico (CartaoDetalhe) quanto na visão cross-cartão (Compras no
// Cartão). Quando o item já está faturado, mostra o status da fatura em vez
// das ações de editar/cancelar/excluir — o backend bloqueia essas ações para
// itens faturados, e aqui o link `onVerFatura` leva ao detalhe do cartão.
// `mostrarData`/`mostrarFatura` só fazem sentido numa listagem que mistura
// datas e ciclos de fatura diferentes (ComprasCartao) — em CartaoDetalhe os
// itens já vêm agrupados por dia/fatura, então essa informação seria redundante.
export default function LinhaCompraCartao({ item, onEdit, onExcluir, onCancelar, onVerFatura, editavel, mostrarData, mostrarFatura }: {
  item: ItemFatura
  onEdit?: () => void
  onExcluir?: () => void
  onCancelar?: () => void
  onVerFatura?: () => void
  editavel: boolean
  mostrarData?: boolean
  mostrarFatura?: boolean
}) {
  const cor = item.categoria?.cor ?? '#6B7280'
  return (
    <div className={`flex items-center gap-3 py-2.5 border-b border-borda/50 last:border-b-0 ${item.cancelado ? 'opacity-50' : ''}`}>
      <div
        className="w-[34px] h-[34px] rounded-lg flex items-center justify-center shrink-0"
        style={{ backgroundColor: `${cor}22` }}
      >
        <Tag size={16} style={{ color: cor }} />
      </div>
      <div className="flex-1 min-w-0">
        <div className={`text-sm font-medium text-conteudo truncate ${item.cancelado ? 'line-through' : ''}`}>
          {item.descricao || item.categoria?.nome || '—'}
        </div>
        <div className="text-xs text-conteudo-suave mt-0.5 flex items-center gap-2 flex-wrap">
          {mostrarData && <span>{format(parseISO(item.data), 'dd/MM')}</span>}
          <span>{item.categoria?.nome ?? 'Sem categoria'}{item.totalParcelas ? ` · ${item.numeroParcela}/${item.totalParcelas}x` : ''}</span>
          {item.centroCusto && (
            <span className="flex items-center gap-1">
              <span className="w-1.5 h-1.5 rounded-full shrink-0" style={{ backgroundColor: item.centroCusto.cor }} />
              <span>{item.centroCusto.nome}</span>
            </span>
          )}
          {mostrarFatura && item.faturaDataFechamento && (
            <span>Fatura de {format(parseISO(item.faturaDataFechamento), 'MMMM yyyy', { locale: ptBR })}</span>
          )}
          {item.faturaStatus && (
            <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${corStatusFatura[item.faturaStatus as StatusTransacao]}`}>
              {rotuloStatusFatura[item.faturaStatus as StatusTransacao]}
            </span>
          )}
        </div>
      </div>
      <div className="text-right shrink-0">
        <div className={`text-sm font-bold ${item.cancelado ? 'line-through text-conteudo-suave' : 'text-conteudo'}`}>
          {fmt(item.valor)}
        </div>
        {item.totalParcelas && (
          <div className="text-xs text-conteudo-suave">parcela {item.numeroParcela}/{item.totalParcelas}</div>
        )}
      </div>
      {editavel && !item.cancelado && (
        <div className="flex gap-1 shrink-0">
          {onEdit && (
            <button onClick={onEdit} className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
              <Pencil size={14} />
            </button>
          )}
          {onCancelar && (
            <button onClick={onCancelar} className="p-1.5 rounded-lg hover:bg-aviso/10 text-conteudo-suave hover:text-aviso transition-colors">
              <Ban size={14} />
            </button>
          )}
          {onExcluir && (
            <button onClick={onExcluir} className="p-1.5 rounded-lg hover:bg-perigo/10 text-conteudo-suave hover:text-perigo transition-colors">
              <Trash2 size={14} />
            </button>
          )}
        </div>
      )}
      {!editavel && onVerFatura && (
        <button onClick={onVerFatura} title="Ver detalhe do cartão"
          className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors shrink-0">
          <ExternalLink size={14} />
        </button>
      )}
    </div>
  )
}
