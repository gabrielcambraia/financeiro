import { LayoutDashboard, ArrowLeftRight, Wallet, Tags, CreditCard, Landmark, PiggyBank, Target, Calendar, HandCoins, TrendingUp, LineChart, Building2, Users, Layers, ArrowDownCircle, ArrowUpCircle, Repeat2, Receipt, BarChart3, Database, List, UserCog } from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

export interface ItemNavegacao {
  to?: string
  icon: LucideIcon
  label: string
  filhos?: { to: string; label: string; icon?: LucideIcon }[]
}

const itensBase: ItemNavegacao[] = [
  { to: '/', icon: LayoutDashboard, label: 'Dashboard' },
  {
    icon: ArrowLeftRight,
    label: 'Lançamentos',
    filhos: [
      { to: '/lancamentos/transacoes',    label: 'Transações',   icon: List },
      { to: '/lancamentos/a-pagar',       label: 'Pagamentos',   icon: ArrowDownCircle },
      { to: '/lancamentos/a-receber',     label: 'Recebimentos', icon: ArrowUpCircle },
      { to: '/lancamentos/recorrencias',  label: 'Recorrências', icon: Repeat2 },
      { to: '/lancamentos/compras-cartao', label: 'Compras no cartão', icon: CreditCard },
      { to: '/lancamentos/faturas',       label: 'Faturas',      icon: Receipt },
    ],
  },
  { to: '/cartoes',    icon: CreditCard, label: 'Cartões' },
  { to: '/calendario', icon: Calendar,   label: 'Calendário' },
  {
    icon: BarChart3,
    label: 'Planejamento',
    filhos: [
      { to: '/metas',        label: 'Metas',         icon: Target },
      { to: '/orcamentos',   label: 'Orçamentos',    icon: PiggyBank },
      { to: '/investimentos', label: 'Investimentos', icon: TrendingUp },
      { to: '/dividas',      label: 'Dívidas',       icon: HandCoins },
      { to: '/fluxo-de-caixa', label: 'Fluxo de caixa', icon: LineChart },
    ],
  },
  {
    icon: Database,
    label: 'Cadastros',
    filhos: [
      { to: '/contas',        label: 'Contas',           icon: Wallet },
      { to: '/cartoes',       label: 'Cartões',          icon: CreditCard },
      { to: '/centros-custo', label: 'Centros de Custo', icon: Layers },
      { to: '/categorias',    label: 'Categorias',       icon: Tags },
      { to: '/filiais',        label: 'Filiais',           icon: Users },
    ],
  },
]

const filhoCadastrosDono = { to: '/usuarios', label: 'Usuários', icon: UserCog }

const itensAdmin: ItemNavegacao[] = [
  { to: '/admin/bancos',  icon: Landmark, label: 'Bancos' },
  { to: '/admin/espacos', icon: Building2, label: 'Espaços' },
]

export const itensNavegacao = (admin: boolean, dono: boolean): ItemNavegacao[] => {
  const base = dono
    ? itensBase.map(item =>
        item.label === 'Cadastros' && item.filhos
          ? { ...item, filhos: [...item.filhos, filhoCadastrosDono] }
          : item
      )
    : itensBase
  return admin ? [...base, ...itensAdmin] : base
}
