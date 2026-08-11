import { LayoutDashboard, ArrowLeftRight, Wallet, Tags, CreditCard, Landmark, PiggyBank, Target, Calendar, HandCoins, TrendingUp, LineChart, Building2, Users } from 'lucide-react'

export const itensPrincipais = [
  { to: '/', icon: LayoutDashboard, label: 'Painel' },
  { to: '/transacoes', icon: ArrowLeftRight, label: 'Lançamentos' },
  { to: '/contas', icon: Wallet, label: 'Contas' },
  { to: '/cartoes', icon: CreditCard, label: 'Cartões' },
]

export const itensSecundarios = [
  { to: '/calendario', icon: Calendar, label: 'Calendário' },
  { to: '/simulacao', icon: LineChart, label: 'Previsão' },
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
  admin
    ? [...itensPrincipais, ...itensSecundarios, ...itensAdmin]
    : [...itensPrincipais, ...itensSecundarios]

export const itensNavegacaoInferior = () => itensPrincipais
