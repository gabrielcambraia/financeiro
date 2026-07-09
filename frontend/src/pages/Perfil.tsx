import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { Copy, UserPlus, KeyRound, Palette, User } from 'lucide-react'
import { trocarSenha } from '../api/autenticacao'
import { adicionarMembro, listarMembros, type RespostaMembroAdicionado } from '../api/membros'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import { iniciaisDoNome } from '../utils/formatadores'
import AlternadorTema from '../components/AlternadorTema'
import CampoSenha from '../components/CampoSenha'

function SecaoInformacoes() {
  const sessao = useLojaAutenticacao(s => s.sessao)
  if (!sessao) return null

  return (
    <div className="card">
      <div className="flex items-center gap-2 mb-4">
        <User size={16} className="text-acento" />
        <h2 className="text-sm font-semibold text-conteudo">Informações</h2>
      </div>
      <div className="flex items-center gap-4">
        <div className="w-14 h-14 rounded-full bg-acento text-white flex items-center justify-center text-lg font-semibold shrink-0">
          {iniciaisDoNome(sessao.nome)}
        </div>
        <div className="min-w-0">
          <p className="text-conteudo font-medium truncate">{sessao.nome}</p>
          <p className="text-conteudo-suave text-sm truncate">{sessao.email}</p>
          <span className={`inline-block mt-1 text-xs font-medium px-2 py-0.5 rounded-full ${
            sessao.papel === 'DONO' ? 'text-pastel-lilas-texto bg-pastel-lilas' : 'text-pastel-azul-texto bg-pastel-azul'
          }`}>
            {sessao.papel === 'DONO' ? 'Dono' : 'Membro'}
          </span>
        </div>
      </div>
    </div>
  )
}

function SecaoAparencia() {
  return (
    <div className="card">
      <div className="flex items-center gap-2 mb-4">
        <Palette size={16} className="text-acento" />
        <h2 className="text-sm font-semibold text-conteudo">Aparência</h2>
      </div>
      <div className="flex items-center justify-between">
        <div>
          <p className="text-sm text-conteudo">Tema claro ou escuro</p>
          <p className="text-xs text-conteudo-suave mt-0.5">Escolha como o Financeiro deve aparecer para você.</p>
        </div>
        <AlternadorTema />
      </div>
    </div>
  )
}

