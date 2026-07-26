import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Search, X } from 'lucide-react'
import { buscarBancos } from '../api/bancos'
import SobreposicaoModal from './SobreposicaoModal'
import LogoBanco from './LogoBanco'

interface Props {
  bancoSelecionado?: number
  aoSelecionar: (id: number | undefined) => void
}

export default function SeletorBanco({ bancoSelecionado, aoSelecionar }: Props) {
  const [aberto, setAberto] = useState(false)
  const [busca, setBusca] = useState('')
  const { data: bancos = [] } = useQuery({ queryKey: ['bancos'], queryFn: buscarBancos })

  const atual = bancos.find(b => b.id === bancoSelecionado)
  const filtrados = bancos.filter(b => b.nome.toLowerCase().includes(busca.toLowerCase()))

  return (
    <>
      <button type="button" onClick={() => setAberto(true)} className="input flex items-center gap-3 text-left">
        <LogoBanco banco={atual} tamanho={28} />
        <span className={atual ? 'text-conteudo' : 'text-conteudo-suave'}>
          {atual ? atual.nome : 'Selecione o banco (opcional)'}
        </span>
      </button>

      {aberto && (
        <SobreposicaoModal aoFechar={() => setAberto(false)}>
          <div className="cartao-modal max-w-lg">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">Selecione o banco</h2>
              <button onClick={() => setAberto(false)} className="btn-ghost p-1.5"><X size={18} /></button>
            </div>
            <div className="cartao-modal-corpo">
              <div className="relative">
                <Search size={16} className="absolute left-3 top-1/2 -translate-y-1/2 text-conteudo-suave" />
                <input
                  className="input pl-9" placeholder="Buscar banco..." autoFocus
                  value={busca} onChange={e => setBusca(e.target.value)}
                />
              </div>

              <div className="grid grid-cols-3 sm:grid-cols-4 gap-2 max-h-80 overflow-y-auto pr-1">
                <button
                  type="button"
                  onClick={() => { aoSelecionar(undefined); setAberto(false) }}
                  className="flex flex-col items-center gap-1.5 p-3 rounded-xl border border-borda hover:border-conteudo-suave transition-colors"
                >
                  <div className="w-10 h-10 rounded-xl bg-superficie-2 flex items-center justify-center text-conteudo-suave text-xs">—</div>
                  <span className="text-xs text-conteudo-suave text-center">Nenhum</span>
                </button>
                {filtrados.map(b => (
                  <button
                    key={b.id}
                    type="button"
                    onClick={() => { aoSelecionar(b.id); setAberto(false) }}
                    className={`flex flex-col items-center gap-1.5 p-3 rounded-xl border transition-colors
                      ${bancoSelecionado === b.id ? 'border-acento bg-acento/10' : 'border-borda hover:border-conteudo-suave'}`}
                  >
                    <LogoBanco banco={b} tamanho={40} />
                    <span className="text-xs text-conteudo text-center leading-tight">{b.nome}</span>
                  </button>
                ))}
                {filtrados.length === 0 && (
                  <p className="col-span-full text-center text-sm text-conteudo-suave py-6">Nenhum banco encontrado.</p>
                )}
              </div>
            </div>
          </div>
        </SobreposicaoModal>
      )}
    </>
  )
}
