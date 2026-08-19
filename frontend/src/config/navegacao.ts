import { LayoutDashboard, ArrowLeftRight, Wallet, Tags, CreditCard, Landmark, PiggyBank, Target, Calendar, HandCoins, TrendingUp, LineChart, Building2, Users, Layers } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

export interface ItemNavegacao {
  to?: string
  icon: LucideIcon
  label: string
  filhos?: { to: string; label: string }[]
}

export const itensPrincipais: ItemNavegacao[] = [
  { to: '/', icon: LayoutDashboard, label: 'Painel' },
  {
    icon: ArrowLeftRight,
    label: 'Lançamentos',
    filhos: [
      { to: '/lancamentos/a-pagar', label: 'Pagamentos' },
      { to: '/lancamentos/a-receber', label: 'Recebimentos' },
      { to: '/lancamentos/recorrencias', label: 'Recorrências' },
      { to: '/lancamentos/faturas', label: 'Faturas' },
    ],
  },
  { to: '/contas', icon: Wallet, label: 'Contas' },
  { to: '/cartoes', icon: CreditCard, label: 'Cartões' },
]

export const itensSecundarios: ItemNavegacao[] = [
  { to: '/calendario', icon: Calendar, label: 'Calendário' },
  { to: '/simulacao', icon: LineChart, label: 'Previsão' },
  { to: '/orcamentos', icon: PiggyBank, label: 'Orçamento' },
  { to: '/metas', icon: Target, label: 'Metas' },
  { to: '/dividas', icon: HandCoins, label: 'Dívidas' },
  { to: '/investimentos', icon: TrendingUp, label: 'Investimentos' },
  { to: '/categorias', icon: Tags, label: 'Categorias' },
  { to: '/centros-custo', icon: Layers, label: 'Centros de Custo' },
  { to: '/entidades', icon: Users, label: 'Entidades' },
]

const itensAdmin: ItemNavegacao[] = [
  { to: '/admin/bancos', icon: Landmark, label: 'Bancos' },
  { to: '/admin/espacos', icon: Building2, label: 'Espaços' },
]

export const itensNavegacao = (admin: boolean): ItemNavegacao[] =>
  admin
    ? [...itensPrincipais, ...itensSecundarios, ...itensAdmin]
    : [...itensPrincipais, ...itensSecundarios]
