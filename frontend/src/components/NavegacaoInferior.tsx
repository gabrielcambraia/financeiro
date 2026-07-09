import { NavLink } from 'react-router-dom'
import { itensNavegacao } from '../config/navegacao'

export default function NavegacaoInferior() {
  return (
    <nav
      className="md:hidden fixed bottom-0 left-0 right-0 z-40 bg-superficie border-t border-borda flex items-stretch"
      style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
    >
      {itensNavegacao.map(({ to, icon: Icon, label }) => (
        <NavLink
          key={to}
          to={to}
          end={to === '/'}
          className={({ isActive }) =>
            `flex-1 flex flex-col items-center justify-center gap-0.5 py-2 text-xs font-medium transition-colors
             ${isActive ? 'text-acento' : 'text-conteudo-suave'}`
          }
        >
          <Icon size={20} />
          <span>{label}</span>
        </NavLink>
      ))}
    </nav>
  )
}
