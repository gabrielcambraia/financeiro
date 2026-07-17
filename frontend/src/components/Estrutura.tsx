import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { ChevronLeft, ChevronRight, LogOut } from 'lucide-react'
import { useState } from 'react'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import { sair } from '../api/autenticacao'
import { iniciaisDoNome } from '../utils/formatadores'
import AlternadorTema from './AlternadorTema'
import NavegacaoInferior from './NavegacaoInferior'
import { itensNavegacao } from '../config/navegacao'

export default function Estrutura() {
  const [recolhido, setRecolhido] = useState(false)
  const navigate = useNavigate()
  const sessao = useLojaAutenticacao(s => s.sessao)
  const limparSessao = useLojaAutenticacao(s => s.limparSessao)

  const handleSair = async () => {
    try {
      await sair()
    } finally {
      limparSessao()
      navigate('/login')
    }
  }

  return (
    <div className="flex h-screen overflow-hidden bg-campo p-0 md:p-3 gap-0 md:gap-3">
      {/* Sidebar funde-se com o campo (azul-clarinho no claro, preta no escuro) — sem cartão próprio */}
      <aside className={`hidden md:flex flex-col transition-all duration-300 ${recolhido ? 'w-16' : 'w-56'}`}>
        <div className="flex items-center justify-between p-4">
          {!recolhido && <span className="font-bold text-campo-texto text-lg">Financeiro</span>}
          <button onClick={() => setRecolhido(!recolhido)} className="p-1.5 ml-auto text-campo-texto-suave hover:text-campo-texto rounded-lg hover:bg-campo-texto/5 transition-colors">
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
                  ? 'bg-acento text-white'
                  : 'text-campo-texto-suave hover:text-campo-texto hover:bg-campo-texto/5'}`
              }
            >
              <Icon size={18} className="shrink-0" />
              {!recolhido && <span>{label}</span>}
            </NavLink>
          ))}
        </nav>

        <div className="p-3 border-t border-campo-texto/10 space-y-2">
          {sessao && (
            <Link to="/perfil"
              className="flex items-center gap-3 px-2 py-2 rounded-xl w-full text-sm hover:bg-campo-texto/5 transition-colors">
              <div className="w-8 h-8 rounded-full bg-acento text-white flex items-center justify-center text-xs font-semibold shrink-0">
                {iniciaisDoNome(sessao.nome)}
              </div>
              {!recolhido && (
                <div className="min-w-0">
                  <p className="text-campo-texto text-sm font-medium truncate">{sessao.nome}</p>
                  <p className="text-campo-texto-suave text-xs truncate">{sessao.email}</p>
                </div>
              )}
            </Link>
          )}
        </div>
      </aside>

      {/* Painel central branco flutuante e arredondado */}
      <div className="flex-1 flex flex-col bg-superficie rounded-none md:rounded-3xl overflow-hidden">
        <header className="flex items-center justify-end gap-3 px-6 py-3 border-b border-borda shrink-0">
          <AlternadorTema />
          {sessao && (
            <Link to="/perfil"
              className="w-8 h-8 rounded-full bg-acento text-white flex items-center justify-center text-xs font-semibold shrink-0">
              {iniciaisDoNome(sessao.nome)}
            </Link>
          )}
          {sessao && (
            <button onClick={handleSair} aria-label="Sair" className="btn-ghost p-1.5">
              <LogOut size={18} />
            </button>
          )}
        </header>
        <main className="flex-1 overflow-auto pb-20 md:pb-0">
          <Outlet />
        </main>
      </div>

      <NavegacaoInferior />
    </div>
  )
}
