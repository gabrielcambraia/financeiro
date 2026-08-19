import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { createPortal } from 'react-dom'
import { ChevronDown, ChevronLeft, ChevronRight, LogOut } from 'lucide-react'
import { useState, useRef } from 'react'
import type { LucideIcon } from 'lucide-react'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import { sair } from '../api/autenticacao'
import { iniciaisDoNome } from '../utils/formatadores'
import AlternadorTema from './AlternadorTema'
import NavegacaoInferior from './NavegacaoInferior'
import SeletorEntidade from './SeletorEntidade'
import LogoNexo360 from './LogoNexo360'
import { type ItemNavegacao, itensNavegacao } from '../config/navegacao'

interface PropsGrupo {
  item: { icon: LucideIcon; label: string; filhos: { to: string; label: string }[] }
  recolhido: boolean
}

function GrupoNavegacao({ item, recolhido }: PropsGrupo) {
  const location = useLocation()
  const [aberto, setAberto] = useState(false)
  const [popoverTop, setPopoverTop] = useState(0)
  const [hovering, setHovering] = useState(false)
  const btnRef = useRef<HTMLButtonElement>(null)
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const Icon = item.icon

  const grupoAtivo = item.filhos.some(f => location.pathname.startsWith(f.to))

  const iniciarHover = () => {
    if (timerRef.current) clearTimeout(timerRef.current)
    if (btnRef.current) setPopoverTop(btnRef.current.getBoundingClientRect().top)
    setHovering(true)
  }

  const encerrarHover = () => {
    timerRef.current = setTimeout(() => setHovering(false), 150)
  }

  if (recolhido) {
    return (
      <>
        <button
          ref={btnRef}
          onMouseEnter={iniciarHover}
          onMouseLeave={encerrarHover}
          className={`relative w-full flex items-center justify-center px-3 py-2.5 rounded-xl transition-colors font-medium text-sm
                      ${grupoAtivo ? 'bg-sb-ativo text-sb-texto' : 'text-sb-suave hover:text-sb-texto hover:bg-sb-hover'}`}
        >
          {grupoAtivo && <span className="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 rounded-full bg-sb-ativo-barra" />}
          <Icon size={18} className="shrink-0" />
        </button>
        {hovering && createPortal(
          <div
            onMouseEnter={iniciarHover}
            onMouseLeave={encerrarHover}
            style={{ top: popoverTop, left: 64 }}
            className="fixed z-50 bg-sb-fundo border border-sb-borda rounded-xl py-1.5 w-44 shadow-xl"
          >
            <p className="px-3 py-1 text-xs font-semibold text-sb-suave uppercase tracking-wider">{item.label}</p>
            {item.filhos.map(f => (
              <NavLink key={f.to} to={f.to}
                className={({ isActive }) =>
                  `block mx-1 px-3 py-2 rounded-lg text-sm transition-colors
                   ${isActive ? 'text-sb-texto bg-sb-ativo' : 'text-sb-suave hover:text-sb-texto hover:bg-sb-hover'}`
                }
              >
                {f.label}
              </NavLink>
            ))}
          </div>,
          document.body
        )}
      </>
    )
  }

  return (
    <div>
      <button
        onClick={() => setAberto(a => !a)}
        className={`relative w-full flex items-center gap-3 px-3 py-2.5 rounded-xl transition-colors font-medium text-sm
                    ${grupoAtivo ? 'bg-sb-ativo text-sb-texto' : 'text-sb-suave hover:text-sb-texto hover:bg-sb-hover'}`}
      >
        {grupoAtivo && <span className="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 rounded-full bg-sb-ativo-barra" />}
        <Icon size={18} className="shrink-0" />
        <span className="flex-1 text-left">{item.label}</span>
        <ChevronDown
          size={14}
          className={`transition-transform duration-200 shrink-0 ${(aberto || grupoAtivo) ? 'rotate-180' : ''}`}
        />
      </button>
      {(aberto || grupoAtivo) && (
        <div className="ml-7 mt-0.5 space-y-0.5">
          {item.filhos.map(f => (
            <NavLink key={f.to} to={f.to}
              className={({ isActive }) =>
                `block px-3 py-2 rounded-xl text-sm font-medium transition-colors
                 ${isActive ? 'bg-sb-ativo text-sb-texto' : 'text-sb-suave hover:text-sb-texto hover:bg-sb-hover'}`
              }
            >
              {f.label}
            </NavLink>
          ))}
        </div>
      )}
    </div>
  )
}

function ItemNavegacaoSimples({ item, recolhido }: { item: ItemNavegacao; recolhido: boolean }) {
  const Icon = item.icon
  return (
    <NavLink
      to={item.to!}
      end={item.to === '/'}
      className={({ isActive }) =>
        `relative flex items-center gap-3 px-3 py-2.5 rounded-xl transition-colors font-medium text-sm
         ${isActive ? 'bg-sb-ativo text-sb-texto' : 'text-sb-suave hover:text-sb-texto hover:bg-sb-hover'}`
      }
    >
      {({ isActive }) => (
        <>
          {isActive && <span className="absolute left-0 top-1/2 -translate-y-1/2 w-0.5 h-5 rounded-full bg-sb-ativo-barra" />}
          <Icon size={18} className="shrink-0" />
          {!recolhido && <span>{item.label}</span>}
        </>
      )}
    </NavLink>
  )
}

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

  const itens = itensNavegacao(sessao?.nivelAcesso === 'ADMIN')

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
          {itens.map(item =>
            item.filhos ? (
              <GrupoNavegacao
                key={item.label}
                item={{ icon: item.icon, label: item.label, filhos: item.filhos }}
                recolhido={recolhido}
              />
            ) : (
              <ItemNavegacaoSimples key={item.to} item={item} recolhido={recolhido} />
            )
          )}
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
