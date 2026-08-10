import { Link, useLocation } from 'react-router-dom'
import { Check } from 'lucide-react'
import LogoNexo360 from './LogoNexo360'
import AlternadorTema from './AlternadorTema'

const features = [
  'Controle de contas e cartões',
  'Metas e investimentos integrados',
  'Relatórios e previsão financeira',
  'Acesso familiar e empresarial',
]

const stats = [
  ['R$ 2,4M', 'gerenciados'],
  ['1.200+', 'usuários'],
  ['99,9%', 'uptime'],
] as const

export default function LayoutAuth({ children }: { children: React.ReactNode }) {
  const { pathname } = useLocation()
  const abaLogin = pathname === '/login' || pathname === '/entrar-codigo'
  const abaRegistro = pathname === '/registro'

  return (
    <div className="min-h-screen flex bg-fundo">
      {/* Mobile header */}
      <div className="md:hidden fixed top-0 left-0 right-0 z-10 flex items-center gap-2.5 px-5 py-4 bg-sb-fundo">
        <LogoNexo360 tamanho={28} />
        <span className="text-base font-bold text-sb-texto">Nexo360</span>
      </div>

      {/* Left panel — always navy, never changes with theme */}
      <div className="hidden md:flex w-[45%] shrink-0 flex-col justify-between px-14 py-12 bg-sb-fundo relative overflow-hidden">
        <div
          className="absolute rounded-full pointer-events-none"
          style={{ width: 400, height: 400, background: 'radial-gradient(circle, rgb(40 100 220 / 0.15), transparent 70%)', top: -100, right: -100 }}
        />
        <div
          className="absolute rounded-full pointer-events-none"
          style={{ width: 300, height: 300, background: 'radial-gradient(circle, rgb(46 160 90 / 0.1), transparent 70%)', bottom: -80, left: -80 }}
        />

        <div className="relative z-10 flex items-center gap-3">
          <LogoNexo360 tamanho={36} />
          <span className="text-xl font-bold text-sb-texto">Nexo360</span>
        </div>

        <div className="relative z-10">
          <h1 className="text-[32px] font-bold text-white leading-tight tracking-tight mb-4">
            Controle total<br />da sua vida financeira.
          </h1>
          <p className="text-[15px] text-sb-suave leading-relaxed max-w-sm mb-7">
            Organize receitas, despesas, cartões, metas e investimentos num único lugar. Multi-tenant, seguro, sempre online.
          </p>
          <div className="space-y-3">
            {features.map(f => (
              <div key={f} className="flex items-center gap-2.5 text-sb-suave text-sm">
                <div
                  className="w-[22px] h-[22px] rounded-full flex items-center justify-center shrink-0"
                  style={{ background: 'rgb(46 180 100 / 0.2)' }}
                >
                  <Check size={12} className="text-green-400" strokeWidth={3} />
                </div>
                {f}
              </div>
            ))}
          </div>
        </div>

        <div className="relative z-10 flex gap-6">
          {stats.map(([val, lbl]) => (
            <div key={lbl} className="text-center">
              <div className="text-2xl font-bold text-white mb-0.5">{val}</div>
              <div className="text-xs text-sb-suave">{lbl}</div>
            </div>
          ))}
        </div>
      </div>

      {/* Right panel */}
      <div className="flex-1 flex items-start md:items-center justify-center px-5 pt-24 pb-8 md:py-8 relative">
        <div className="fixed top-4 right-4 z-20">
          <AlternadorTema />
        </div>

        <div className="w-full max-w-[420px]">
          {/* Tab bar */}
          <div className="flex bg-superficie-2 rounded-[10px] p-[3px] mb-6">
            <Link
              to="/login"
              className={`flex-1 py-2 text-center rounded-[8px] text-[13.5px] font-medium transition-all ${
                abaLogin
                  ? 'bg-superficie text-conteudo font-semibold shadow-sm'
                  : 'text-conteudo-suave hover:text-conteudo'
              }`}
            >
              Entrar
            </Link>
            <Link
              to="/registro"
              className={`flex-1 py-2 text-center rounded-[8px] text-[13.5px] font-medium transition-all ${
                abaRegistro
                  ? 'bg-superficie text-conteudo font-semibold shadow-sm'
                  : 'text-conteudo-suave hover:text-conteudo'
              }`}
            >
              Criar conta
            </Link>
          </div>

          {/* Card */}
          <div className="px-0 py-0 md:bg-superficie md:border md:border-borda md:rounded-[20px] md:p-10 md:shadow-sm dark:md:shadow-none">
            {children}
          </div>
        </div>
      </div>
    </div>
  )
}
