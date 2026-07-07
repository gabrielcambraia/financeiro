import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { registrar } from '../api/autenticacao'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'

export default function Registro() {
  const navigate = useNavigate()
  const definirSessao = useLojaAutenticacao(s => s.definirSessao)
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [senha, setSenha] = useState('')
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setCarregando(true)
    try {
      const resposta = await registrar({ nome, email, senha })
      definirSessao(resposta)
      navigate('/')
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } })?.response?.status
      setErro(status === 409 ? 'Este e-mail já está cadastrado' : 'Não foi possível criar a conta')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="card w-full max-w-sm">
        <h1 className="text-2xl font-bold text-white mb-1">💰 Financeiro</h1>
        <p className="text-sm text-gray-500 mb-6">Crie sua conta</p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="label">Nome</label>
            <input className="input" required
              value={nome} onChange={e => setNome(e.target.value)} />
          </div>
          <div>
            <label className="label">E-mail</label>
            <input className="input" type="email" required
              value={email} onChange={e => setEmail(e.target.value)} />
          </div>
          <div>
            <label className="label">Senha</label>
            <input className="input" type="password" required minLength={8}
              value={senha} onChange={e => setSenha(e.target.value)} />
          </div>

          {erro && <p className="text-sm text-red-400">{erro}</p>}

          <button type="submit" disabled={carregando} className="w-full btn-primary">
            {carregando ? 'Criando...' : 'Criar conta'}
          </button>
        </form>

        <p className="text-sm text-gray-500 mt-5 text-center">
          Já tem conta? <Link to="/login" className="text-indigo-400 hover:underline">Entrar</Link>
        </p>
      </div>
    </div>
  )
}
