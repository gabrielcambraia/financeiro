import { useQuery } from '@tanstack/react-query'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, AreaChart, Area, Legend
} from 'recharts'
import { TrendingUp, TrendingDown, Wallet, ArrowLeftRight, CheckCircle2, Clock } from 'lucide-react'
import { format, parseISO } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { buscarPainel } from '../api/painel'
import { useLojaFiltro } from '../store/lojaFiltro'
import SeletorMes from '../components/SeletorMes'
import { buscarContas } from '../api/contas'
import { useLojaTema } from '../store/lojaTema'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import { primeiroNome, saudacaoPorHora } from '../utils/formatadores'

const coresGrafico = (tema: 'claro' | 'escuro') =>
  tema === 'escuro'
    ? { grade: '#1f2937', eixo: '#6b7280', tooltipFundo: '#111827', tooltipBorda: '#374151', linha: '#4472d4' }
    : { grade: '#e2e8f0', eixo: '#64748b', tooltipFundo: '#ffffff', tooltipBorda: '#e2e8f0', linha: '#123a75' }

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

export default function Painel() {
  const { mes, contaId, definirContaId } = useLojaFiltro()
  const tema = useLojaTema(s => s.tema)
  const cores = coresGrafico(tema)
  const sessao = useLojaAutenticacao(s => s.sessao)
  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })
  const { data, isLoading } = useQuery({
    queryKey: ['painel', mes, contaId],
    queryFn: () => buscarPainel(mes, contaId),
  })

  if (isLoading || !data) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-acento" />
      </div>
    )
  }

  const tendenciaMensalFormatada = data.tendenciaMensal.map(m => ({
    ...m,
    label: format(parseISO(`${m.mes}-01`), 'MMM', { locale: ptBR }),
  }))

  const cartoesResumo = [
    {
      label: 'Receitas', value: data.totalReceitas, icon: TrendingUp,
      color: 'text-pastel-verde-texto', bg: 'bg-pastel-verde'
    },
    {
      label: 'Despesas', value: data.totalDespesas, icon: TrendingDown,
      color: 'text-pastel-vermelho-texto', bg: 'bg-pastel-vermelho'
    },
    {
      label: 'Saldo do Mês', value: data.saldoLiquido, icon: ArrowLeftRight,
      color: data.saldoLiquido >= 0 ? 'text-pastel-lilas-texto' : 'text-pastel-bege-texto',
      bg: data.saldoLiquido >= 0 ? 'bg-pastel-lilas' : 'bg-pastel-bege'
    },
    {
      label: 'Saldo Total', value: data.saldosContas.reduce((s, c) => s + c.saldo, 0),
      icon: Wallet, color: 'text-pastel-azul-texto', bg: 'bg-pastel-azul'
    },
  ]

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

      {/* Cartões resumo */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-3 md:gap-4">
        {cartoesResumo.map(({ label, value, icon: Icon, color, bg }) => (
          <div key={label} className={`${bg} rounded-2xl p-4 md:p-5 flex flex-col gap-3`}>
            <div className={`${color} bg-white/50 dark:bg-white/10 w-9 h-9 md:w-10 md:h-10 rounded-xl flex items-center justify-center shrink-0`}>
              <Icon size={18} />
            </div>
            <div className="min-w-0">
              <p className={`text-xs font-medium ${color} opacity-80 mb-1 truncate`}>{label}</p>
              <p className={`text-lg md:text-2xl font-bold ${color} truncate`}>{fmt(value)}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Realizado vs A vencer */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-4">
        {[
          {
            label: 'Realizado',
            subtitle: 'Transações até hoje',
            icon: CheckCircle2,
            accent: 'text-emerald-500',
            border: 'border-emerald-400/20',
            data: data.realizado,
          },
          {
            label: 'A vencer',
            subtitle: 'Transações futuras no mês',
            icon: Clock,
            accent: 'text-amber-500',
            border: 'border-amber-400/20',
            data: data.pendente,
          },
        ].map(({ label, subtitle, icon: Icon, accent, border, data: fluxo }) => (
          <div key={label} className={`card border ${border}`}>
            <div className="flex items-center gap-2 mb-4">
              <Icon size={16} className={accent} />
              <div>
                <p className="text-sm font-semibold text-conteudo">{label}</p>
                <p className="text-xs text-conteudo-suave">{subtitle}</p>
              </div>
            </div>
            <div className="space-y-2">
              <div className="flex justify-between items-center">
                <span className="text-xs text-conteudo-suave flex items-center gap-1.5">
                  <TrendingUp size={12} className="text-emerald-500" /> Receitas
                </span>
                <span className="text-sm font-medium text-emerald-500">{fmt(fluxo.receita)}</span>
              </div>
              <div className="flex justify-between items-center">
                <span className="text-xs text-conteudo-suave flex items-center gap-1.5">
                  <TrendingDown size={12} className="text-red-500" /> Despesas
                </span>
                <span className="text-sm font-medium text-red-500">{fmt(fluxo.despesa)}</span>
              </div>
              <div className="border-t border-borda pt-2 flex justify-between items-center">
                <span className="text-xs text-conteudo-suave font-medium">Saldo</span>
                <span className={`text-base font-bold ${fluxo.saldo >= 0 ? 'text-emerald-500' : 'text-red-500'}`}>
                  {fmt(fluxo.saldo)}
                </span>
              </div>
            </div>
          </div>
        ))}
      </div>

      {/* Gráficos linha 1 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Barras - Tendência mensal */}
        <div className="card lg:col-span-2">
          <h3 className="text-sm font-semibold text-conteudo mb-4">Receitas vs Despesas (6 meses)</h3>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={tendenciaMensalFormatada} barGap={4}>
              <CartesianGrid strokeDasharray="3 3" stroke={cores.grade} />
              <XAxis dataKey="label" tick={{ fill: cores.eixo, fontSize: 12 }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fill: cores.eixo, fontSize: 11 }} axisLine={false} tickLine={false}
                tickFormatter={v => `R$${(v / 1000).toFixed(0)}k`} />
              <Tooltip
                contentStyle={{ background: cores.tooltipFundo, border: `1px solid ${cores.tooltipBorda}`, borderRadius: 8 }}
                formatter={(v) => fmt(Number(v))}
              />
              <Legend wrapperStyle={{ fontSize: 12, color: cores.eixo }} />
              <Bar dataKey="receita" name="Receitas" fill="#10b981" radius={[4, 4, 0, 0]} maxBarSize={40} />
              <Bar dataKey="despesa" name="Despesas" fill="#ef4444" radius={[4, 4, 0, 0]} maxBarSize={40} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Pizza - Despesas por categoria */}
        <div className="card">
          <h3 className="text-sm font-semibold text-conteudo mb-4">Despesas por categoria</h3>
          {data.despesasPorCategoria.length === 0 ? (
            <div className="flex items-center justify-center h-[220px] text-conteudo-suave text-sm">
              Sem despesas no mês
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie
                  data={data.despesasPorCategoria}
                  dataKey="total"
                  nameKey="categoria.nome"
                  cx="50%" cy="50%"
                  innerRadius={55} outerRadius={90}
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

      {/* Gráficos linha 2 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Área - Saldo diário */}
        <div className="card lg:col-span-2">
          <h3 className="text-sm font-semibold text-conteudo mb-4">Evolução do saldo no mês</h3>
          <ResponsiveContainer width="100%" height={180}>
            <AreaChart data={data.saldoDiario}>
              <defs>
                <linearGradient id="balGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={cores.linha} stopOpacity={0.3} />
                  <stop offset="95%" stopColor={cores.linha} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke={cores.grade} />
              <XAxis dataKey="data" tick={{ fill: cores.eixo, fontSize: 10 }} axisLine={false} tickLine={false}
                tickFormatter={d => d.slice(8)} interval="preserveStartEnd" />
              <YAxis tick={{ fill: cores.eixo, fontSize: 10 }} axisLine={false} tickLine={false}
                tickFormatter={v => `${(v / 1000).toFixed(0)}k`} />
              <Tooltip
                contentStyle={{ background: cores.tooltipFundo, border: `1px solid ${cores.tooltipBorda}`, borderRadius: 8 }}
                formatter={(v) => fmt(Number(v))}
                labelFormatter={l => `Dia ${l.slice(8)}`}
              />
              <Area type="monotone" dataKey="saldo" name="Saldo" stroke={cores.linha} strokeWidth={2}
                fill="url(#balGrad)" />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        {/* Saldo por conta */}
        <div className="card">
          <h3 className="text-sm font-semibold text-conteudo mb-4">Saldo por conta</h3>
          <div className="space-y-3">
            {data.saldosContas.length === 0 ? (
              <p className="text-conteudo-suave text-sm text-center py-4">Nenhuma conta cadastrada</p>
            ) : (
              data.saldosContas.map(({ conta, saldo }) => (
                <div key={conta.id} className="flex items-center justify-between p-3 rounded-xl bg-superficie-2">
                  <div className="flex items-center gap-2">
                    <div className="w-3 h-3 rounded-full" style={{ background: conta.cor }} />
                    <span className="text-sm text-conteudo">{conta.nome}</span>
                  </div>
                  <span className={`text-sm font-semibold ${saldo >= 0 ? 'text-emerald-500' : 'text-red-500'}`}>
                    {fmt(saldo)}
                  </span>
                </div>
              ))
            )}
          </div>
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
