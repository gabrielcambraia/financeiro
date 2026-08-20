import { useQuery } from '@tanstack/react-query'
import { listarEntidades } from '../../api/entidades'
import { useLojaEntidadeAtual } from '../../store/lojaEntidadeAtual'

interface Props {
  value: number | null | undefined
  onChange: (v: number | null) => void
  disabled?: boolean
}

export default function CampoEntidade({ value, onChange, disabled }: Props) {
  const { data: entidades } = useQuery({ queryKey: ['entidades'], queryFn: listarEntidades })
  const { entidadeAtualId } = useLojaEntidadeAtual()

  if (!entidades || entidades.length < 2) return null

  const valorResolvido = value === undefined
    ? (entidadeAtualId ?? null)
    : value

  return (
    <div>
      <label className="label">Entidade</label>
      <select
        value={valorResolvido ?? ''}
        onChange={e => onChange(e.target.value === '' ? null : Number(e.target.value))}
        disabled={disabled}
        className="input w-full"
      >
        <option value="">Global (sem entidade)</option>
        {entidades.map(e => (
          <option key={e.id} value={e.id}>{e.nome}</option>
        ))}
      </select>
    </div>
  )
}
