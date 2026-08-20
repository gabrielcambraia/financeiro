import type { TipoPagamento } from '../../types'

interface Props {
  valor: TipoPagamento
  aoTrocar: (valor: TipoPagamento) => void
  desabilitado?: boolean
  aviso?: string
}

// Widget de Débito/Crédito compartilhado entre o popup de lançamento
// (FormularioTransacao) e o formulário de recorrência — só aparece para
// despesa, já que receita e transferência não fazem sentido em crédito.
export default function SeletorFormaPagamento({ valor, aoTrocar, desabilitado, aviso }: Props) {
  return (
    <div>
      <label className="label">Forma de pagamento</label>
      {aviso && <p className="text-xs text-acento mb-1.5">{aviso}</p>}
      <div className="flex gap-2">
        {(['DEBITO', 'CREDITO'] as TipoPagamento[]).map(p => (
          <button
            key={p} type="button"
            disabled={desabilitado}
            onClick={() => aoTrocar(p)}
            className={`flex-1 py-2 text-sm rounded-lg border transition-colors
              ${valor === p
                ? 'border-acento bg-acento/20 text-acento'
                : 'border-borda text-conteudo-suave hover:border-conteudo-suave'}
              ${desabilitado ? 'opacity-60 cursor-not-allowed' : ''}`}
          >
            {p === 'DEBITO' ? 'Débito' : 'Crédito'}
          </button>
        ))}
      </div>
    </div>
  )
}
