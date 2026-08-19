import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { CheckCircle2, Undo2, ExternalLink } from 'lucide-react'
import { format, parseISO } from 'date-fns'
import { buscarTodasFaturas } from '../../api/faturas'
import { buscarCartoes } from '../../api/cartoes'
import { pagarTransacao, estornarTransacao } from '../../api/transacoes'
import { useLojaFiltro } from '../../store/lojaFiltro'
import SeletorMes from '../../components/SeletorMes'
import SobreposicaoModal from '../../components/SobreposicaoModal'
import Spinner from '../../components/Spinner'
import type { StatusTransacao } from '../../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const corStatus: Record<StatusTransacao, string> = {
  PAGA: 'bg-emerald-500/15 text-emerald-500',
  PENDENTE: 'bg-amber-500/15 text-amber-500',
  ATRASADA: 'bg-red-500/15 text-red-500',
  CANCELADA: 'bg-gray-500/15 text-gray-400',
}
const rotuloStatus: Record<StatusTransacao, string> = {
  PAGA: 'Paga', PENDENTE: 'Pendente', ATRASADA: 'Atrasada', CANCELADA: 'Cancelada',
}

export default function Faturas() {
  const qc = useQueryClient()
  const { mes } = useLojaFiltro()
  const [filtroCartao, setFiltroCartao] = useState<number | ''>('')
  const [pagarModal, setPagarModal] = useState<{ id: number; status: StatusTransacao } | null>(null)

  const { data: faturas = [], isLoading } = useQuery({
    queryKey: ['faturas', 'todas', mes, filtroCartao],
    queryFn: () => buscarTodasFaturas(mes, filtroCartao || undefined),
  })

  const { data: cartoes = [] } = useQuery({ queryKey: ['cartoes'], queryFn: buscarCartoes })

  const invalidar = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['faturas'] }),
      qc.invalidateQueries({ queryKey: ['painel'] }),
      qc.invalidateQueries({ queryKey: ['contas'] }),
    ])
  }

  const pagarMutation = useMutation({
    mutationFn: ({ id, dataPagamento }: { id: number; dataPagamento: string }) =>
      pagarTransacao(id, { dataPagamento }),
    onSuccess: async () => { await invalidar(); setPagarModal(null); toast.success('Fatura marcada como paga') },
  })

  const estornarMutation = useMutation({
    mutationFn: (id: number) => estornarTransacao(id),
    onSuccess: async () => { await invalidar(); toast.success('Pagamento estornado') },
  })

  const totalFaturas = faturas.reduce((s, f) => s + f.valor, 0)
  const totalPendente = faturas
    .filter(f => f.status === 'PENDENTE' || f.status === 'ATRASADA')
    .reduce((s, f) => s + f.valor, 0)

  return (
    <div className="p-6 space-y-5">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-conteudo">Faturas</h1>
          <p className="text-sm text-conteudo-suave mt-0.5">Faturas fechadas de cartões de crédito</p>
        </div>
        <div className="flex items-center gap-3">
          <SeletorMes />
        </div>
      </div>

      {/* Resumo */}
      <div className="grid grid-cols-2 gap-3">
        <div className="card p-4">
          <p className="text-xs text-conteudo-suave">Total faturas</p>
          <p className="text-xl font-bold text-conteudo">{fmt(totalFaturas)}</p>
          <p className="text-xs text-conteudo-suave mt-1">{faturas.length} fatura{faturas.length !== 1 ? 's' : ''}</p>
        </div>
        <div className="card p-4">
          <p className="text-xs text-conteudo-suave">Pendente / Atrasado</p>
          <p className="text-xl font-bold text-perigo">{fmt(totalPendente)}</p>
          <p className="text-xs text-conteudo-suave mt-1">
            {faturas.filter(f => f.status === 'PENDENTE' || f.status === 'ATRASADA').length} fatura{faturas.filter(f => f.status === 'PENDENTE' || f.status === 'ATRASADA').length !== 1 ? 's' : ''}
          </p>
        </div>
      </div>

      {/* Filtro de cartão */}
      <div className="card p-3 flex flex-wrap gap-2 items-center">
        <select
          className="text-xs px-3 py-1.5 rounded-full font-medium border border-borda bg-superficie text-conteudo-suave focus:border-acento focus:outline-none cursor-pointer"
          value={filtroCartao}
          onChange={e => setFiltroCartao(e.target.value ? Number(e.target.value) : '')}>
          <option value="">Todos os cartões</option>
          {[...cartoes].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => (
            <option key={c.id} value={c.id}>{c.nome}</option>
          ))}
        </select>
      </div>

      {/* Tabela */}
      {isLoading ? (
        <div className="flex justify-center py-12"><Spinner /></div>
      ) : faturas.length === 0 ? (
        <div className="card text-center py-12 text-conteudo-suave">
          <p>Nenhuma fatura encontrada para este período.</p>
        </div>
      ) : (
        <>
          {/* Desktop */}
          <div className="hidden md:block card p-0 overflow-hidden">
            <div className="overflow-x-auto">
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr className="border-b border-borda">
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Cartão</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Fechamento</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Vencimento</th>
                    <th className="px-4 py-3 text-left text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Status</th>
                    <th className="px-4 py-3 text-right text-xs font-semibold text-conteudo-suave uppercase tracking-wide">Valor</th>
                    <th className="px-4 py-3 w-[80px]" />
                  </tr>
                </thead>
                <tbody>
                  {faturas.map(fatura => (
                    <tr key={fatura.id} className="group border-b border-borda last:border-b-0 hover:bg-superficie-2 transition-colors">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-2">
                          <div className="w-3 h-3 rounded-full shrink-0" style={{ background: fatura.cartao.cor }} />
                          <span className="font-medium text-conteudo">{fatura.cartao.nome}</span>
                        </div>
                        <div className="text-xs text-conteudo-suave mt-0.5">{fatura.cartao.contaPagamento.nome}</div>
                      </td>
                      <td className="px-4 py-3 text-sm text-conteudo-suave whitespace-nowrap">
                        {format(parseISO(fatura.dataFechamento), 'dd/MM/yyyy')}
                      </td>
                      <td className="px-4 py-3 text-sm text-conteudo-suave whitespace-nowrap">
                        {format(parseISO(fatura.dataVencimento), 'dd/MM/yyyy')}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${corStatus[fatura.status]}`}>
                          {rotuloStatus[fatura.status]}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <span className="font-bold text-red-500 whitespace-nowrap">-{fmt(fatura.valor)}</span>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex gap-0.5 justify-end opacity-0 group-hover:opacity-100 transition-opacity">
                          {(fatura.status === 'PENDENTE' || fatura.status === 'ATRASADA') && (
                            <button onClick={() => setPagarModal({ id: fatura.transacaoDespesaId, status: fatura.status })}
                              title="Marcar como paga"
                              className="p-1.5 rounded-lg hover:bg-emerald-900/40 text-conteudo-suave hover:text-emerald-500 transition-colors">
                              <CheckCircle2 size={14} />
                            </button>
                          )}
                          {fatura.status === 'PAGA' && (
                            <button onClick={() => estornarMutation.mutate(fatura.transacaoDespesaId)}
                              title="Estornar pagamento"
                              className="p-1.5 rounded-lg hover:bg-amber-900/40 text-conteudo-suave hover:text-amber-500 transition-colors">
                              <Undo2 size={14} />
                            </button>
                          )}
                          <Link to={`/cartoes/${fatura.cartaoId}`} title="Ver detalhe do cartão"
                            className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
                            <ExternalLink size={14} />
                          </Link>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>

          {/* Mobile */}
          <div className="md:hidden space-y-2">
            {faturas.map(fatura => (
              <div key={fatura.id} className="card flex items-center gap-3 px-4 py-3">
                <div className="w-9 h-9 rounded-xl flex items-center justify-center shrink-0"
                  style={{ background: `${fatura.cartao.cor}20` }}>
                  <div className="w-3 h-3 rounded-full" style={{ background: fatura.cartao.cor }} />
                </div>
                <div className="flex-1 min-w-0">
                  <div className="font-medium text-conteudo text-sm">{fatura.cartao.nome}</div>
                  <div className="text-xs text-conteudo-suave mt-0.5">
                    Fecha {format(parseISO(fatura.dataFechamento), 'dd/MM')} · Vence {format(parseISO(fatura.dataVencimento), 'dd/MM')}
                  </div>
                </div>
                <div className="text-right shrink-0">
                  <div className="font-bold text-red-500 text-sm">{fmt(fatura.valor)}</div>
                  <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${corStatus[fatura.status]}`}>
                    {rotuloStatus[fatura.status]}
                  </span>
                </div>
                {(fatura.status === 'PENDENTE' || fatura.status === 'ATRASADA') && (
                  <button onClick={() => setPagarModal({ id: fatura.transacaoDespesaId, status: fatura.status })}
                    className="p-1.5 rounded-lg text-conteudo-suave hover:text-emerald-500 transition-colors shrink-0">
                    <CheckCircle2 size={18} />
                  </button>
                )}
              </div>
            ))}
          </div>
        </>
      )}

      {pagarModal && (
        <PagarFaturaModal
          status={pagarModal.status}
          aoFechar={() => setPagarModal(null)}
          aoConfirmar={dataPagamento => pagarMutation.mutate({ id: pagarModal.id, dataPagamento })}
          carregando={pagarMutation.isPending}
        />
      )}
    </div>
  )
}

