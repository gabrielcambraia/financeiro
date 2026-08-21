import { X, LogOut } from 'lucide-react'
import { NavLink, useNavigate } from 'react-router-dom'
import { itensNavegacao } from '../config/navegacao'
import type { LucideIcon } from 'lucide-react'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'

interface Props {
  aberto: boolean
  fechar: () => void
}

// Rotas fixadas na barra inferior — não precisam aparecer no drawer.
const ROTAS_BARRA_INFERIOR = new Set(['/', '/lancamentos/a-pagar', '/cartoes'])

function itensDrawer(dono: boolean): { to: string; icon: LucideIcon; label: string }[] {
  return itensNavegacao(false, dono).flatMap(item => {
    if (item.filhos) {
      return item.filhos
        .filter(f => !ROTAS_BARRA_INFERIOR.has(f.to))
        .map(f => ({ to: f.to, icon: f.icon ?? item.icon, label: f.label }))
    }
    if (item.to && !ROTAS_BARRA_INFERIOR.has(item.to)) {
      return [{ to: item.to, icon: item.icon, label: item.label }]
    }
    return []
  })
}

export default function DrawerMobile({ aberto, fechar }: Props) {
  const limparSessao = useLojaAutenticacao(s => s.limparSessao)
  const sessao = useLojaAutenticacao(s => s.sessao)
  const navigate = useNavigate()
  const itens = itensDrawer(sessao?.papel === 'DONO')

  const sair = () => {
    limparSessao()
    navigate('/login')
    fechar()
  }

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
          {itens.map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
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
        <button
          onClick={sair}
          className="flex items-center gap-3 w-full px-5 py-4 border-t border-borda text-red-400 hover:bg-red-900/20 transition-colors text-sm font-medium">
          <LogOut size={18} />
          Sair
        </button>
      </div>
    </>
  )
}
