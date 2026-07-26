import { LayoutDashboard, ArrowLeftRight, Wallet, Tags, CreditCard, Landmark, PiggyBank, Target, Calendar, HandCoins, TrendingUp, LineChart } from 'lucide-react'

const itensBase = [
  { to: '/', icon: LayoutDashboard, label: 'Painel' },
  { to: '/transacoes', icon: ArrowLeftRight, label: 'Lançamentos' },
  { to: '/calendario', icon: Calendar, label: 'Calendário' },
  { to: '/simulacao', icon: LineChart, label: 'Previsão' },
  { to: '/contas', icon: Wallet, label: 'Contas' },
  { to: '/cartoes', icon: CreditCard, label: 'Cartões' },
  { to: '/orcamentos', icon: PiggyBank, label: 'Orçamento' },
  { to: '/metas', icon: Target, label: 'Metas' },
  { to: '/dividas', icon: HandCoins, label: 'Dívidas' },
  { to: '/investimentos', icon: TrendingUp, label: 'Investimentos' },
  { to: '/categorias', icon: Tags, label: 'Categorias' },
]

const itemAdmin = { to: '/admin/bancos', icon: Landmark, label: 'Bancos' }

export const itensNavegacao = (admin: boolean) =>
  admin ? [...itensBase, itemAdmin] : itensBase
