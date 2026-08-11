import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, AreaChart, Area, Legend
} from 'recharts'
import { TrendingUp, TrendingDown, Wallet, ArrowLeftRight, ArrowUp, ArrowDown, Minus, CheckCircle2 } from 'lucide-react'
import { format, parseISO } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { Link } from 'react-router-dom'
import { buscarPainel } from '../api/painel'
import { useLojaFiltro } from '../store/lojaFiltro'
import Spinner from '../components/Spinner'
import SeletorMes from '../components/SeletorMes'
import { buscarContas } from '../api/contas'
import { useLojaTema } from '../store/lojaTema'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import { primeiroNome, saudacaoPorHora } from '../utils/formatadores'
import { obterTokenCor } from '../utils/cores'
import type { GrupoVencimento, ItemVencimento } from '../types'

const coresGrafico = (tema: 'claro' | 'escuro') =>
  tema === 'escuro'
    ? { grade: '#1e2c38', eixo: '#94A3B8', tooltipFundo: '#12151c', tooltipBorda: '#26293a' }
    : { grade: '#e2ded5', eixo: '#5c6473', tooltipFundo: '#ffffff', tooltipBorda: '#e2ded5' }

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const RADIAN = Math.PI / 180
const renderCustomLabel = ({ cx, cy, midAngle, innerRadius, outerRadius, percent }: any) => {
  if (percent < 0.05) return null
  const r = innerRadius + (outerRadius - innerRadius) * 0.5
  return (
    <text x={cx + r * Math.cos(-midAngle * RADIAN)} y={cy + r * Math.sin(-midAngle * RADIAN)}
      fill="white" textAnchor="middle" dominantBaseline="central" fontSize={11} fontWeight={600}>
      {`${(percent * 100).toFixed(0)}%`}
    </text>
  )
}

const GRUPO_VAZIO: GrupoVencimento = {
  totalVencido: 0,
  quantidadeVencida: 0,
  vencidas: [],
  totalAVencer: 0,
  quantidadeAVencer: 0,
  aVencer: [],
}

type ItemVencimentoComTipo = ItemVencimento & { tipo: 'pagar' | 'receber' }

