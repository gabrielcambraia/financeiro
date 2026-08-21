import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { Users, Lock } from 'lucide-react'
import { listarUsuarios, alterarPapel, type UsuarioDoEspaco } from '../api/usuarios'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import { iniciaisDoNome } from '../utils/formatadores'
import type { PapelUsuario } from '../types'

const PAPEIS: { value: PapelUsuario; label: string }[] = [
  { value: 'DONO', label: 'Dono' },
  { value: 'MEMBRO', label: 'Usuário' },
]

function BadgePapel({ papel }: { papel: PapelUsuario }) {
  return (
    <span className={`text-xs font-medium px-2 py-0.5 rounded-full ${
      papel === 'DONO'
        ? 'text-pastel-lilas-texto bg-pastel-lilas'
        : 'text-pastel-azul-texto bg-pastel-azul'
    }`}>
      {papel === 'DONO' ? 'Dono' : 'Usuário'}
    </span>
  )
}

function LinhaUsuario({ usuario, euId }: { usuario: UsuarioDoEspaco; euId: number | undefined }) {
  const qc = useQueryClient()
  const [papel, setPapel] = useState<PapelUsuario>(usuario.papel)
  const euMesmo = usuario.id === euId

  const mutation = useMutation({
    mutationFn: (novoPapel: PapelUsuario) => alterarPapel(usuario.id, novoPapel),
    onSuccess: u => {
      setPapel(u.papel)
      qc.invalidateQueries({ queryKey: ['usuarios'] })
      toast.success('Papel atualizado')
    },
    onError: () => toast.error('Não foi possível alterar o papel'),
  })

  return (
    <tr className="border-b border-borda last:border-0">
      <td className="py-3 pr-4">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-full bg-acento text-white flex items-center justify-center text-xs font-semibold shrink-0">
            {iniciaisDoNome(usuario.nome)}
          </div>
          <div className="min-w-0">
            <p className="text-sm text-conteudo font-medium truncate">{usuario.nome}</p>
            <p className="text-xs text-conteudo-suave truncate">{usuario.email}</p>
          </div>
        </div>
      </td>
      <td className="py-3 pr-4">
        {euMesmo ? (
          <BadgePapel papel={papel} />
        ) : (
          <select
            value={papel}
            disabled={mutation.isPending}
            onChange={e => mutation.mutate(e.target.value as PapelUsuario)}
            className="input py-1 text-sm w-32"
          >
            {PAPEIS.map(p => (
              <option key={p.value} value={p.value}>{p.label}</option>
            ))}
          </select>
        )}
      </td>
      <td className="py-3">
        <div className="flex items-center gap-1.5 text-xs text-conteudo-suave">
          <Lock size={12} />
          <span>Permissões por tela — em breve</span>
        </div>
      </td>
    </tr>
  )
}

export default function Usuarios() {
  const sessaoId = useLojaAutenticacao(s => s.sessao?.usuarioId)
  const { data: usuarios = [], isLoading } = useQuery({
    queryKey: ['usuarios'],
    queryFn: listarUsuarios,
  })

  return (
    <div className="p-6 space-y-5 max-w-3xl">
      <div>
        <h1 className="text-2xl font-bold text-conteudo">Usuários</h1>
        <p className="text-sm text-conteudo-suave mt-0.5">Gerencie os usuários do seu espaço.</p>
      </div>

      <div className="card">
        <div className="flex items-center gap-2 mb-4">
          <Users size={16} className="text-acento" />
          <h2 className="text-sm font-semibold text-conteudo">Usuários do espaço</h2>
        </div>

        {isLoading ? (
          <p className="text-sm text-conteudo-suave">Carregando...</p>
        ) : usuarios.length === 0 ? (
          <p className="text-sm text-conteudo-suave">Nenhum usuário encontrado.</p>
        ) : (
          <table className="w-full">
            <thead>
              <tr className="border-b border-borda">
                <th className="text-left text-xs font-medium text-conteudo-suave pb-2 pr-4">Usuário</th>
                <th className="text-left text-xs font-medium text-conteudo-suave pb-2 pr-4">Papel</th>
                <th className="text-left text-xs font-medium text-conteudo-suave pb-2">Permissões</th>
              </tr>
            </thead>
            <tbody>
              {usuarios.map(u => (
                <LinhaUsuario key={u.id} usuario={u} euId={sessaoId} />
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
