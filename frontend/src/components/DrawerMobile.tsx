import { X } from 'lucide-react'
import { NavLink } from 'react-router-dom'
import { itensSecundarios } from '../config/navegacao'

interface Props {
  aberto: boolean
  fechar: () => void
}

export default function DrawerMobile({ aberto, fechar }: Props) {
  return (
    <>
      <div
        className={`md:hidden fixed inset-0 z-40 bg-black/50 transition-opacity duration-200 ${aberto ? 'opacity-100' : 'opacity-0 pointer-events-none'}`}
        onClick={fechar}
      />
      <div
        className={`md:hidden fixed bottom-0 left-0 right-0 z-50 bg-superficie rounded-t-2xl shadow-xl transition-transform duration-300 ${aberto ? 'translate-y-0' : 'translate-y-full'}`}
        style={{ paddingBottom: 'env(safe-area-inset-bottom)' }}
      >
        <div className="flex items-center justify-between px-5 py-4 border-b border-borda">
          <p className="text-sm font-semibold text-conteudo">Menu</p>
          <button onClick={fechar} className="btn-ghost p-1.5"><X size={18} /></button>
        </div>
        <div className="grid grid-cols-4 gap-1 p-4">
          {itensSecundarios.map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              onClick={fechar}
              className={({ isActive }) =>
                `flex flex-col items-center gap-1.5 p-3 rounded-xl transition-colors text-xs font-medium
                 ${isActive ? 'text-acento bg-acento/10' : 'text-conteudo-suave hover:text-conteudo hover:bg-superficie-2'}`
              }
            >
              <Icon size={22} />
              <span className="text-center leading-tight">{label}</span>
            </NavLink>
          ))}
        </div>
      </div>
    </>
  )
}
