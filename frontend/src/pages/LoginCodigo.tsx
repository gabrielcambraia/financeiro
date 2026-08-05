import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { solicitarOtp, verificarOtp } from '../api/otp'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'

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
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="card w-full max-w-sm">
        <h1 className="text-2xl font-bold text-conteudo mb-1">💰 Financeiro</h1>
        <p className="text-sm text-conteudo-suave mb-6">Entrar com código por e-mail</p>

        {!enviado ? (
          <form onSubmit={handleSolicitar} className="space-y-4">
            <div>
              <label className="label">Seu e-mail</label>
              <input className="input" type="email" required
                value={email} onChange={e => setEmail(e.target.value)} />
            </div>
            {erro && <p className="text-sm text-red-500">{erro}</p>}
            <button type="submit" disabled={carregando} className="w-full btn-primary">
              {carregando ? 'Enviando...' : 'Enviar código'}
            </button>
          </form>
        ) : (
          <form onSubmit={handleVerificar} className="space-y-4">
            <p className="text-sm text-conteudo-suave">
              Código enviado para <strong>{email}</strong>. Válido por 10 minutos.
            </p>
            <div>
              <label className="label">Código de 6 dígitos</label>
              <input
                className="input text-center text-2xl tracking-widest"
                required maxLength={6} pattern="\d{6}"
                value={codigo}
                onChange={e => setCodigo(e.target.value.replace(/\D/g, ''))}
                placeholder="000000"
                autoFocus
              />
            </div>
            {erro && <p className="text-sm text-red-500">{erro}</p>}
            <button type="submit" disabled={carregando} className="w-full btn-primary">
              {carregando ? 'Verificando...' : 'Entrar'}
            </button>
            <button type="button" onClick={() => setEnviado(false)}
              className="w-full text-sm text-conteudo-suave hover:text-conteudo">
              Usar outro e-mail
            </button>
          </form>
        )}

        <p className="text-sm text-conteudo-suave mt-5 text-center">
          <Link to="/login" className="text-acento hover:underline">Entrar com senha</Link>
          {' · '}
          <Link to="/registro" className="text-acento hover:underline">Criar conta</Link>
        </p>
      </div>
    </div>
  )
}