export default function Painel() {
  const { mes, contaId, definirContaId } = useLojaFiltro()
  const tema = useLojaTema(s => s.tema)
  const cores = coresGrafico(tema)
  const sessao = useLojaAutenticacao(s => s.sessao)
  const [abaVencimento, setAbaVencimento] = useState<'todos' | 'avencer' | 'vencidas'>('todos')

  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })
  const { data, isLoading } = useQuery({
    queryKey: ['painel', mes, contaId],
    queryFn: () => buscarPainel(mes, contaId),
  })

  if (isLoading || !data) {
    return (
      <div className="flex items-center justify-center h-full">
        <Spinner tamanho="lg" />
      </div>
    )
  }

  const tendenciaMensalFormatada = data.tendenciaMensal.map(m => ({
    ...m,
    label: format(parseISO(`${m.mes}-01`), 'MMM', { locale: ptBR }),
  }))

  const ultimos = data.tendenciaMensal
  const mesAtual = ultimos[ultimos.length - 1]
  const mesAnterior = ultimos[ultimos.length - 2]
  const calcVariacao = (atual: number, anterior: number) =>
    anterior === 0 ? null : ((atual - anterior) / Math.abs(anterior)) * 100

  const variacaoReceita = mesAnterior ? calcVariacao(mesAtual?.receita ?? 0, mesAnterior.receita) : null
  const variacaoDespesa = mesAnterior ? calcVariacao(mesAtual?.despesa ?? 0, mesAnterior.despesa) : null
  const saldoTotal = data.saldosContas.reduce((s, c) => s + c.saldo, 0)

  const cartoesResumo = [
    { label: 'Saldo Total',   value: saldoTotal,          icon: Wallet,        iconColor: 'text-acento',  variacao: null,            positivo: true },
    { label: 'Receitas',      value: data.totalReceitas,  icon: TrendingUp,    iconColor: 'text-sucesso', variacao: variacaoReceita,  positivo: true },
    { label: 'Despesas',      value: data.totalDespesas,  icon: TrendingDown,  iconColor: 'text-perigo',  variacao: variacaoDespesa,  positivo: false },
    { label: 'Resultado',     value: data.saldoLiquido,   icon: ArrowLeftRight,iconColor: 'text-acento',  variacao: null,             positivo: true },
  ]

  // Vencimentos
  const aPagar = data.vencimentos?.aPagar ?? GRUPO_VAZIO
  const aReceber = data.vencimentos?.aReceber ?? GRUPO_VAZIO

  const itensAVencer: ItemVencimentoComTipo[] = [
    ...aPagar.aVencer.map(i => ({ ...i, tipo: 'pagar' as const })),
    ...aReceber.aVencer.map(i => ({ ...i, tipo: 'receber' as const })),
  ]
  const itensVencidas: ItemVencimentoComTipo[] = [
    ...aPagar.vencidas.map(i => ({ ...i, tipo: 'pagar' as const })),
    ...aReceber.vencidas.map(i => ({ ...i, tipo: 'receber' as const })),
  ]
  const itensTodos: ItemVencimentoComTipo[] = [...itensVencidas, ...itensAVencer]

  const itensVencimentoExibidos =
    abaVencimento === 'avencer' ? itensAVencer
    : abaVencimento === 'vencidas' ? itensVencidas
    : itensTodos

  return (
    <div className="p-6 space-y-6">
      {/* Cabeçalho */}
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <div>
          <h1 className="text-2xl font-bold text-conteudo">
            {saudacaoPorHora()}
            {sessao && <>, <span className="text-acento">{primeiroNome(sessao.nome)}</span></>}
          </h1>
          <p className="text-sm text-conteudo-suave mt-0.5">Bem-vindo(a) de volta ao seu app de gestão financeira.</p>
        </div>
        <div className="flex flex-col gap-3 md:flex-row md:items-center">
          <select
            className="select w-full md:w-44"
            value={contaId ?? ''}
            onChange={e => definirContaId(e.target.value ? Number(e.target.value) : undefined)}
          >
            <option value="">Todas as contas</option>
            {contas.map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
          </select>
          <SeletorMes />
        </div>
      </div>

      {/* KPIs */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 md:gap-4">
        {cartoesResumo.map(({ label, value, icon: Icon, iconColor, variacao, positivo }) => {
          const delta = variacao !== null && variacao !== undefined
          const sobiu = (variacao ?? 0) > 0
          const deltaPositivo = positivo ? sobiu : !sobiu
          return (
            <div key={label} className="card flex flex-col gap-3">
              <div className="flex items-start justify-between gap-2">
                <div className={`${iconColor} bg-superficie-2 w-9 h-9 rounded-xl flex items-center justify-center shrink-0`}>
                  <Icon size={18} />
                </div>
                {delta && (
                  <span className={`flex items-center gap-0.5 text-xs font-semibold ${deltaPositivo ? 'text-sucesso' : 'text-perigo'}`}>
                    {sobiu ? <ArrowUp size={12} /> : variacao === 0 ? <Minus size={12} /> : <ArrowDown size={12} />}
                    {Math.abs(variacao!).toFixed(1)}%
                  </span>
                )}
              </div>
              <div className="min-w-0">
                <p className="text-xs font-medium text-conteudo-suave mb-1 truncate">{label}</p>
                <p className={`text-lg md:text-xl font-bold truncate ${value < 0 ? 'text-perigo' : 'text-conteudo'}`}>{fmt(value)}</p>
              </div>
            </div>
          )
        })}
      </div>

      {/* Fluxo de Caixa + Contas */}
      <div className="grid grid-cols-1 lg:grid-cols-[1.6fr_1fr] gap-4">
        {/* Fluxo de Caixa - AreaChart */}
        <div className="card">
          <div className="mb-4">
            <h3 className="text-sm font-semibold text-conteudo">Fluxo de Caixa</h3>
            <p className="text-xs text-conteudo-suave mt-0.5">12 meses</p>
          </div>
          <ResponsiveContainer width="100%" height={200}>
            <AreaChart data={tendenciaMensalFormatada}>
              <defs>
                <linearGradient id="gradReceita" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={obterTokenCor('acento')} stopOpacity={0.25} />
                  <stop offset="95%" stopColor={obterTokenCor('acento')} stopOpacity={0} />
                </linearGradient>
                <linearGradient id="gradDespesa" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={obterTokenCor('perigo')} stopOpacity={0.25} />
                  <stop offset="95%" stopColor={obterTokenCor('perigo')} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke={cores.grade} />
              <XAxis dataKey="label" tick={{ fill: cores.eixo, fontSize: 11 }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fill: cores.eixo, fontSize: 10 }} axisLine={false} tickLine={false}
                tickFormatter={v => `R$${(v / 1000).toFixed(0)}k`} />
              <Tooltip
                contentStyle={{ background: cores.tooltipFundo, border: `1px solid ${cores.tooltipBorda}`, borderRadius: 8 }}
                formatter={(v) => fmt(Number(v))}
              />
              <Legend wrapperStyle={{ fontSize: 12, color: cores.eixo }} />
              <Area type="monotone" dataKey="receita" name="Receitas"
                stroke={obterTokenCor('acento')} strokeWidth={2} fill="url(#gradReceita)" />
              <Area type="monotone" dataKey="despesa" name="Despesas"
                stroke={obterTokenCor('perigo')} strokeWidth={2} fill="url(#gradDespesa)" />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        {/* Contas */}
        <div className="card">
          <div className="flex items-center justify-between mb-4">
            <h3 className="text-sm font-semibold text-conteudo">Contas</h3>
            <Link to="/contas" className="text-xs text-acento hover:underline">Gerenciar</Link>
          </div>
          <div className="space-y-0.5">
            {data.saldosContas.length === 0 ? (
              <p className="text-conteudo-suave text-sm text-center py-4">Nenhuma conta cadastrada</p>
            ) : (
              data.saldosContas.map(({ conta, saldo }) => (
                <div key={conta.id} className="flex items-center gap-2.5 hover:bg-superficie-2 rounded-xl p-2.5 transition-colors">
                  <div className="w-2.5 h-2.5 rounded-full shrink-0" style={{ background: conta.cor }} />
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-conteudo truncate">{conta.nome}</p>
                    <p className="text-xs text-conteudo-suave">{conta.tipo}</p>
                  </div>
                  <span className={`text-sm font-semibold shrink-0 ${saldo >= 0 ? 'text-sucesso' : 'text-perigo'}`}>
                    {fmt(saldo)}
                  </span>
                </div>
              ))
            )}
          </div>
          {data.saldosContas.length > 0 && (
            <div className="mt-3 pt-3 border-t border-borda flex items-center justify-between">
              <span className="text-xs text-conteudo-suave font-medium">Total consolidado</span>
              <span className={`text-sm font-bold ${saldoTotal >= 0 ? 'text-sucesso' : 'text-perigo'}`}>{fmt(saldoTotal)}</span>
            </div>
          )}
        </div>
      </div>

      {/* Vencimentos */}
      <div className="card">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-3 mb-4">
          <h3 className="text-sm font-semibold text-conteudo">Vencimentos</h3>
          <div className="flex items-center gap-2">
            <div className="flex gap-1">
              {(['todos', 'avencer', 'vencidas'] as const).map(aba => (
                <button
                  key={aba}
                  onClick={() => setAbaVencimento(aba)}
                  className={`text-xs px-3 py-1 rounded-full font-medium transition-colors ${
                    abaVencimento === aba
                      ? 'bg-acento text-white'
                      : 'bg-superficie-2 text-conteudo-suave hover:text-conteudo'
                  }`}
                >
                  {aba === 'todos' ? 'Todos' : aba === 'avencer' ? 'A vencer' : 'Vencidas'}
                </button>
              ))}
            </div>
            <Link to="/transacoes" className="text-xs text-acento hover:underline shrink-0">Ver todos</Link>
          </div>
        </div>

        {itensVencimentoExibidos.length === 0 ? (
          <div className="text-center text-conteudo-suave py-8 text-sm flex flex-col items-center gap-2">
            <CheckCircle2 size={24} className="text-sucesso" />
            Nenhum item para exibir
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2">
            {itensVencimentoExibidos.map((item, idx) => {
              const eVencida = abaVencimento === 'vencidas' || (abaVencimento === 'todos' && item.diasEmRelacaoAHoje < 0)
              const eReceber = item.tipo === 'receber'
              return (
                <div
                  key={`${item.tipo}-${item.id}-${idx}`}
                  className={`flex items-center gap-3 p-3 rounded-xl border ${
                    eVencida
                      ? 'border-perigo/30 bg-perigo-fundo'
                      : 'border-borda bg-superficie'
                  }`}
                >
                  <div className="flex-1 min-w-0">
                    <p className="text-sm text-conteudo font-medium truncate">{item.descricao}</p>
                    <p className="text-xs text-conteudo-suave truncate">{item.contaNome}</p>
                  </div>
                  <div className="flex flex-col items-end gap-1 shrink-0">
                    <span className={`text-sm font-bold ${eVencida ? 'text-perigo' : eReceber ? 'text-sucesso' : 'text-conteudo'}`}>
                      {fmt(item.valor)}
                    </span>
                    {eVencida ? (
                      <span className="bg-perigo text-white text-xs px-2 py-0.5 rounded-full">Vencida</span>
                    ) : item.diasEmRelacaoAHoje === 0 ? (
                      <span className="bg-aviso text-white text-xs px-2 py-0.5 rounded-full">Hoje</span>
                    ) : eReceber ? (
                      <span className="bg-sucesso-fundo text-sucesso text-xs px-2 py-0.5 rounded-full">{item.diasEmRelacaoAHoje}d</span>
                    ) : (
                      <span className="bg-superficie-2 text-conteudo-suave border border-borda text-xs px-2 py-0.5 rounded-full">{item.diasEmRelacaoAHoje}d</span>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        )}
      </div>

      {/* Receitas x Despesas + Despesas por Categoria */}
      <div className="grid grid-cols-1 lg:grid-cols-[1.6fr_1fr] gap-4">
        {/* BarChart - Receitas x Despesas */}
        <div className="card">
          <div className="mb-4">
            <h3 className="text-sm font-semibold text-conteudo">Receitas × Despesas</h3>
            <p className="text-xs text-conteudo-suave mt-0.5">12 meses</p>
          </div>
          <ResponsiveContainer width="100%" height={200}>
            <BarChart data={tendenciaMensalFormatada} barGap={4}>
              <CartesianGrid strokeDasharray="3 3" stroke={cores.grade} />
              <XAxis dataKey="label" tick={{ fill: cores.eixo, fontSize: 11 }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fill: cores.eixo, fontSize: 10 }} axisLine={false} tickLine={false}
                tickFormatter={v => `R$${(v / 1000).toFixed(0)}k`} />
              <Tooltip
                contentStyle={{ background: cores.tooltipFundo, border: `1px solid ${cores.tooltipBorda}`, borderRadius: 8 }}
                formatter={(v) => fmt(Number(v))}
              />
              <Legend wrapperStyle={{ fontSize: 12, color: cores.eixo }} />
              <Bar dataKey="receita" name="Receitas" fill={obterTokenCor('sucesso')} radius={[4, 4, 0, 0]} maxBarSize={40} />
              <Bar dataKey="despesa" name="Despesas" fill={obterTokenCor('perigo')} radius={[4, 4, 0, 0]} maxBarSize={40} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* PieChart - Despesas por Categoria */}
        <div className="card">
          <h3 className="text-sm font-semibold text-conteudo mb-4">Despesas por Categoria</h3>
          {data.despesasPorCategoria.length === 0 ? (
            <div className="flex items-center justify-center h-[200px] text-conteudo-suave text-sm">
              Sem despesas no mês
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={200}>
              <PieChart>
                <Pie
                  data={data.despesasPorCategoria}
                  dataKey="total"
                  nameKey="categoria.nome"
                  cx="50%" cy="50%"
                  innerRadius={50} outerRadius={85}
                  labelLine={false}
                  label={renderCustomLabel}
                >
                  {data.despesasPorCategoria.map((entry, i) => (
                    <Cell key={i} fill={entry.categoria.cor} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{ background: cores.tooltipFundo, border: `1px solid ${cores.tooltipBorda}`, borderRadius: 8 }}
                  formatter={(v) => fmt(Number(v))}
                />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* Detalhamento por categoria */}
      {data.despesasPorCategoria.length > 0 && (
        <div className="card">
          <h3 className="text-sm font-semibold text-conteudo mb-4">Detalhamento por categoria</h3>
          <div className="space-y-1">
            {data.despesasPorCategoria.map(({ categoria, total, percentual }) => (
              <div key={categoria.id}
                className="flex items-center gap-3 px-3 py-2 rounded-xl transition-colors hover:bg-superficie-2 group cursor-default">
                <div className="w-2.5 h-2.5 rounded-full shrink-0 transition-transform group-hover:scale-125"
                  style={{ background: categoria.cor }} />
                <span className="text-sm text-conteudo-suave w-24 md:w-32 truncate group-hover:text-conteudo transition-colors">
                  {categoria.nome}
                </span>
                <div className="flex-1 bg-superficie-2 rounded-full h-2">
                  <div className="h-2 rounded-full transition-all group-hover:brightness-125"
                    style={{ width: `${percentual}%`, background: categoria.cor }} />
                </div>
                <span className="text-sm text-conteudo w-14 md:w-20 text-right font-medium transition-colors">
                  {fmt(total)}
                </span>
                <span className="hidden sm:block text-xs text-conteudo-suave w-10 text-right transition-colors">
                  {percentual.toFixed(0)}%
                </span>
              </div>
            ))}
          </div>
        </div>
      )}

    </div>
  )
}
