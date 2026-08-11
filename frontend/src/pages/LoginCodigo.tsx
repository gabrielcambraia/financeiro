import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Mail } from 'lucide-react'
import { solicitarOtp, verificarOtp } from '../api/otp'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import LayoutAuth from '../components/LayoutAuth'

export default function LoginCodigo() {
  const navigate = useNavigate()
  const definirSessao = useLojaAutenticacao(s => s.definirSessao)
  const [email, setEmail] = useState('')
  const [codigo, setCodigo] = useState('')
  const [enviado, setEnviado] = useState(false)
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)

  const handleSolicitar = async (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setCarregando(true)
    try {
      await solicitarOtp(email)
      setEnviado(true)
    } catch {
      setErro('Não foi possível enviar o código. Tente novamente.')
    } finally {
      setCarregando(false)
    }
  }

  const handleVerificar = async (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setCarregando(true)
    try {
      const resposta = await verificarOtp(email, codigo)
      definirSessao(resposta)
      navigate('/')
    } catch {
      setErro('Código inválido ou expirado')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <LayoutAuth>
      {!enviado ? (
        <>
          <h2 className="text-2xl font-bold text-conteudo mb-1.5">Entrar com código</h2>
          <p className="text-sm text-conteudo-suave mb-7">Enviaremos um código de 6 dígitos para seu e-mail</p>

          <form onSubmit={handleSolicitar} className="space-y-[18px]">
            <div>
              <label className="label">Seu e-mail</label>
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
            {erro && <p className="text-sm text-perigo">{erro}</p>}
            <button
              type="submit"
              disabled={carregando}
              className="w-full py-3 bg-acento text-white rounded-[10px] text-[15px] font-semibold hover:brightness-110 transition-all disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {carregando ? 'Enviando...' : 'Enviar código'}
            </button>
          </form>

          <p className="text-sm text-conteudo-suave mt-5 text-center">
            <Link to="/login" className="text-acento hover:underline font-medium">
              ← Entrar com senha
            </Link>
          </p>
        </>
      ) : (
        <>
          <h2 className="text-2xl font-bold text-conteudo mb-1.5">Código enviado!</h2>
          <p className="text-sm text-conteudo-suave mb-7">
            Verifique <strong className="text-conteudo">{email}</strong>. O código expira em 10 minutos.
          </p>

          <form onSubmit={handleVerificar} className="space-y-[18px]">
            <div>
              <label className="label">Código de 6 dígitos</label>
              <input
                className="input text-center text-2xl tracking-widest"
                required
                maxLength={6}
                pattern="\d{6}"
                value={codigo}
                onChange={e => setCodigo(e.target.value.replace(/\D/g, ''))}
                placeholder="000000"
                autoFocus
              />
            </div>
            {erro && <p className="text-sm text-perigo">{erro}</p>}
            <button
              type="submit"
              disabled={carregando}
              className="w-full py-3 bg-acento text-white rounded-[10px] text-[15px] font-semibold hover:brightness-110 transition-all disabled:opacity-60 disabled:cursor-not-allowed"
            >
              {carregando ? 'Verificando...' : 'Entrar'}
            </button>
            <button
              type="button"
              onClick={() => setEnviado(false)}
              className="w-full text-sm text-conteudo-suave hover:text-conteudo text-center py-1 transition-colors"
            >
              Usar outro e-mail
            </button>
          </form>
        </>
      )}
    </LayoutAuth>
  )
}
