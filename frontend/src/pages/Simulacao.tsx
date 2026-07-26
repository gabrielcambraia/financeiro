import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { AreaChart, Area, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceLine, Legend } from 'recharts'
import { format } from 'date-fns'
import { AlertTriangle, CheckCircle2 } from 'lucide-react'
import { buscarProjecao } from '../api/projecao'
import { buscarContas } from '../api/contas'
import { useLojaTema } from '../store/lojaTema'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

const coresGrafico = (tema: 'claro' | 'escuro') =>
  tema === 'escuro'
    ? { grade: '#1f2937', eixo: '#6b7280', tooltipFundo: '#111827', tooltipBorda: '#374151', linha: '#4472d4', simulada: '#f59e0b' }
    : { grade: '#e2e8f0', eixo: '#64748b', tooltipFundo: '#ffffff', tooltipBorda: '#e2e8f0', linha: '#123a75', simulada: '#f59e0b' }

const OPCOES_DIAS = [30, 90, 180, 365]

export default function Simulacao() {
  const tema = useLojaTema(s => s.tema)
  const cores = coresGrafico(tema)
  const [dias, setDias] = useState(90)
  const [contaId, setContaId] = useState<string>('')
  const [simValor, setSimValor] = useState('')
  const [simData, setSimData] = useState(format(new Date(), 'yyyy-MM-dd'))
  const [simulando, setSimulando] = useState(false)

  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })

  const { data: projecao, isLoading } = useQuery({
    queryKey: ['projecao', dias, contaId, simulando ? simValor : null, simulando ? simData : null],
    queryFn: () => buscarProjecao({
      dias,
      contaId: contaId ? Number(contaId) : undefined,
      simulacaoValor: simulando && simValor ? Number(simValor) : undefined,
      simulacaoData: simulando && simValor ? simData : undefined,
    }),
  })

  const pontos = projecao?.pontos ?? []
  const menorSaldo = pontos.length ? Math.min(...pontos.map(p => (simulando ? p.saldoSimulado ?? p.saldo : p.saldo))) : 0
  const cabe = menorSaldo >= 0
  const diaAperto = simulando ? pontos.find(p => (p.saldoSimulado ?? p.saldo) < 0) : pontos.find(p => p.saldo < 0)

  const handleSimular = (e: React.FormEvent) => {
    e.preventDefault()
    setSimulando(true)
  }

  return (
    <div className="p-6 space-y-5">
      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <h1 className="text-2xl font-bold text-conteudo">Previsão de saldo</h1>
        <div className="flex items-center gap-2">
          <select className="select w-40" value={contaId} onChange={e => setContaId(e.target.value)}>
            <option value="">Todas as contas</option>
            {contas.map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
          </select>
          <select className="select w-32" value={dias} onChange={e => setDias(Number(e.target.value))}>
            {OPCOES_DIAS.map(d => <option key={d} value={d}>{d} dias</option>)}
          </select>
        </div>
      </div>

      {projecao && (
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="card">
            <p className="text-xs text-conteudo-suave mb-1">Saldo atual</p>
            <p className="text-2xl font-bold text-conteudo">{fmt(projecao.saldoAtual)}</p>
          </div>
          <div className="card md:col-span-2">
            <p className="text-xs text-conteudo-suave mb-1">Nos próximos {dias} dias</p>
            {cabe ? (
              <p className="flex items-center gap-2 text-emerald-500 font-semibold">
                <CheckCircle2 size={18} /> Saldo não fica negativo{simulando ? ' com essa compra' : ''}
              </p>
            ) : (
              <p className="flex items-center gap-2 text-red-500 font-semibold">
                <AlertTriangle size={18} /> Saldo fica negativo{diaAperto ? ` em ${format(new Date(diaAperto.data + 'T00:00'), 'dd/MM/yyyy')}` : ''}
              </p>
            )}
          </div>
        </div>
      )}

      <div className="card">
        <h3 className="text-sm font-semibold text-conteudo mb-4">Evolução projetada</h3>
        {isLoading ? (
          <div className="flex justify-center py-12">
            <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-acento" />
          </div>
        ) : (
          <ResponsiveContainer width="100%" height={280}>
            <AreaChart data={pontos}>
              <defs>
                <linearGradient id="projGrad" x1="0" y1="0" x2="0" y2="1">
                  <stop offset="5%" stopColor={cores.linha} stopOpacity={0.3} />
                  <stop offset="95%" stopColor={cores.linha} stopOpacity={0} />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke={cores.grade} />
              <XAxis dataKey="data" tick={{ fill: cores.eixo, fontSize: 10 }} axisLine={false} tickLine={false}
                tickFormatter={d => d.slice(8) + '/' + d.slice(5, 7)} interval="preserveStartEnd" />
              <YAxis tick={{ fill: cores.eixo, fontSize: 10 }} axisLine={false} tickLine={false}
                tickFormatter={v => `${(v / 1000).toFixed(0)}k`} />
              <Tooltip
                contentStyle={{ background: cores.tooltipFundo, border: `1px solid ${cores.tooltipBorda}`, borderRadius: 8 }}
                formatter={(v) => fmt(Number(v))}
                labelFormatter={l => format(new Date(l + 'T00:00'), 'dd/MM/yyyy')}
              />
              {simulando && <Legend wrapperStyle={{ fontSize: 12 }} />}
              <ReferenceLine y={0} stroke={cores.eixo} strokeDasharray="4 4" />
              <Area type="monotone" dataKey="saldo" name="Sem simulação" stroke={cores.linha} strokeWidth={2} fill="url(#projGrad)" />
              {simulando && (
                <Area type="monotone" dataKey="saldoSimulado" name="Com a compra" stroke={cores.simulada} strokeWidth={2} fill="none" strokeDasharray="5 3" />
              )}
            </AreaChart>
          </ResponsiveContainer>
        )}
      </div>

      <div className="card max-w-md">
        <h3 className="text-sm font-semibold text-conteudo mb-3">Simular uma compra</h3>
        <form onSubmit={handleSimular} className="space-y-3">
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="label">Valor (R$)</label>
              <input className="input" type="number" step="0.01" min="0.01" placeholder="0,00" required
                value={simValor} onChange={e => setSimValor(e.target.value)} />
            </div>
            <div>
              <label className="label">Data</label>
              <input className="input" type="date" required
                value={simData} onChange={e => setSimData(e.target.value)} />
            </div>
          </div>
          <div className="flex gap-2">
            <button type="submit" className="flex-1 btn-primary">Simular</button>
            {simulando && (
              <button type="button" onClick={() => { setSimulando(false); setSimValor('') }}
                className="py-2.5 px-4 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium">
                Limpar
              </button>
            )}
          </div>
        </form>
      </div>
    </div>
  )
}
