import { useNavigate, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Users, Lock, Pencil } from 'lucide-react'
import { listarUsuarios, type UsuarioDoEspaco } from '../api/usuarios'
import { iniciaisDoNome } from '../utils/formatadores'
import AcaoNova from '../components/AcaoNova'
import type { PapelUsuario } from '../types'

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

function LinhaUsuario({ usuario }: { usuario: UsuarioDoEspaco }) {
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
        <BadgePapel papel={usuario.papel} />
      </td>
      <td className="py-3 pr-4">
        <div className="flex items-center gap-1.5 text-xs text-conteudo-suave">
          <Lock size={12} />
          <span>Permissões por tela — em breve</span>
        </div>
      </td>
      <td className="py-3 text-right">
        <Link to={`/usuarios/${usuario.id}`}
          className="inline-flex p-1.5 rounded hover:bg-superficie-2 text-conteudo-suave hover:text-conteudo transition-colors">
          <Pencil size={14} />
        </Link>
      </td>
    </tr>
  )
}

export default function Usuarios() {
  const navigate = useNavigate()
  const { data: usuarios = [], isLoading } = useQuery({
    queryKey: ['usuarios'],
    queryFn: listarUsuarios,
  })

  return (
    <div className="p-6 space-y-5 max-w-3xl">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-conteudo">Usuários</h1>
          <p className="text-sm text-conteudo-suave mt-0.5">Gerencie os usuários do seu espaço.</p>
        </div>
        <AcaoNova aoClicar={() => navigate('/usuarios/novo')} rotulo="Adicionar usuário" />
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
                <th className="text-left text-xs font-medium text-conteudo-suave pb-2 pr-4">Permissões</th>
                <th className="pb-2"></th>
              </tr>
            </thead>
            <tbody>
              {usuarios.map(u => (
                <LinhaUsuario key={u.id} usuario={u} />
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  )
}
