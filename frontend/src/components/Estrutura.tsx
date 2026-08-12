import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom'
import { ChevronLeft, ChevronRight, LogOut } from 'lucide-react'
import { useState } from 'react'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import { sair } from '../api/autenticacao'
import { iniciaisDoNome } from '../utils/formatadores'
import AlternadorTema from './AlternadorTema'
import NavegacaoInferior from './NavegacaoInferior'
import SeletorEntidade from './SeletorEntidade'
import LogoNexo360 from './LogoNexo360'
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
    <div className="flex h-screen overflow-hidden bg-fundo">
      {/* Sidebar — azul-marinho fixo, nunca muda com o tema */}
      <aside
        className={`hidden md:flex flex-col shrink-0 transition-all duration-300 bg-sb-fundo border-r border-sb-borda
                    ${recolhido ? 'w-16' : 'w-56'}`}
      >
        {/* Topo: logo + nome + toggle */}
        <div className={`flex items-center px-3 py-4 gap-2.5 ${recolhido ? 'justify-center' : 'justify-between'}`}>
          <div className="flex items-center gap-2.5 min-w-0">
            <LogoNexo360 tamanho={32} />
            {!recolhido && (
              <span className="font-bold text-sb-texto text-base truncate">Nexo360</span>
            )}
          </div>
          {!recolhido && (
            <button
              onClick={() => setRecolhido(true)}
              className="p-1.5 text-sb-suave hover:text-sb-texto rounded-lg hover:bg-sb-hover transition-colors shrink-0"
              aria-label="Recolher sidebar"
            >
              <ChevronLeft size={16} />
            </button>
          )}
        </div>

        {recolhido && (
          <div className="flex justify-center pb-2">
            <button
              onClick={() => setRecolhido(false)}
              className="p-1.5 text-sb-suave hover:text-sb-texto rounded-lg hover:bg-sb-hover transition-colors"
              aria-label="Expandir sidebar"
            >
              <ChevronRight size={16} />
            </button>
          </div>
        )}

        <SeletorEntidade recolhido={recolhido} nasSidebar />

        <nav className="flex-1 min-h-0 overflow-y-auto px-2 py-1 space-y-0.5">
          {itensNavegacao(sessao?.nivelAcesso === 'ADMIN').map(({ to, icon: Icon, label }) => (
            <NavLink
              key={to}
              to={to}
              end={to === '/'}
              className={({ isActive }) =>
                `relative flex items-center gap-3 px-3 py-2.5 rounded-xl transition-colors font-medium text-sm
                 ${isActive
                  ? 'bg-sb-ativo text-sb-texto'
                  : 'text-sb-suave hover:text-sb-texto hover:bg-sb-hover'}`
              }
            >
              {({ isActive }) => (
                <>
                  {isActive && (
                    <span className="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 rounded-full bg-sb-ativo-barra" />
                  )}
                  <Icon size={18} className="shrink-0" />
                  {!recolhido && <span>{label}</span>}
                </>
              )}
            </NavLink>
          ))}
        </nav>

        {/* Rodapé da sidebar: avatar + nome + sair */}
        <div className="border-t border-sb-borda p-3 space-y-1">
          {sessao && (
            <Link
              to="/perfil"
              className="flex items-center gap-2.5 px-2 py-2 rounded-xl hover:bg-sb-hover transition-colors"
            >
              <div className="w-8 h-8 rounded-full bg-sb-ativo-barra text-sb-fundo flex items-center justify-center text-xs font-bold shrink-0">
                {iniciaisDoNome(sessao.nome)}
              </div>
              {!recolhido && (
                <div className="min-w-0 flex-1">
                  <p className="text-sb-texto text-sm font-medium truncate">{sessao.nome}</p>
                  <p className="text-sb-suave text-xs truncate">{sessao.email}</p>
                </div>
              )}
            </Link>
          )}
          <button
            onClick={handleSair}
            aria-label="Sair"
            className={`w-full flex items-center gap-2.5 px-2 py-2 rounded-xl text-sb-suave hover:text-sb-texto hover:bg-sb-hover transition-colors text-sm
                        ${recolhido ? 'justify-center' : ''}`}
          >
            <LogOut size={16} className="shrink-0" />
            {!recolhido && <span>Sair</span>}
          </button>
        </div>
      </aside>

      {/* Barra superior mobile — fixa igual à barra inferior */}
      <header
        className="md:hidden fixed top-0 left-0 right-0 z-40 bg-superficie border-b border-borda flex flex-col justify-end"
        style={{ paddingTop: 'env(safe-area-inset-top)' }}
      >
        <div className="flex items-center justify-end gap-3 px-6 py-3">
          <div className="mr-auto">
            <SeletorEntidade />
          </div>
          <AlternadorTema />
          {sessao && (
            <Link
              to="/perfil"
              className="w-8 h-8 rounded-full bg-acento text-white flex items-center justify-center text-xs font-semibold shrink-0"
            >
              {iniciaisDoNome(sessao.nome)}
            </Link>
          )}
        </div>
      </header>

      {/* Área de conteúdo */}
      <div className="flex-1 flex flex-col overflow-hidden">
        {/* Barra superior desktop — em fluxo normal na sidebar layout */}
        <header
          className="hidden md:flex items-center justify-end gap-3 px-6 pb-3 bg-superficie border-b border-borda shrink-0"
          style={{ paddingTop: 'calc(env(safe-area-inset-top) + 0.75rem)' }}
        >
          <AlternadorTema />
          {sessao && (
            <Link
              to="/perfil"
              className="w-8 h-8 rounded-full bg-acento text-white flex items-center justify-center text-xs font-semibold shrink-0"
            >
              {iniciaisDoNome(sessao.nome)}
            </Link>
          )}
        </header>
        <main className="flex-1 overflow-auto pb-20 md:pb-0">
          {/* Espaçador para a barra superior fixa no mobile */}
          <div className="md:hidden" style={{ height: 'calc(env(safe-area-inset-top) + 3.5rem)' }} />
          <Outlet />
        </main>
      </div>

      <NavegacaoInferior />
    </div>
  )
}
