import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { format, parseISO, startOfMonth, endOfMonth, eachDayOfInterval, getDay, isSameDay, isToday } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { CreditCard, Banknote, ArrowRightLeft } from 'lucide-react'
import { buscarCalendario } from '../api/calendario'
import { useLojaFiltro } from '../store/lojaFiltro'
import SeletorMes from '../components/SeletorMes'
import Spinner from '../components/Spinner'
import type { StatusTransacao, Transacao } from '../types'

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

const DIAS_SEMANA = ['D', 'S', 'T', 'Q', 'Q', 'S', 'S']

export default function Calendario() {
  const { mes } = useLojaFiltro()
  const [diaSelecionado, setDiaSelecionado] = useState<Date | null>(null)

  const { data: itens = [], isLoading } = useQuery({
    queryKey: ['calendario', mes],
    queryFn: () => buscarCalendario(mes),
  })

  const inicioMes = startOfMonth(parseISO(`${mes}-01`))
  const fimMes = endOfMonth(inicioMes)
  const dias = eachDayOfInterval({ start: inicioMes, end: fimMes })
  const offsetInicial = getDay(inicioMes)

  const porDia = useMemo(() => {
    const mapa = new Map<string, Transacao[]>()
    itens.forEach(t => {
      const chave = t.dataVencimento ?? t.data
      if (!mapa.has(chave)) mapa.set(chave, [])
      mapa.get(chave)!.push(t)
    })
    return mapa
  }, [itens])

  const itensDoDia = diaSelecionado ? (porDia.get(format(diaSelecionado, 'yyyy-MM-dd')) ?? []) : []

  return (
    <div className="p-6 space-y-5">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <h1 className="text-2xl font-bold text-conteudo">Calendário</h1>
        <SeletorMes />
      </div>

      {isLoading ? (
        <div className="flex justify-center py-12">
          <Spinner />
        </div>
      ) : (
        <>
          <div className="card">
            <div className="grid grid-cols-7 gap-1 mb-2">
              {DIAS_SEMANA.map((d, i) => (
                <div key={i} className="text-center text-xs font-semibold text-conteudo-suave uppercase py-1">{d}</div>
              ))}
            </div>
            <div className="grid grid-cols-7 gap-1">
              {Array.from({ length: offsetInicial }).map((_, i) => <div key={`vazio-${i}`} />)}
              {dias.map(dia => {
                const chave = format(dia, 'yyyy-MM-dd')
                const eventos = porDia.get(chave) ?? []
                const totalDespesa = eventos.filter(e => e.tipo === 'DESPESA' && e.status !== 'CANCELADA').reduce((s, e) => s + e.valor, 0)
                const totalReceita = eventos.filter(e => e.tipo === 'RECEITA' && e.status !== 'CANCELADA').reduce((s, e) => s + e.valor, 0)
                const temAtrasada = eventos.some(e => e.status === 'ATRASADA')
                const selecionado = diaSelecionado && isSameDay(dia, diaSelecionado)

                return (
                  <button
                    key={chave}
                    onClick={() => setDiaSelecionado(selecionado ? null : dia)}
                    className={`aspect-square rounded-lg p-1 flex flex-col items-center justify-start text-left transition-colors border
                      ${selecionado ? 'border-acento bg-acento/10' : 'border-transparent hover:bg-superficie-2'}`}
                  >
                    <span className={`text-xs mb-0.5 ${isToday(dia) ? 'w-5 h-5 rounded-full bg-acento text-white flex items-center justify-center' : 'text-conteudo-suave'}`}>
                      {format(dia, 'd')}
                    </span>
                    <div className="flex flex-col items-center gap-0.5 w-full">
                      {totalDespesa > 0 && (
                        <span className={`text-[10px] leading-none px-1 rounded-full ${temAtrasada ? 'bg-red-500/20 text-red-500' : 'bg-superficie-2 text-conteudo-suave'} truncate w-full text-center`}>
                          -{fmt(totalDespesa)}
                        </span>
                      )}
                      {totalReceita > 0 && (
                        <span className="text-[10px] leading-none px-1 rounded-full bg-emerald-500/15 text-emerald-500 truncate w-full text-center">
                          +{fmt(totalReceita)}
                        </span>
                      )}
                    </div>
                  </button>
                )
              })}
            </div>
          </div>

          {diaSelecionado && (
            <div>
              <p className="text-xs font-semibold text-conteudo-suave uppercase tracking-wider mb-2">
                {format(diaSelecionado, "EEEE, dd 'de' MMMM", { locale: ptBR })}
              </p>
              {itensDoDia.length === 0 ? (
                <div className="card text-center py-8 text-conteudo-suave text-sm">Nada agendado neste dia.</div>
              ) : (
                <div className="card p-0 overflow-hidden divide-y divide-borda">
                  {itensDoDia.map(t => (
                    <div key={t.id} className="flex items-center gap-4 px-5 py-3.5">
                      <div className="w-3 h-3 rounded-full shrink-0"
                        style={{ background: t.tipo === 'TRANSFERENCIA' ? '#3b82f6' : t.categoria?.cor ?? '#6b7280' }} />
                      <div className="flex-1 min-w-0">
                        <div className="flex items-center gap-2">
                          <span className="text-sm font-medium text-conteudo truncate">
                            {t.descricao || t.categoria?.nome || (t.tipo === 'TRANSFERENCIA' ? 'Transferência' : '—')}
                          </span>
                          <span className={`text-xs px-1.5 py-0.5 rounded-full font-medium ${corStatus[t.status]}`}>
                            {rotuloStatus[t.status]}
                          </span>
                        </div>
                        <div className="flex items-center gap-2 mt-0.5">
                          <span className="text-xs text-conteudo-suave">{t.conta.nome}</span>
                          {t.tipo === 'TRANSFERENCIA' ? <ArrowRightLeft size={11} className="text-conteudo-suave" />
                            : t.tipoPagamento === 'CREDITO' ? <CreditCard size={11} className="text-conteudo-suave" />
                            : <Banknote size={11} className="text-conteudo-suave" />}
                        </div>
                      </div>
                      <span className={`text-sm font-bold ${t.tipo === 'RECEITA' ? 'text-emerald-500' : t.tipo === 'TRANSFERENCIA' ? 'text-blue-500' : 'text-red-500'}`}>
                        {t.tipo === 'DESPESA' ? '-' : '+'}{fmt(t.valor)}
                      </span>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </>
      )}
    </div>
  )
}
