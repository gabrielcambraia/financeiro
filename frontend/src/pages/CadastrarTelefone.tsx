import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { IMaskInput } from 'react-imask'
import { cadastrarTelefone } from '../api/autenticacao'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'

export default function CadastrarTelefone() {
  const navigate = useNavigate()
  const definirSessao = useLojaAutenticacao(s => s.definirSessao)
  const [telefone, setTelefone] = useState('')
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setCarregando(true)
    try {
      const resposta = await cadastrarTelefone({ telefone })
      definirSessao(resposta)
      navigate('/')
    } catch {
      setErro('Não foi possível cadastrar o telefone')
    } finally {
      setCarregando(false)
    }
  }

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <div className="card w-full max-w-sm">
        <h1 className="text-2xl font-bold text-conteudo mb-1">Cadastro de telefone obrigatório</h1>
        <p className="text-sm text-conteudo-suave mb-6">
          Precisamos do seu telefone para continuar.
        </p>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="label">Telefone</label>
            <IMaskInput
              mask={[{ mask: '(00) 0000-0000' }, { mask: '(00) 00000-0000' }]}
              unmask={true}
              required
              className="input"
              placeholder="(11) 99999-9999"
              value={telefone}
              onAccept={val => setTelefone(val as string)}
            />
          </div>

          {erro && <p className="text-sm text-red-500">{erro}</p>}

          <button type="submit" disabled={carregando} className="w-full btn-primary">
            {carregando ? 'Salvando...' : 'Cadastrar telefone'}
          </button>
        </form>
      </div>
    </div>
  )
}
