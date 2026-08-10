import { useState } from 'react'
import { NavLink, useNavigate } from 'react-router-dom'
import { LayoutDashboard, ArrowLeftRight, Plus, CreditCard, MoreHorizontal } from 'lucide-react'
import DrawerMobile from './DrawerMobile'

export default function NavegacaoInferior() {
  const [drawerAberto, setDrawerAberto] = useState(false)
  const navigate = useNavigate()

  const itensEsquerda = [
    { to: '/', icon: LayoutDashboard, label: 'Painel', end: true },
    { to: '/transacoes', icon: ArrowLeftRight, label: 'Lançamentos', end: false },
  ]
  const itensDireita = [
    { to: '/cartoes', icon: CreditCard, label: 'Cartões', end: false },
  ]

  return (
    <>
      <DrawerMobile aberto={drawerAberto} fechar={() => setDrawerAberto(false)} />
      <nav
        className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-superficie border-t border-borda flex items-stretch"
        style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
      >
        {itensEsquerda.map(({ to, icon: Icon, label, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              `flex-1 flex flex-col items-center justify-center gap-0.5 py-2 text-xs font-medium transition-colors
               ${isActive ? 'text-acento' : 'text-conteudo-suave'}`
            }
          >
            <Icon size={20} />
            <span>{label}</span>
          </NavLink>
        ))}

        <button
          onClick={() => navigate('/transacoes')}
          className="flex-1 flex flex-col items-center justify-center"
          aria-label="Novo lançamento"
        >
          <div className="bg-acento text-white rounded-full p-3 -mt-5 shadow-lg shadow-acento/30">
            <Plus size={22} />
          </div>
        </button>

        {itensDireita.map(({ to, icon: Icon, label, end }) => (
          <NavLink
            key={to}
            to={to}
            end={end}
            className={({ isActive }) =>
              `flex-1 flex flex-col items-center justify-center gap-0.5 py-2 text-xs font-medium transition-colors
               ${isActive ? 'text-acento' : 'text-conteudo-suave'}`
            }
          >
            <Icon size={20} />
            <span>{label}</span>
          </NavLink>
        ))}

        <button
          onClick={() => setDrawerAberto(true)}
          className="flex-1 flex flex-col items-center justify-center gap-0.5 py-2 text-xs font-medium text-conteudo-suave"
        >
          <MoreHorizontal size={20} />
          <span>Mais</span>
        </button>
      </nav>
    </>
  )
}
