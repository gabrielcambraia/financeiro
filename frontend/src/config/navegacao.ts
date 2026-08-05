import { LayoutDashboard, ArrowLeftRight, Wallet, Tags, CreditCard, Landmark, PiggyBank, Target, Calendar, HandCoins, TrendingUp, LineChart, Building2, Users } from 'lucide-react'

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
  { to: '/entidades', icon: Users, label: 'Entidades' },
]

const itensAdmin = [
  { to: '/admin/bancos', icon: Landmark, label: 'Bancos' },
  { to: '/admin/espacos', icon: Building2, label: 'Espaços' },
]

export const itensNavegacao = (admin: boolean) =>
  admin ? [...itensBase, ...itensAdmin] : itensBase

// Bottom nav mobile: itens admin ficam de fora — a barra é flex-1 e já
// está no limite (11 itens); admin continua acessível pela sidebar/URL.
export const itensNavegacaoInferior = () => itensBase
