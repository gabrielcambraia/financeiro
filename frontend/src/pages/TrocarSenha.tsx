import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { trocarSenha } from '../api/autenticacao'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import CampoSenha from '../components/CampoSenha'

export default function TrocarSenha() {
  const navigate = useNavigate()
  const definirSessao = useLojaAutenticacao(s => s.definirSessao)
  const [senhaAtual, setSenhaAtual] = useState('')
  const [novaSenha, setNovaSenha] = useState('')
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setCarregando(true)
    try {
      const resposta = await trocarSenha({ senhaAtual, novaSenha })
      definirSessao(resposta)
      navigate('/')
    } catch (e: unknown) {
      const status = (e as { response?: { status?: number } })?.response?.status
      setErro(status === 401 ? 'Senha atual incorreta' : 'Não foi possível trocar a senha')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="card w-full max-w-sm">
        <h1 className="text-2xl font-bold text-conteudo mb-1">Troca de senha obrigatória</h1>
        <p className="text-sm text-conteudo-suave mb-6">
          Sua senha é temporária. Defina uma nova senha para continuar.
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="label">Senha temporária</label>
            <CampoSenha required
              value={senhaAtual} onChange={e => setSenhaAtual(e.target.value)} />
          </div>
          <div>
            <label className="label">Nova senha</label>
            <CampoSenha required minLength={8}
              value={novaSenha} onChange={e => setNovaSenha(e.target.value)} />
          </div>

          {erro && <p className="text-sm text-red-500">{erro}</p>}

          <button type="submit" disabled={carregando} className="w-full btn-primary">
            {carregando ? 'Salvando...' : 'Trocar senha'}
          </button>
        </form>
      </div>
    </div>
  )
}
