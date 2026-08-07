import { useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ChevronLeft, ChevronRight, Pencil, Trash2, Ban, ChevronDown, ChevronUp, Layers } from 'lucide-react'
import { format, parseISO, addMonths, subMonths } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { buscarCartoes } from '../api/cartoes'
import { buscarItensFaturaPorCiclo, excluirItemFatura, cancelarItemFatura } from '../api/itensFatura'
import { buscarFaturas, buscarFatura } from '../api/faturas'
import { pagarTransacao, estornarTransacao } from '../api/transacoes'
import FormularioCompraCartao from '../components/forms/FormularioCompraCartao'
import ModalConfirmacao from '../components/ModalConfirmacao'
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
  const [confirmarExcluirItem, setConfirmarExcluirItem] = useState<ItemFatura | null>(null)
  const [faturaExpandida, setFaturaExpandida] = useState<number | null>(null)
  // Vazio = todas as faturas. Compras parceladas podem cair em ciclos
  // diferentes do mês de hoje, então filtrar por mês ajuda a achar uma
  // fatura específica sem rolar a lista inteira.
  const [mesFatura, setMesFatura] = useState('')
  // Mês do CICLO de fechamento sendo visualizado nos itens em aberto — não é
  // o mês corrido da data da compra, é o mês em que aquele ciclo fecha (ver
  // ItemFaturaService.findAbertosPorCiclo). Abre no mês atual; navegando pra
  // frente dá pra ver que compras (normalmente parcelas futuras) já estão
  // garantidas para entrar em cada fatura seguinte.
  const [mesCiclo, setMesCiclo] = useState(format(new Date(), 'yyyy-MM'))

  const { data: cartoes = [] } = useQuery({ queryKey: ['cartoes'], queryFn: buscarCartoes })
  const cartao = cartoes.find(c => c.id === cartaoId)

  const { data: itensAbertos = [] } = useQuery({
    queryKey: ['itensFatura', 'ciclo', cartaoId, mesCiclo],
    queryFn: () => buscarItensFaturaPorCiclo(cartaoId, mesCiclo),
    enabled: !!cartaoId,
  })
  const totalCiclo = itensAbertos.reduce((s, i) => s + i.valor, 0)

  const { data: faturas = [] } = useQuery({
    queryKey: ['faturas', cartaoId, mesFatura],
    queryFn: () => buscarFaturas(cartaoId, mesFatura || undefined),
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

  const excluirMutation = useMutation({
    mutationFn: excluirItemFatura,
    onSuccess: async () => { await invalidarTudo(); setConfirmarExcluirItem(null); toast.success('Compra excluída') },
  })
  const cancelarMutation = useMutation({
    mutationFn: cancelarItemFatura,
    onSuccess: async () => { await invalidarTudo(); toast.success('Compra cancelada') },
  })
  const pagarMutation = useMutation({
    mutationFn: (id: number) => pagarTransacao(id),
    onSuccess: async () => { await invalidarTudo(); toast.success('Fatura marcada como paga') },
  })
  const estornarMutation = useMutation({
    mutationFn: (id: number) => estornarTransacao(id),
    onSuccess: async () => { await invalidarTudo(); toast.success('Pagamento estornado') },
  })

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

      {/* Fatura em aberto do ciclo selecionado — itens com data dentro da
          janela que fecha no mês escolhido (ver ItemFaturaService.findAbertosPorCiclo).
          Navega mês a mês para ver o que já está garantido em cada fatura futura. */}
      <div>
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between mb-2">
          <h2 className="text-sm font-semibold text-conteudo-suave uppercase tracking-wider">
            Fatura de {format(parseISO(`${mesCiclo}-01`), 'MMMM yyyy', { locale: ptBR })} — {fmt(totalCiclo)}
          </h2>
          <div className="flex items-center gap-2">
            <div className="flex items-center gap-1 bg-superficie-2 rounded-xl px-2 py-1">
              <button onClick={() => setMesCiclo(format(subMonths(parseISO(`${mesCiclo}-01`), 1), 'yyyy-MM'))} className="btn-ghost p-1">
                <ChevronLeft size={16} />
              </button>
              <span className="text-xs text-conteudo-suave min-w-[64px] text-center capitalize">
                {format(parseISO(`${mesCiclo}-01`), 'MMM yyyy', { locale: ptBR })}
              </span>
              <button onClick={() => setMesCiclo(format(addMonths(parseISO(`${mesCiclo}-01`), 1), 'yyyy-MM'))} className="btn-ghost p-1">
                <ChevronRight size={16} />
              </button>
            </div>
            <AcaoNova aoClicar={() => { setEditing(undefined); setShowForm(true) }} rotulo="Nova compra" />
          </div>
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
                onExcluir={() => setConfirmarExcluirItem(item)}
                onCancelar={() => cancelarMutation.mutate(item.id)}
              />
            ))
          )}
        </div>
      </div>

      {/* Faturas fechadas */}
      <div>
        <div className="flex items-center justify-between mb-2">
          <h2 className="text-sm font-semibold text-conteudo-suave uppercase tracking-wider">Faturas</h2>
          <div className="flex items-center gap-2">
            <input
              type="month" className="input py-1 text-sm w-40"
              value={mesFatura} onChange={e => setMesFatura(e.target.value)}
            />
            {mesFatura && (
              <button onClick={() => setMesFatura('')} className="text-xs text-acento hover:opacity-80">
                Ver todas
              </button>
            )}
          </div>
        </div>
        {faturas.length === 0 ? (
          <div className="card text-center py-8 text-conteudo-suave text-sm">
            {mesFatura ? 'Nenhuma fatura fechada nesse mês.' : 'Nenhuma fatura fechada ainda.'}
          </div>
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
                            disabled={pagarMutation.isPending}
                            className="btn-primary text-sm py-1.5 px-3 disabled:opacity-60 disabled:cursor-not-allowed">
                            {pagarMutation.isPending ? 'Processando...' : 'Marcar fatura como paga'}
                          </button>
                        </div>
                      )}
                      {fatura.status === 'PAGA' && (
                        <div className="px-4 py-2 border-b border-borda">
                          <button
                            onClick={() => estornarMutation.mutate(fatura.transacaoDespesaId)}
                            disabled={estornarMutation.isPending}
                            className="py-1.5 px-3 rounded-lg border border-amber-800 text-amber-500 hover:bg-amber-900/20 transition-colors text-sm font-medium disabled:opacity-60 disabled:cursor-not-allowed">
                            {estornarMutation.isPending ? 'Processando...' : 'Estornar pagamento'}
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

      <ModalConfirmacao
        aberto={confirmarExcluirItem !== null}
        titulo="Excluir compra"
        aoFechar={() => !excluirMutation.isPending && setConfirmarExcluirItem(null)}
        botoes={[{ rotulo: 'Excluir', variante: 'perigo', aoClicar: () => excluirMutation.mutate(confirmarExcluirItem!.id), carregando: excluirMutation.isPending }]}
      >
        {`Deseja excluir a compra "${confirmarExcluirItem?.descricao || confirmarExcluirItem?.categoria?.nome}"?`}
      </ModalConfirmacao>

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
