import { Link } from 'react-router-dom'
import { addDays, format, parseISO } from 'date-fns'
import { AlertTriangle, ArrowDownCircle, ArrowUpCircle, CheckCircle2 } from 'lucide-react'
import type { GrupoVencimento, ItemVencimento } from '../types'

// Mesma janela usada pelo backend (PainelService.DIAS_JANELA_VENCIMENTO).
const DIAS_JANELA_VENCIMENTO = 7

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

function textoPrazo(dias: number) {
  if (dias < 0) return `há ${Math.abs(dias)} dia${Math.abs(dias) === 1 ? '' : 's'}`
  if (dias === 0) return 'hoje'
  if (dias === 1) return 'amanhã'
  return `em ${dias} dias`
}

function ItemLinha({ item, vencida }: { item: ItemVencimento; vencida: boolean }) {
  return (
    <div className="flex items-center justify-between gap-3 py-1.5">
      <div className="min-w-0">
        <p className={`text-sm truncate ${vencida ? 'text-red-500 font-medium' : 'text-conteudo'}`}>
          {item.descricao}
        </p>
        <p className="text-xs text-conteudo-suave truncate">{item.contaNome}</p>
      </div>
      <div className="text-right shrink-0">
        <p className={`text-sm font-semibold ${vencida ? 'text-red-500' : 'text-conteudo'}`}>
          {fmt(item.valor)}
        </p>
        <p className="text-xs text-conteudo-suave">
          venc. {format(parseISO(item.dataVencimento), 'dd/MM')} · {textoPrazo(item.diasEmRelacaoAHoje)}
        </p>
      </div>
    </div>
  )
}

interface Props {
  titulo: string
  variante: 'pagar' | 'receber'
  grupo?: GrupoVencimento
}

const GRUPO_VAZIO: GrupoVencimento = {
  totalVencido: 0, quantidadeVencida: 0, vencidas: [],
  totalAVencer: 0, quantidadeAVencer: 0, aVencer: [],
}

export default function CartaoVencimentos({ titulo, variante, grupo = GRUPO_VAZIO }: Props) {
  const Icone = variante === 'pagar' ? ArrowDownCircle : ArrowUpCircle
  const temVencidas = grupo.quantidadeVencida > 0
  const vazio = grupo.quantidadeVencida === 0 && grupo.quantidadeAVencer === 0
  const tipo = variante === 'pagar' ? 'DESPESA' : 'RECEITA'
  const dataVencimentoFim = format(addDays(new Date(), DIAS_JANELA_VENCIMENTO), 'yyyy-MM-dd')

  return (
    <div className={`card border ${temVencidas ? 'border-red-400/40' : 'border-borda'}`}>
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <Icone size={16} className={variante === 'pagar' ? 'text-red-500' : 'text-emerald-500'} />
          <p className="text-sm font-semibold text-conteudo">{titulo}</p>
        </div>
        <Link
          to="/transacoes"
          state={{ tipo, dataVencimentoFim }}
          className="text-xs text-acento hover:underline shrink-0"
        >
          Ver todas
        </Link>
      </div>

      {vazio ? (
        <div className="flex items-center gap-2 text-sm text-conteudo-suave py-4 justify-center">
          <CheckCircle2 size={16} className="text-emerald-500" />
          Nada por aqui
        </div>
      ) : (
        <div className="space-y-4">
          {temVencidas && (
            <div>
              <div className="flex items-center justify-between bg-red-500/10 text-red-500 rounded-lg px-2.5 py-1.5 mb-1">
                <span className="text-xs font-medium flex items-center gap-1.5">
                  <AlertTriangle size={12} />
                  {grupo.quantidadeVencida} vencida{grupo.quantidadeVencida === 1 ? '' : 's'}
                </span>
                <span className="text-xs font-semibold">{fmt(grupo.totalVencido)}</span>
              </div>
              <div className="divide-y divide-borda">
                {grupo.vencidas.map(item => (
                  <ItemLinha key={item.id} item={item} vencida />
                ))}
              </div>
              {grupo.quantidadeVencida > grupo.vencidas.length && (
                <p className="text-xs text-conteudo-suave mt-1">
                  + {grupo.quantidadeVencida - grupo.vencidas.length} outra{grupo.quantidadeVencida - grupo.vencidas.length === 1 ? '' : 's'}
                </p>
              )}
            </div>
          )}

          {grupo.quantidadeAVencer > 0 && (
            <div>
              <div className="flex items-center justify-between px-0.5 mb-1">
                <span className="text-xs font-medium text-conteudo-suave">A vencer (7 dias)</span>
                <span className="text-xs font-semibold text-conteudo-suave">{fmt(grupo.totalAVencer)}</span>
              </div>
              <div className="divide-y divide-borda">
                {grupo.aVencer.map(item => (
                  <ItemLinha key={item.id} item={item} vencida={false} />
                ))}
              </div>
              {grupo.quantidadeAVencer > grupo.aVencer.length && (
                <p className="text-xs text-conteudo-suave mt-1">
                  + {grupo.quantidadeAVencer - grupo.aVencer.length} outra{grupo.quantidadeAVencer - grupo.aVencer.length === 1 ? '' : 's'}
                </p>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  )
}
