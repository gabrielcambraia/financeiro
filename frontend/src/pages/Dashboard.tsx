import { useQuery } from '@tanstack/react-query'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer,
  PieChart, Pie, Cell, AreaChart, Area, Legend
} from 'recharts'
import { TrendingUp, TrendingDown, Wallet, ArrowLeftRight } from 'lucide-react'
import { format, parseISO } from 'date-fns'
import { ptBR } from 'date-fns/locale'
import { getDashboard } from '../api/dashboard'
import { useFilterStore } from '../store/filterStore'
import MonthPicker from '../components/MonthPicker'
import { getAccounts } from '../api/accounts'

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

export default function Dashboard() {
  const { month, accountId, setAccountId } = useFilterStore()
  const { data: accounts = [] } = useQuery({ queryKey: ['accounts'], queryFn: getAccounts })
  const { data, isLoading } = useQuery({
    queryKey: ['dashboard', month, accountId],
    queryFn: () => getDashboard(month, accountId),
  })

  if (isLoading || !data) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="animate-spin rounded-full h-10 w-10 border-b-2 border-indigo-500" />
      </div>
    )
  }

  const monthlyTrendFormatted = data.monthlyTrend.map(m => ({
    ...m,
    label: format(parseISO(`${m.month}-01`), 'MMM', { locale: ptBR }),
  }))

  const summaryCards = [
    {
      label: 'Receitas', value: data.totalIncome, icon: TrendingUp,
      color: 'text-emerald-400', bg: 'bg-emerald-400/10'
    },
    {
      label: 'Despesas', value: data.totalExpense, icon: TrendingDown,
      color: 'text-red-400', bg: 'bg-red-400/10'
    },
    {
      label: 'Saldo do Mês', value: data.netBalance, icon: ArrowLeftRight,
      color: data.netBalance >= 0 ? 'text-indigo-400' : 'text-orange-400',
      bg: data.netBalance >= 0 ? 'bg-indigo-400/10' : 'bg-orange-400/10'
    },
    {
      label: 'Saldo Total', value: data.accountBalances.reduce((s, a) => s + a.balance, 0),
      icon: Wallet, color: 'text-blue-400', bg: 'bg-blue-400/10'
    },
  ]

  return (
    <div className="p-6 space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold text-white">Dashboard</h1>
        <div className="flex items-center gap-3">
          <select
            className="select w-44"
            value={accountId ?? ''}
            onChange={e => setAccountId(e.target.value ? Number(e.target.value) : undefined)}
          >
            <option value="">Todas as contas</option>
            {accounts.map(a => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
          <MonthPicker />
        </div>
      </div>

      {/* Summary Cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        {summaryCards.map(({ label, value, icon: Icon, color, bg }) => (
          <div key={label} className="card flex items-start gap-4">
            <div className={`${bg} ${color} p-3 rounded-xl`}>
              <Icon size={20} />
            </div>
            <div>
              <p className="text-xs text-gray-500 mb-0.5">{label}</p>
              <p className={`text-xl font-bold ${color}`}>{fmt(value)}</p>
            </div>
          </div>
        ))}
      </div>

      {/* Charts Row 1 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Bar Chart - Monthly Trend */}
        <div className="card lg:col-span-2">
          <h3 className="text-sm font-semibold text-gray-300 mb-4">Receitas vs Despesas (6 meses)</h3>
          <ResponsiveContainer width="100%" height={220}>
            <BarChart data={monthlyTrendFormatted} barGap={4}>
              <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
              <XAxis dataKey="label" tick={{ fill: '#6b7280', fontSize: 12 }} axisLine={false} tickLine={false} />
              <YAxis tick={{ fill: '#6b7280', fontSize: 11 }} axisLine={false} tickLine={false}
                tickFormatter={v => `R$${(v / 1000).toFixed(0)}k`} />
              <Tooltip
                contentStyle={{ background: '#111827', border: '1px solid #374151', borderRadius: 8 }}
                formatter={(v) => fmt(Number(v))}
              />
              <Legend wrapperStyle={{ fontSize: 12, color: '#9ca3af' }} />
              <Bar dataKey="income" name="Receitas" fill="#10b981" radius={[4, 4, 0, 0]} maxBarSize={40} />
              <Bar dataKey="expense" name="Despesas" fill="#ef4444" radius={[4, 4, 0, 0]} maxBarSize={40} />
            </BarChart>
          </ResponsiveContainer>
        </div>

        {/* Pie Chart - Expenses by Category */}
        <div className="card">
          <h3 className="text-sm font-semibold text-gray-300 mb-4">Despesas por categoria</h3>
          {data.expensesByCategory.length === 0 ? (
            <div className="flex items-center justify-center h-[220px] text-gray-600 text-sm">
              Sem despesas no mês
            </div>
          ) : (
            <ResponsiveContainer width="100%" height={220}>
              <PieChart>
                <Pie
                  data={data.expensesByCategory}
                  dataKey="total"
                  nameKey="category.name"
                  cx="50%" cy="50%"
                  innerRadius={55} outerRadius={90}
                  labelLine={false}
                  label={renderCustomLabel}
                >
                  {data.expensesByCategory.map((entry, i) => (
                    <Cell key={i} fill={entry.category.color} />
                  ))}
                </Pie>
                <Tooltip
                  contentStyle={{ background: '#111827', border: '1px solid #374151', borderRadius: 8 }}
                  formatter={(v) => fmt(Number(v))}
                />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>

      {/* Charts Row 2 */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
        {/* Area Chart - Daily Balance */}
        <div className="card lg:col-span-2">
          <h3 className="text-sm font-semibold text-gray-300 mb-4">Evolução do saldo no mês</h3>
          <ResponsiveContainer width="100%" height={180}>
            <AreaChart data={data.dailyBalance}>
              <defs>
                <linearGradient id="balGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor="#6366f1" stopOpacity={0.3} />
                  <stop offset="95%" stopColor="#6366f1" stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#1f2937" />
              <XAxis dataKey="date" tick={{ fill: '#6b7280', fontSize: 10 }} axisLine={false} tickLine={false}
                tickFormatter={d => d.slice(8)} interval="preserveStartEnd" />
              <YAxis tick={{ fill: '#6b7280', fontSize: 10 }} axisLine={false} tickLine={false}
                tickFormatter={v => `${(v / 1000).toFixed(0)}k`} />
              <Tooltip
                contentStyle={{ background: '#111827', border: '1px solid #374151', borderRadius: 8 }}
                formatter={(v) => fmt(Number(v))}
                labelFormatter={l => `Dia ${l.slice(8)}`}
              />
              <Area type="monotone" dataKey="balance" stroke="#6366f1" strokeWidth={2}
                fill="url(#balGrad)" />
            </AreaChart>
          </ResponsiveContainer>
        </div>

        {/* Account Balances */}
        <div className="card">
          <h3 className="text-sm font-semibold text-gray-300 mb-4">Saldo por conta</h3>
          <div className="space-y-3">
            {data.accountBalances.length === 0 ? (
              <p className="text-gray-600 text-sm text-center py-4">Nenhuma conta cadastrada</p>
            ) : (
              data.accountBalances.map(({ account, balance }) => (
                <div key={account.id} className="flex items-center justify-between p-3 rounded-xl bg-gray-800">
                  <div className="flex items-center gap-2">
                    <div className="w-3 h-3 rounded-full" style={{ background: account.color }} />
                    <span className="text-sm text-gray-300">{account.name}</span>
                  </div>
                  <span className={`text-sm font-semibold ${balance >= 0 ? 'text-emerald-400' : 'text-red-400'}`}>
                    {fmt(balance)}
                  </span>
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* Category breakdown */}
      {data.expensesByCategory.length > 0 && (
        <div className="card">
          <h3 className="text-sm font-semibold text-gray-300 mb-4">Detalhamento por categoria</h3>
          <div className="space-y-2">
            {data.expensesByCategory.map(({ category, total, percentage }) => (
              <div key={category.id} className="flex items-center gap-3">
                <div className="w-2 h-2 rounded-full shrink-0" style={{ background: category.color }} />
                <span className="text-sm text-gray-400 w-32 truncate">{category.name}</span>
                <div className="flex-1 bg-gray-800 rounded-full h-2">
                  <div className="h-2 rounded-full transition-all" style={{ width: `${percentage}%`, background: category.color }} />
                </div>
                <span className="text-sm text-gray-300 w-20 text-right">{fmt(total)}</span>
                <span className="text-xs text-gray-500 w-10 text-right">{percentage.toFixed(0)}%</span>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  )
}
