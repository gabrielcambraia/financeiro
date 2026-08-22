import { useState } from 'react'
import { useParams, useNavigate, Link } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { ChevronLeft, Copy, Check } from 'lucide-react'
import { IMaskInput } from 'react-imask'
import { listarUsuarios, criarUsuario, atualizarUsuario, type UsuarioDoEspaco, type UsuarioCriado } from '../api/usuarios'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import type { PapelUsuario } from '../types'

const PAPEIS: { value: PapelUsuario; label: string }[] = [
  { value: 'DONO', label: 'Dono' },
  { value: 'MEMBRO', label: 'Usuário' },
]

const formPadrao = { nome: '', email: '', telefone: '', papel: 'MEMBRO' as PapelUsuario }

function CardSenhaTemporaria({ usuario }: { usuario: UsuarioCriado }) {
  const [copiado, setCopiado] = useState(false)

  const copiar = async () => {
    try {
      await navigator.clipboard.writeText(usuario.senhaTemporaria)
      setCopiado(true)
      setTimeout(() => setCopiado(false), 2000)
    } catch {
      toast.error('Não foi possível copiar')
    }
  }

  return (
    <div className="card space-y-3">
      <h2 className="text-sm font-semibold text-conteudo">Usuário criado</h2>
      <p className="text-sm text-conteudo-suave">
        Repasse esta senha temporária para <strong className="text-conteudo">{usuario.nome}</strong> — ela só é
        exibida uma vez e precisará ser trocada no primeiro acesso.
      </p>
      <div className="flex items-center gap-2 bg-superficie-2 rounded-lg px-3 py-2.5">
        <code className="flex-1 text-sm font-mono text-conteudo select-all">{usuario.senhaTemporaria}</code>
        <button onClick={copiar} className="btn-ghost p-1.5 text-sm shrink-0" title="Copiar">
          {copiado ? <Check size={15} className="text-green-500" /> : <Copy size={15} />}
        </button>
      </div>
    </div>
  )
}

/**
 * Formulário propriamente dito. Recebe `editando` já resolvido (ou
 * `undefined` no modo criação) e inicializa o estado local a partir dele —
 * o componente pai remonta este aqui (via `key`) quando o usuário editado
 * muda, em vez de sincronizar via useEffect.
 */
function FormularioUsuario({ usuarioId, editando, euMesmo }: {
  usuarioId: number | null
  editando?: UsuarioDoEspaco
  euMesmo: boolean
}) {
  const navigate = useNavigate()
  const qc = useQueryClient()
  const [form, setForm] = useState(() => editando
    ? { nome: editando.nome, email: editando.email, telefone: editando.telefone ?? '', papel: editando.papel }
    : formPadrao)
  const [usuarioCriado, setUsuarioCriado] = useState<UsuarioCriado | null>(null)

  const criarMutation = useMutation({
    mutationFn: criarUsuario,
    onSuccess: async u => {
      await qc.invalidateQueries({ queryKey: ['usuarios'] })
      setUsuarioCriado(u)
    },
    onError: () => toast.error('Não foi possível criar o usuário'),
  })

  const atualizarMutation = useMutation({
    mutationFn: (dados: typeof form) => atualizarUsuario(usuarioId!, dados),
    onSuccess: async () => {
      await qc.invalidateQueries({ queryKey: ['usuarios'] })
      toast.success('Usuário atualizado')
      navigate('/usuarios')
    },
    onError: (e: unknown) => {
      const err = e as { response?: { data?: { mensagem?: string } } }
      toast.error(err?.response?.data?.mensagem ?? 'Não foi possível salvar')
    },
  })

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (usuarioId != null) atualizarMutation.mutate(form)
    else criarMutation.mutate({ ...form, telefone: form.telefone || undefined })
  }

  const salvando = criarMutation.isPending || atualizarMutation.isPending

  if (usuarioCriado) {
    return (
      <>
        <CardSenhaTemporaria usuario={usuarioCriado} />
        <button onClick={() => navigate('/usuarios')} className="w-full btn-primary">Voltar para Usuários</button>
      </>
    )
  }

  return (
    <form onSubmit={handleSubmit} className="card space-y-4">
      <div>
        <label className="label">Nome</label>
        <input className="input" required
          value={form.nome} onChange={e => setForm(f => ({ ...f, nome: e.target.value }))} />
      </div>
      <div>
        <label className="label">E-mail</label>
        <input type="email" className="input" required
          value={form.email} onChange={e => setForm(f => ({ ...f, email: e.target.value }))} />
      </div>
      <div>
        <label className="label">Telefone</label>
        <IMaskInput
          mask={[{ mask: '(00) 0000-0000' }, { mask: '(00) 00000-0000' }]}
          unmask={true}
          className="input"
          value={form.telefone}
          onAccept={val => setForm(f => ({ ...f, telefone: val as string }))}
        />
      </div>
      <div>
        <label className="label">Papel</label>
        <select className="input" value={form.papel} disabled={euMesmo}
          onChange={e => setForm(f => ({ ...f, papel: e.target.value as PapelUsuario }))}>
          {PAPEIS.map(p => (
            <option key={p.value} value={p.value}>{p.label}</option>
          ))}
        </select>
        {euMesmo && (
          <p className="text-xs text-conteudo-suave mt-1">Não é possível alterar o próprio papel.</p>
        )}
      </div>
      <div className="flex gap-3 pt-2">
        <Link to="/usuarios"
          className="flex-1 py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo transition-colors text-sm font-medium text-center">
          Cancelar
        </Link>
        <button type="submit" disabled={salvando} className="flex-1 btn-primary">
          {salvando ? 'Salvando...' : 'Salvar'}
        </button>
      </div>
    </form>
  )
}

export default function UsuarioForm() {
  const { id } = useParams()
  const usuarioId = id ? Number(id) : null
  const sessaoId = useLojaAutenticacao(s => s.sessao?.usuarioId)
  const euMesmo = usuarioId != null && usuarioId === sessaoId

  const { data: usuarios = [], isLoading } = useQuery({ queryKey: ['usuarios'], queryFn: listarUsuarios })
  const editando = usuarioId != null ? usuarios.find(u => u.id === usuarioId) : undefined

  // Em modo edição, espera a lista carregar antes de montar o formulário —
  // é dela que vêm os valores iniciais (não existe GET /usuarios/{id}).
  const aguardandoDados = usuarioId != null && isLoading

  return (
    <div className="p-4 md:p-6 space-y-5 max-w-lg">
      <div className="flex items-center gap-3">
        <Link to="/usuarios" className="p-1.5 rounded-lg hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
          <ChevronLeft size={20} />
        </Link>
        <h1 className="text-xl font-bold text-conteudo">
          {usuarioId != null ? 'Editar usuário' : 'Adicionar usuário'}
        </h1>
      </div>

      {aguardandoDados ? (
        <p className="text-sm text-conteudo-suave">Carregando...</p>
      ) : (
        <FormularioUsuario key={editando?.id ?? 'novo'} usuarioId={usuarioId} editando={editando} euMesmo={euMesmo} />
      )}
    </div>
  )
}
