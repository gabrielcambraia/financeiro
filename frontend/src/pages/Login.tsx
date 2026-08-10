import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Mail, Lock, Phone } from 'lucide-react'
import { entrar } from '../api/autenticacao'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import CampoSenha from '../components/CampoSenha'
import LayoutAuth from '../components/LayoutAuth'

export default function Login() {
  const navigate = useNavigate()
  const definirSessao = useLojaAutenticacao(s => s.definirSessao)
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setCarregando(true)
    try {
      const resposta = await entrar({ email, senha })
      definirSessao(resposta)
      navigate('/')
    } catch {
      setErro('E-mail ou senha inválidos')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <LayoutAuth>
      <h2 className="text-2xl font-bold text-conteudo mb-1.5">Bem-vindo de volta!</h2>
      <p className="text-sm text-conteudo-suave mb-7">Acesse sua conta para continuar</p>

      <form onSubmit={handleSubmit} className="space-y-[18px]">
        <div>
          <label className="label">E-mail</label>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-conteudo-suave pointer-events-none">
              <Mail size={15} />
            </span>
            <input
              className="input pl-10"
              type="email"
              required
              placeholder="voce@email.com"
              value={email}
              onChange={e => setEmail(e.target.value)}
            />
          </div>
        </div>

        <div>
          <div className="flex justify-between items-center mb-1.5">
            <label className="label mb-0">Senha</label>
          </div>
          <div className="relative">
            <span className="absolute left-3 top-1/2 -translate-y-1/2 text-conteudo-suave pointer-events-none z-10">
              <Lock size={15} />
            </span>
            <CampoSenha
              className="input pl-10"
              required
              placeholder="••••••••"
              value={senha}
              onChange={e => setSenha(e.target.value)}
            />
          </div>
        </div>

        {erro && <p className="text-sm text-perigo">{erro}</p>}

        <button
          type="submit"
          disabled={carregando}
          className="w-full py-3 bg-acento text-white rounded-[10px] text-[15px] font-semibold hover:brightness-110 transition-all disabled:opacity-60 disabled:cursor-not-allowed mt-2"
        >
          {carregando ? 'Entrando...' : 'Entrar'}
        </button>
      </form>

      <div className="flex items-center gap-3 my-5 text-conteudo-suave text-xs">
        <div className="flex-1 h-px bg-borda" />
        ou entre com código de acesso
        <div className="flex-1 h-px bg-borda" />
      </div>

      <Link
        to="/entrar-codigo"
        className="w-full flex items-center justify-center gap-2 py-2.5 rounded-[10px] border border-borda bg-superficie-2 text-conteudo text-sm font-medium hover:bg-borda/60 transition-colors"
      >
        <Phone size={16} />
        Entrar com código de 6 dígitos
      </Link>
    </LayoutAuth>
  )
}
