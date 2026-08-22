import { useQuery } from '@tanstack/react-query'
import { listarFiliais } from '../../api/filiais'
import { useLojaFilialAtual } from '../../store/lojaFilialAtual'

interface Props {
  value: number | null | undefined
  onChange: (v: number | null) => void
  disabled?: boolean
}

export default function CampoFilial({ value, onChange, disabled }: Props) {
  const { data: filiais } = useQuery({ queryKey: ['filiais'], queryFn: listarFiliais })
  const { filialAtualId } = useLojaFilialAtual()

  if (!filiais || filiais.length < 2) return null

  const valorResolvido = value === undefined
    ? (filialAtualId ?? null)
    : value

  return (
    <div>
      <label className="label">Filial</label>
      <select
        value={valorResolvido ?? ''}
        onChange={e => onChange(e.target.value === '' ? null : Number(e.target.value))}
        disabled={disabled}
        className="input w-full"
      >
        <option value="">Global (sem filial)</option>
        {filiais.map(e => (
          <option key={e.id} value={e.id}>{e.nome}</option>
        ))}
      </select>
    </div>
  )
}
