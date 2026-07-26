import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { ChevronLeft, Pencil, Trash2, Ban, ChevronDown, ChevronUp, Layers } from 'lucide-react'
import { format, parseISO } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { buscarCartoes } from '../api/cartoes'
import { buscarItensFatura, excluirItemFatura, cancelarItemFatura } from '../api/itensFatura'
import { buscarFaturas, buscarFatura } from '../api/faturas'
import { pagarTransacao, estornarTransacao } from '../api/transacoes'
import FormularioCompraCartao from '../components/forms/FormularioCompraCartao'
import AcaoNova from '../components/AcaoNova'
import LogoBanco from '../components/LogoBanco'
import type { ItemFatura, StatusTransacao } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const rotuloStatus: Record<StatusTransacao, string> = {
  PAGA: 'Paga', PENDENTE: 'Pendente', ATRASADA: 'Atrasada', CANCELADA: 'Cancelada',
}
const corStatus: Record<StatusTransacao, string> = {
  PAGA: 'bg-emerald-500/15 text-emerald-500',
  PENDENTE: 'bg-amber-500/15 text-amber-500',
  ATRASADA: 'bg-red-500/15 text-red-500',
  CANCELADA: 'bg-gray-500/15 text-gray-400 line-through',
}

function ItemLinha({ item, onEdit, onExcluir, onCancelar, editavel }: {
  item: ItemFatura
  onEdit?: () => void
  onExcluir?: () => void
  onCancelar?: () => void
  editavel: boolean
}) {
  return (
    <div className="flex items-center gap-3 px-4 py-3">
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className={`text-sm font-medium text-conteudo truncate ${item.cancelado ? 'line-through text-conteudo-suave' : ''}`}>
            {item.descricao || item.categoria?.nome || '—'}
          </span>
          {item.totalParcelas && (
            <span className="text-xs text-conteudo-suave flex items-center gap-0.5">
              <Layers size={11} />{item.numeroParcela}/{item.totalParcelas}
            </span>
          )}
        </div>
        <p className="text-xs text-conteudo-suave mt-0.5">
          {format(parseISO(item.data), "dd/MM/yyyy", { locale: ptBR })}
          {item.categoria && ` · ${item.categoria.nome}`}
        </p>
      </div>
      <span className={`text-sm font-bold ${item.cancelado ? 'text-conteudo-suave line-through' : 'text-conteudo'}`}>
        {fmt(item.valor)}
      </span>
      {editavel && !item.cancelado && (
        <div className="flex gap-1">
          {onEdit && (
            <button onClick={onEdit} className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
              <Pencil size={14} />
            </button>
          )}
          {onCancelar && (
            <button onClick={onCancelar} className="p-1.5 rounded-lg hover:bg-orange-900/40 text-conteudo-suave hover:text-orange-500 transition-colors">
              <Ban size={14} />
            </button>
          )}
          {onExcluir && (
            <button onClick={onExcluir} className="p-1.5 rounded-lg hover:bg-red-900/40 text-conteudo-suave hover:text-red-500 transition-colors">
              <Trash2 size={14} />
            </button>
          )}
        </div>
      )}
    </div>
  )
}

