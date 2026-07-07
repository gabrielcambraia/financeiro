import { NavLink, Outlet } from 'react-router-dom'
import {
  LayoutDashboard, ArrowLeftRight, Wallet, Tags, ChevronLeft, ChevronRight
} from 'lucide-react'
import { useState } from 'react'

const itensNavegacao = [
  { to: '/', icon: LayoutDashboard, label: 'Painel' },
  { to: '/transacoes', icon: ArrowLeftRight, label: 'Lançamentos' },
  { to: '/contas', icon: Wallet, label: 'Contas' },
  { to: '/categorias', icon: Tags, label: 'Categorias' },
]

export default function Estrutura() {
  const [recolhido, setRecolhido] = useState(false)

  return (
    <div className="flex h-screen overflow-hidden">
      <aside className={`flex flex-col bg-gray-900 border-r border-gray-800 transition-all duration-300 ${recolhido ? 'w-16' : 'w-56'}`}>
        <div className="flex items-center justify-between p-4 border-b border-gray-800">
          {!recolhido && <span className="font-bold text-white text-lg">💰 Financeiro</span>}
          <button onClick={() => setRecolhido(!recolhido)} className="btn-ghost p-1.5 ml-auto">
            {recolhido ? <ChevronRight size={16} /> : <ChevronLeft size={16} />}
          </button>
        </div>

        <nav className="flex-1 p-2 space-y-1">
          {itensNavegacao.map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                `flex items-center gap-3 px-3 py-2.5 rounded-xl transition-colors font-medium text-sm
                 ${isActive
                  ? 'bg-indigo-600 text-white'
                  : 'text-gray-400 hover:text-white hover:bg-gray-800'}`
              }
            >
              <Icon size={18} className="shrink-0" />
              {!recolhido && <span>{label}</span>}
            </NavLink>
          ))}
        </nav>

        <div className="p-3 border-t border-gray-800">
          {!recolhido && <p className="text-xs text-gray-600 text-center">Controle Financeiro</p>}
        </div>
      </aside>

      <main className="flex-1 overflow-auto">
        <Outlet />
      </main>
    </div>
  )
}