function SecaoTrocarSenha() {
  const definirSessao = useLojaAutenticacao(s => s.definirSessao)
  const [senhaAtual, setSenhaAtual] = useState('')
  const [novaSenha, setNovaSenha] = useState('')
  const [erro, setErro] = useState('')
  const [sucesso, setSucesso] = useState(false)

  const mutation = useMutation({
    mutationFn: trocarSenha,
    onSuccess: resposta => {
      definirSessao(resposta)
      setSenhaAtual('')
      setNovaSenha('')
      setSucesso(true)
      setErro('')
    },
    onError: (e: unknown) => {
      const status = (e as { response?: { status?: number } })?.response?.status
      setErro(status === 401 ? 'Senha atual incorreta' : 'Não foi possível trocar a senha')
      setSucesso(false)
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setSucesso(false)
    mutation.mutate({ senhaAtual, novaSenha })
  }

  return (
    <div className="card">
      <div className="flex items-center gap-2 mb-4">
        <KeyRound size={16} className="text-acento" />
        <h2 className="text-sm font-semibold text-conteudo">Segurança — Trocar senha</h2>
      </div>
      <form onSubmit={handleSubmit} className="space-y-4 max-w-sm">
        <div>
          <label className="label">Senha atual</label>
          <CampoSenha required
            value={senhaAtual} onChange={e => setSenhaAtual(e.target.value)} />
        </div>
        <div>
          <label className="label">Nova senha</label>
          <CampoSenha required minLength={8}
            value={novaSenha} onChange={e => setNovaSenha(e.target.value)} />
        </div>

        {erro && <p className="text-sm text-red-500">{erro}</p>}
        {sucesso && <p className="text-sm text-emerald-500">Senha atualizada com sucesso.</p>}

        <button type="submit" disabled={mutation.isPending} className="btn-primary">
          {mutation.isPending ? 'Salvando...' : 'Trocar senha'}
        </button>
      </form>
    </div>
  )
}

function SecaoMembros() {
  const qc = useQueryClient()
  const [nome, setNome] = useState('')
  const [email, setEmail] = useState('')
  const [erro, setErro] = useState('')
  const [criado, setCriado] = useState<RespostaMembroAdicionado | null>(null)

  const { data: membros = [], isError: erroAoListar } = useQuery({ queryKey: ['membros'], queryFn: listarMembros })

  const mutation = useMutation({
    mutationFn: adicionarMembro,
    onSuccess: dados => {
      setCriado(dados)
      setNome('')
      setEmail('')
      qc.invalidateQueries({ queryKey: ['membros'] })
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

  return (
    <div className="card space-y-5">
      <div className="flex items-center gap-2">
        <UserPlus size={16} className="text-acento" />
        <h2 className="text-sm font-semibold text-conteudo">Membros</h2>
      </div>

      {erroAoListar && (
        <p className="text-sm text-red-500">Não foi possível carregar os membros.</p>
      )}

      {membros.length > 0 && (
        <div className="divide-y divide-borda">
          {membros.map(m => (
            <div key={m.usuarioId} className="flex items-center gap-3 py-2.5">
              <div className="w-8 h-8 rounded-full bg-superficie-2 text-conteudo flex items-center justify-center text-xs font-semibold shrink-0">
                {iniciaisDoNome(m.nome)}
              </div>
              <div className="min-w-0 flex-1">
                <p className="text-sm text-conteudo truncate">{m.nome}</p>
                <p className="text-xs text-conteudo-suave truncate">{m.email}</p>
              </div>
              <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${
                m.papel === 'DONO' ? 'text-pastel-lilas-texto bg-pastel-lilas' : 'text-pastel-azul-texto bg-pastel-azul'
              }`}>
                {m.papel === 'DONO' ? 'Dono' : 'Membro'}
              </span>
            </div>
          ))}
        </div>
      )}

      <form onSubmit={handleSubmit} className="space-y-4 max-w-sm">
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

        {erro && <p className="text-sm text-red-500">{erro}</p>}

        <button type="submit" disabled={mutation.isPending} className="btn-primary flex items-center gap-2">
          <UserPlus size={16} /> {mutation.isPending ? 'Adicionando...' : 'Adicionar membro'}
        </button>
      </form>

      {criado && (
        <div className="rounded-xl border border-emerald-400/30 bg-emerald-400/10 p-4">
          <p className="text-sm text-emerald-600 dark:text-emerald-400 font-medium mb-2">
            Membro criado! Repasse a senha temporária abaixo — ela não será exibida novamente.
          </p>
          <div className="flex items-center gap-2 bg-superficie-2 rounded-lg px-3 py-2">
            <code className="text-conteudo font-mono text-sm flex-1">{criado.senhaTemporaria}</code>
            <button type="button" onClick={() => navigator.clipboard.writeText(criado.senhaTemporaria)}
              className="btn-ghost p-1.5">
              <Copy size={14} />
            </button>
          </div>
          <p className="text-xs text-conteudo-suave mt-2">
            {criado.nome} ({criado.email}) precisará trocar essa senha no primeiro acesso.
          </p>
        </div>
      )}
    </div>
  )
}

export default function Perfil() {
  const sessao = useLojaAutenticacao(s => s.sessao)

  return (
    <div className="p-6 space-y-5 max-w-2xl">
      <div>
        <h1 className="text-2xl font-bold text-conteudo">Perfil</h1>
        <p className="text-sm text-conteudo-suave mt-0.5">Suas informações, aparência e segurança.</p>
      </div>

      <SecaoInformacoes />
      <SecaoAparencia />
      <SecaoTrocarSenha />
      {sessao?.papel === 'DONO' && <SecaoMembros />}
    </div>
  )
}