export default function CartaoDetalhe() {
  const { id } = useParams()
  const cartaoId = Number(id)
  const qc = useQueryClient()
  const [showForm, setShowForm] = useState(false)
  const [editing, setEditing] = useState<ItemFatura | undefined>()
  const [faturaExpandida, setFaturaExpandida] = useState<number | null>(null)

  const { data: cartoes = [] } = useQuery({ queryKey: ['cartoes'], queryFn: buscarCartoes })
  const cartao = cartoes.find(c => c.id === cartaoId)

  const { data: itensAbertos = [] } = useQuery({
    queryKey: ['itensFatura', cartaoId, 'abertos'],
    queryFn: () => buscarItensFatura({ cartaoId }),
    enabled: !!cartaoId,
  })

  const { data: faturas = [] } = useQuery({
    queryKey: ['faturas', cartaoId],
    queryFn: () => buscarFaturas(cartaoId),
    enabled: !!cartaoId,
  })

  const { data: faturaDetalhe } = useQuery({
    queryKey: ['fatura', faturaExpandida],
    queryFn: () => buscarFatura(faturaExpandida as number),
    enabled: faturaExpandida !== null,
  })

  const invalidarTudo = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['itensFatura'] }),
      qc.invalidateQueries({ queryKey: ['faturas'] }),
      qc.invalidateQueries({ queryKey: ['cartoes'] }),
      qc.invalidateQueries({ queryKey: ['fatura'] }),
      qc.invalidateQueries({ queryKey: ['transacoes'] }),
      qc.invalidateQueries({ queryKey: ['painel'] }),
      qc.invalidateQueries({ queryKey: ['contas'] }),
    ])
  }

  const excluirMutation = useMutation({ mutationFn: excluirItemFatura, onSuccess: invalidarTudo })
  const cancelarMutation = useMutation({ mutationFn: cancelarItemFatura, onSuccess: invalidarTudo })
  const pagarMutation = useMutation({ mutationFn: (id: number) => pagarTransacao(id), onSuccess: invalidarTudo })
  const estornarMutation = useMutation({ mutationFn: (id: number) => estornarTransacao(id), onSuccess: invalidarTudo })

  if (!cartao) {
    return <div className="p-6 text-conteudo-suave">Carregando...</div>
  }

  return (
    <div className="p-6 space-y-6">
      <div className="flex items-center gap-3">
        <Link to="/cartoes" className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
          <ChevronLeft size={20} />
        </Link>
        <LogoBanco banco={cartao.banco} tamanho={40} />
        <div>
          <h1 className="text-2xl font-bold text-conteudo">{cartao.nome}</h1>
          <p className="text-sm text-conteudo-suave">
            {fmt(cartao.limiteDisponivel)} disponível de {fmt(cartao.limite)} · fecha dia {cartao.diaFechamento}, vence dia {cartao.diaVencimento}
          </p>
        </div>
      </div>

      {/* Fatura atual (em aberto) */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-sm font-semibold text-conteudo-suave uppercase tracking-wider">
            Fatura atual — {fmt(cartao.faturaAtualTotal)}
          </h2>
          <AcaoNova aoClicar={() => { setEditing(undefined); setShowForm(true) }} rotulo="Nova compra" />
        </div>
        <div className="card p-0 overflow-hidden divide-y divide-borda">
          {itensAbertos.length === 0 ? (
            <p className="text-center py-8 text-conteudo-suave text-sm">Nenhuma compra nesta fatura ainda.</p>
          ) : (
            itensAbertos.map(item => (
              <ItemLinha
                key={item.id}
                item={item}
                editavel
                onEdit={() => { setEditing(item); setShowForm(true) }}
                onExcluir={() => { if (confirm('Excluir esta compra?')) excluirMutation.mutate(item.id) }}
                onCancelar={() => cancelarMutation.mutate(item.id)}
              />
            ))
          )}
        </div>
      </div>

      {/* Faturas fechadas */}
      <div>
        <h2 className="text-sm font-semibold text-conteudo-suave uppercase tracking-wider mb-2">Faturas</h2>
        {faturas.length === 0 ? (
          <div className="card text-center py-8 text-conteudo-suave text-sm">Nenhuma fatura fechada ainda.</div>
        ) : (
          <div className="space-y-2">
            {faturas.map(fatura => {
              const expandida = faturaExpandida === fatura.id
              return (
                <div key={fatura.id} className="card p-0 overflow-hidden">
                  <button
                    onClick={() => setFaturaExpandida(expandida ? null : fatura.id)}
                    className="w-full flex items-center gap-3 px-4 py-3.5 hover:bg-superficie-2 transition-colors"
                  >
                    {expandida ? <ChevronUp size={16} className="text-conteudo-suave shrink-0" /> : <ChevronDown size={16} className="text-conteudo-suave shrink-0" />}
                    <div className="flex-1 min-w-0 text-left">
                      <div className="flex items-center gap-2">
                        <span className="text-sm font-medium text-conteudo">
                          Vencimento {format(parseISO(fatura.dataVencimento), "dd/MM/yyyy", { locale: ptBR })}
                        </span>
                        <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${corStatus[fatura.status]}`}>
                          {rotuloStatus[fatura.status]}
                        </span>
                      </div>
                      <p className="text-xs text-conteudo-suave mt-0.5">
                        Fechada em {format(parseISO(fatura.dataFechamento), "dd/MM/yyyy", { locale: ptBR })}
                      </p>
                    </div>
                    <span className="text-base font-bold text-conteudo">{fmt(fatura.valor)}</span>
                  </button>

                  {expandida && (
                    <div className="border-t border-borda">
                      {(fatura.status === 'PENDENTE' || fatura.status === 'ATRASADA') && (
                        <div className="px-4 py-2 border-b border-borda">
                          <button
                            onClick={() => pagarMutation.mutate(fatura.transacaoDespesaId)}
                            className="btn-primary text-sm py-1.5 px-3">
                            Marcar fatura como paga
                          </button>
                        </div>
                      )}
                      {fatura.status === 'PAGA' && (
                        <div className="px-4 py-2 border-b border-borda">
                          <button
                            onClick={() => estornarMutation.mutate(fatura.transacaoDespesaId)}
                            className="py-1.5 px-3 rounded-lg border border-amber-800 text-amber-500 hover:bg-amber-900/20 transition-colors text-sm font-medium">
                            Estornar pagamento
                          </button>
                        </div>
                      )}
                      <div className="divide-y divide-borda">
                        {faturaExpandida === fatura.id && faturaDetalhe?.itens?.map(item => (
                          <ItemLinha key={item.id} item={item} editavel={false} />
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              )
            })}
          </div>
        )}
      </div>

      {showForm && (
        <FormularioCompraCartao
          cartaoId={cartaoId}
          editing={editing}
          onClose={() => { setShowForm(false); setEditing(undefined) }}
        />
      )}
    </div>
  )
}
