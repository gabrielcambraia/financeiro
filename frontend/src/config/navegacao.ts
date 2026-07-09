import { LayoutDashboard, ArrowLeftRight, Wallet, Tags } from 'lucide-react'

export const itensNavegacao = [
  { to: '/', icon: LayoutDashboard, label: 'Painel' },
  { to: '/transacoes', icon: ArrowLeftRight, label: 'Lançamentos' },
  { to: '/contas', icon: Wallet, label: 'Contas' },
  { to: '/categorias', icon: Tags, label: 'Categorias' },
]
