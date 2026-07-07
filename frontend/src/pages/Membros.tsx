import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { Copy, UserPlus } from 'lucide-react'
import { adicionarMembro, type RespostaMembroAdicionado } from '../api/membros'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'

export default function Membros() {
  const sessao = useLojaAutenticacao(s => s.sessao)
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [erro, setErro] = useState('')
  const [criado, setCriado] = useState<RespostaMembroAdicionado | null>(null)

  const mutation = useMutation({
    mutationFn: adicionarMembro,
    onSuccess: dados => {
      setCriado(dados)
      setNome('')
      setEmail('')
    },
    onError: (e: unknown) => {
      const status = (e as { response?: { status?: number } })?.response?.status
      setErro(status === 409 ? 'Este e-mail já está cadastrado' : 'Não foi possível adicionar o membro')
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setCriado(null)
    mutation.mutate({ nome, email })
  }

  if (sessao?.papel !== 'DONO') {
    return (
      <div className="p-6">
        <p className="text-gray-500">Somente o dono do espaço pode gerenciar membros.</p>
      </div>
    )
  }

  return (
    <div className="p-6 space-y-5 max-w-lg">
      <div>
        <h1 className="text-2xl font-bold text-white">Membros</h1>
        <p className="text-sm text-gray-500 mt-0.5">Adicione pessoas para acessar este espaço.</p>
      </div>

      <div className="card">
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

          {erro && <p className="text-sm text-red-400">{erro}</p>}

          <button type="submit" disabled={mutation.isPending} className="btn-primary flex items-center gap-2">
            <UserPlus size={16} /> {mutation.isPending ? 'Adicionando...' : 'Adicionar membro'}
          </button>
        </form>
      </div>

      {criado && (
        <div className="card border-emerald-800 bg-emerald-950/30">
          <p className="text-sm text-emerald-400 font-medium mb-2">
            Membro criado! Repasse a senha temporária abaixo — ela não será exibida novamente.
          </p>
          <div className="flex items-center gap-2 bg-gray-900 rounded-lg px-3 py-2">
            <code className="text-white font-mono text-sm flex-1">{criado.senhaTemporaria}</code>
            <button type="button" onClick={() => navigator.clipboard.writeText(criado.senhaTemporaria)}
              className="btn-ghost p-1.5">
              <Copy size={14} />
            </button>
          </div>
          <p className="text-xs text-gray-500 mt-2">
            {criado.nome} ({criado.email}) precisará trocar essa senha no primeiro acesso.
          </p>
        </div>
      )}
    </div>
  )
}