function PagarFaturaModal({ status, aoFechar, aoConfirmar, carregando }: {
  status: StatusTransacao
  aoFechar: () => void
  aoConfirmar: (dataPagamento: string) => void
  carregando: boolean
}) {
  const hoje = format(new Date(), 'yyyy-MM-dd')
  const [dataPagamento, setDataPagamento] = useState(hoje)

  return (
    <SobreposicaoModal aoFechar={aoFechar}>
      <div className="cartao-modal max-w-sm">
        <div className="cartao-modal-cabecalho">
          <h3 className="text-base font-semibold text-conteudo">Pagar fatura</h3>
        </div>
        <form onSubmit={e => { e.preventDefault(); aoConfirmar(dataPagamento) }} className="cartao-modal-corpo">
          <div>
            <label className="label">Data de pagamento</label>
            <input className="input" type="date" max={hoje}
              value={dataPagamento} onChange={e => setDataPagamento(e.target.value)} required />
            {status === 'ATRASADA' && (
              <p className="text-xs text-orange-500 mt-1">Esta fatura está com o vencimento ultrapassado.</p>
            )}
          </div>
          <div className="flex gap-3 pt-2">
            <button type="button" onClick={aoFechar}
              className="flex-1 py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium">
              Cancelar
            </button>
            <button type="submit" disabled={carregando} className="flex-1 btn-primary">
              {carregando ? 'Salvando...' : 'Confirmar'}
            </button>
          </div>
        </form>
      </div>
    </SobreposicaoModal>
  )
}
