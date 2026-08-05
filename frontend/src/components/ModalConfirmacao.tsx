import type { ReactNode } from 'react'
import SobreposicaoModal from './SobreposicaoModal'
import type { RespostaImpacto } from '../types'

const fmt = (v: number) =>
  new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v)

type Botao = {
  rotulo: string
  variante: 'perigo' | 'atencao' | 'neutro'
  aoClicar: () => void
}

interface Props {
  aberto: boolean
  titulo: string
  aoFechar: () => void
  botoes?: Botao[]
  carregandoImpacto?: boolean
  impacto?: RespostaImpacto | null
  children?: ReactNode
}

export default function ModalConfirmacao({
  aberto, titulo, aoFechar, botoes = [], carregandoImpacto, impacto, children,
}: Props) {
  if (!aberto) return null

  const varianteClasse = (v: Botao['variante']) => {
    if (v === 'perigo') return 'w-full py-2 px-4 rounded-lg border border-red-800 text-red-400 hover:bg-red-900/30 transition-colors text-sm font-medium'
    if (v === 'atencao') return 'w-full py-2 px-4 rounded-lg border border-orange-800 text-orange-500 hover:bg-orange-900/30 transition-colors text-sm font-medium'
    return 'w-full py-2 px-4 rounded-lg border border-borda text-conteudo-suave hover:bg-superficie-2 transition-colors text-sm'
  }

  return (
    <SobreposicaoModal aoFechar={aoFechar}>
      <div className="cartao-modal w-full max-w-md">
        <div className="cartao-modal-cabecalho">
          <span className="font-semibold text-conteudo">{titulo}</span>
        </div>
        <div className="cartao-modal-corpo space-y-4">
          {children && <div className="text-sm text-conteudo-suave">{children}</div>}

          {carregandoImpacto && (
            <p className="text-sm text-conteudo-suave animate-pulse">Verificando impacto...</p>
          )}

          {impacto?.bloqueado && (
            <div className="rounded-lg border border-red-800/50 bg-red-900/20 p-3 text-sm text-red-400">
              {impacto.motivoBloqueio}
            </div>
          )}

          {!impacto?.bloqueado && impacto?.origem && (
            <div className="rounded-lg border border-orange-800/50 bg-orange-900/20 p-3 text-sm text-orange-400">
              ⚠ {impacto.origem.efeito}
            </div>
          )}

          {!impacto?.bloqueado && (impacto?.itensAfetados?.length ?? 0) > 0 && (
            <div className="space-y-1">
              <p className="text-xs font-medium text-conteudo-suave uppercase tracking-wide">
                Lançamentos afetados
              </p>
              <div className="max-h-48 overflow-y-auto space-y-1">
                {impacto!.itensAfetados.map((item, i) => (
                  <div key={i} className="flex items-center justify-between text-xs text-conteudo-suave bg-superficie-2 rounded px-2 py-1">
                    <span className="truncate">{item.descricao}</span>
                    <span className="ml-2 shrink-0 font-medium text-conteudo">{fmt(item.valor)}</span>
                  </div>
                ))}
              </div>
            </div>
          )}

          <div className="space-y-2 pt-1">
            {!impacto?.bloqueado && botoes.map((b, i) => (
              <button key={i} onClick={b.aoClicar} className={varianteClasse(b.variante)}>
                {b.rotulo}
              </button>
            ))}
            <button onClick={aoFechar} className={varianteClasse('neutro')}>
              Voltar
            </button>
          </div>
        </div>
      </div>
    </SobreposicaoModal>
  )
}
