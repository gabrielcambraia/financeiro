import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { entrar } from '../api/autenticacao'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import CampoSenha from '../components/CampoSenha'

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
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="card w-full max-w-sm">
        <h1 className="text-2xl font-bold text-conteudo mb-1">💰 Financeiro</h1>
        <p className="text-sm text-conteudo-suave mb-6">Entre na sua conta</p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="label">E-mail</label>
            <input className="input" type="email" required
              value={email} onChange={e => setEmail(e.target.value)} />
          </div>
          <div>
            <label className="label">Senha</label>
            <CampoSenha required
              value={senha} onChange={e => setSenha(e.target.value)} />
          </div>

          {erro && <p className="text-sm text-red-500">{erro}</p>}

          <button type="submit" disabled={carregando} className="w-full btn-primary">
            {carregando ? 'Entrando...' : 'Entrar'}
          </button>
        </form>

        <p className="text-sm text-conteudo-suave mt-5 text-center">
          Não tem conta? <Link to="/registro" className="text-acento hover:underline">Criar conta</Link>
        </p>
      </div>
    </div>
  )
}
