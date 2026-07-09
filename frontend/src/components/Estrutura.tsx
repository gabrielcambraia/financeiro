import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import {
  LayoutDashboard, ArrowLeftRight, Wallet, Tags, ChevronLeft, ChevronRight, LogOut
} from 'lucide-react'
import { useState } from 'react'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import { iniciaisDoNome } from '../utils/formatadores'
import AlternadorTema from './AlternadorTema'

const itensNavegacao = [
  { to: '/', icon: LayoutDashboard, label: 'Painel' },
  { to: '/transacoes', icon: ArrowLeftRight, label: 'Lançamentos' },
  { to: '/contas', icon: Wallet, label: 'Contas' },
  { to: '/categorias', icon: Tags, label: 'Categorias' },
]

export default function Estrutura() {
  const [recolhido, setRecolhido] = useState(false)
  const navigate = useNavigate()
  const sessao = useLojaAutenticacao(s => s.sessao)
  const limparSessao = useLojaAutenticacao(s => s.limparSessao)

  const handleSair = () => {
    limparSessao()
    navigate('/login')
  }

  return (
    <div className="flex h-screen overflow-hidden bg-fundo p-3 gap-3">
      {/* Sidebar arredondada e flutuante — cinza no tema claro, preta no escuro */}
      <aside className={`flex flex-col bg-superficie rounded-3xl transition-all duration-300 ${recolhido ? 'w-16' : 'w-56'}`}>
        <div className="flex items-center justify-between p-4">
          {!recolhido && <span className="font-bold text-conteudo text-lg">Financeiro</span>}
          <button onClick={() => setRecolhido(!recolhido)} className="p-1.5 ml-auto text-conteudo-suave hover:text-conteudo rounded-lg hover:bg-superficie-2 transition-colors">
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
                  : 'text-conteudo-suave hover:text-conteudo hover:bg-superficie-2'}`
              }
            >
              <Icon size={18} className="shrink-0" />
              {!recolhido && <span>{label}</span>}
            </NavLink>
          ))}
        </nav>

        <div className="p-3 border-t border-borda space-y-2">
          {sessao && (
            <Link to="/perfil"
              className="flex items-center gap-3 px-2 py-2 rounded-xl w-full text-sm hover:bg-superficie-2 transition-colors">
              <div className="w-8 h-8 rounded-full bg-acento text-white flex items-center justify-center text-xs font-semibold shrink-0">
                {iniciaisDoNome(sessao.nome)}
              </div>
              {!recolhido && (
                <div className="min-w-0">
                  <p className="text-conteudo text-sm font-medium truncate">{sessao.nome}</p>
                  <p className="text-conteudo-suave text-xs truncate">{sessao.email}</p>
                </div>
              )}
            </Link>
          )}
          {sessao && (
            <button onClick={handleSair}
              className="flex items-center gap-3 px-3 py-2 rounded-xl w-full text-sm font-medium text-conteudo-suave hover:text-conteudo hover:bg-superficie-2 transition-colors">
              <LogOut size={18} className="shrink-0" />
              {!recolhido && <span>Sair</span>}
            </button>
          )}
        </div>
      </aside>

      {/* Painel central branco flutuante e arredondado */}
      <div className="flex-1 flex flex-col bg-superficie rounded-3xl overflow-hidden">
        <header className="flex items-center justify-end gap-3 px-6 py-3 border-b border-borda shrink-0">
          <AlternadorTema />
          {sessao && (
            <Link to="/perfil"
              className="w-8 h-8 rounded-full bg-acento text-white flex items-center justify-center text-xs font-semibold shrink-0">
              {iniciaisDoNome(sessao.nome)}
            </Link>
          )}
        </header>
        <main className="flex-1 overflow-auto">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
