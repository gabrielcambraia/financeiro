import { useState, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { toast } from 'sonner'
import { X, Repeat } from 'lucide-react'
import { format } from 'date-fns'
import { buscarContas } from '../../api/contas'
import { buscarCategorias } from '../../api/categorias'
import { buscarCentrosCusto } from '../../api/centrosCusto'
import { buscarCartoes } from '../../api/cartoes'
import { criarTransacao, atualizarTransacao, converterTransacaoParaCartao, type EscopoAtualizacao } from '../../api/transacoes'
import { criarItemFatura, atualizarItemFatura, converterItemParaDebito } from '../../api/itensFatura'
import SobreposicaoModal from '../SobreposicaoModal'
import SeletorFormaPagamento from './SeletorFormaPagamento'
import type { Transacao, ItemFatura, TipoPagamento } from '../../types'

// As telas de Lançamentos (Pagamentos/Recebimentos) mesclam dois tipos de
// registro: Transacao (débito, amarrada a uma conta) e ItemFatura (crédito,
// amarrado a um cartão, sem vencimento/pagamento — vira despesa na conta de
// pagamento só quando a fatura fecha). Este form decide qual API chamar
// conforme o tipo/forma de pagamento escolhidos.
export type EdicaoLancamento =
  | { origem: 'TRANSACAO'; tx: Transacao }
  | { origem: 'ITEM_FATURA'; item: ItemFatura }

const fmtParcela = (valor: string | number, totalParcelas: string | number) => {
  const n = Number(totalParcelas)
  const v = Number(valor) / n
  return new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(v || 0)
}

interface Props {
  // Tipo do lançamento a criar — determinado pelo contexto de onde o popup
  // foi aberto (tela de Pagamentos ou de Recebimentos), nunca escolhido
  // dentro do form. Ignorado quando `editing` está presente.
  tipo: 'DESPESA' | 'RECEITA'
  onClose: () => void
  editing?: EdicaoLancamento
}

export default function FormularioTransacao({ tipo: tipoProp, onClose, editing }: Props) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const editingTx = editing?.origem === 'TRANSACAO' ? editing.tx : undefined
  const editingItem = editing?.origem === 'ITEM_FATURA' ? editing.item : undefined

  // O tipo é fixo: na edição vem do lançamento existente (ItemFatura é
  // sempre despesa), na criação vem da tela de origem.
  const tipo = editingTx?.tipo ?? (editingItem ? 'DESPESA' : tipoProp)
  const hoje = format(new Date(), 'yyyy-MM-dd')
  const dataInicial = editingTx?.data ?? editingItem?.data ?? hoje
  const [form, setForm] = useState({
    contaId: editingTx?.contaId ?? '',
    cartaoId: editingItem?.cartaoId ?? '',
    categoriaId: editingTx?.categoriaId ?? editingItem?.categoriaId ?? '',
    tipoPagamento: (editingTx?.tipoPagamento ?? (editingItem ? 'CREDITO' : 'DEBITO')) as TipoPagamento,
    valor: editingTx?.valor ?? editingItem?.valor ?? '',
    descricao: editingTx?.descricao ?? editingItem?.descricao ?? '',
    data: dataInicial,
    dataVencimento: editingTx?.dataVencimento ?? dataInicial,
    // Vazio = pendente. Ao criar, nasce preenchida se a data já chegou (mantém
    // o fluxo leve de hoje); o usuário pode desmarcar para deixar a pagar.
    dataPagamento: editingTx ? (editingTx.dataPagamento ?? '') : (dataInicial <= hoje ? dataInicial : ''),
    debitoAutomatico: editingTx?.debitoAutomatico ?? false,
    totalParcelas: editingTx?.totalParcelas ?? editingItem?.totalParcelas ?? '',
    centroCustoId: (editingTx?.centroCustoId ?? editingItem?.centroCustoId ?? '') as number | '',
  })
  const paga = form.dataPagamento !== ''
  // Guards de elegibilidade para conversão entre débito e crédito.
  const podeConverterParaCredito = !!(
    editingTx &&
    editingTx.tipo === 'DESPESA' &&
    editingTx.tipoPagamento === 'DEBITO' &&
    !editingTx.origemDerivada &&
    !editingTx.grupoParcelaId &&
    editingTx.status !== 'CANCELADA'
  )
  const podeConverterParaDebito = !!(
    editingItem &&
    !editingItem.dataCancelamento &&
    !editingItem.grupoParcelaId &&
    editingItem.faturaStatus !== 'PAGA' &&
    editingItem.faturaStatus !== 'CANCELADA'
  )

  // Modo crédito: ItemFatura form vs Transacao form.
  // Para editingTx, só entra em modo crédito se elegível E o toggle foi
  // trocado; legado com tipoPagamento=CREDITO não é elegível e fica em débito.
  const credito = editingTx
    ? (podeConverterParaCredito && form.tipoPagamento === 'CREDITO')
    : (tipo === 'DESPESA' && form.tipoPagamento === 'CREDITO')

  const convertendoParaCredito = !!(editingTx && credito)
  const convertendoParaDebito = !!(editingItem && !credito)

  const { data: contas = [] } = useQuery({ queryKey: ['contas'], queryFn: buscarContas })
  const { data: cartoes = [] } = useQuery({ queryKey: ['cartoes'], queryFn: buscarCartoes, enabled: credito || !!editingItem })

  useEffect(() => {
    if (!editing && contas.length === 1 && !form.contaId) {
      set('contaId', contas[0].id)
    }
  }, [contas])

  useEffect(() => {
    if (!editing && credito && cartoes.length === 1 && !form.cartaoId) {
      set('cartaoId', cartoes[0].id)
    }
  }, [credito, cartoes])

  const { data: categorias = [] } = useQuery({
    queryKey: ['categorias', tipo],
    queryFn: () => buscarCategorias(tipo),
  })

  const { data: centrosCusto = [] } = useQuery({
    queryKey: ['centros-custo'],
    queryFn: buscarCentrosCusto,
  })

  const invalidarTudo = async () => {
    await Promise.all([
      qc.invalidateQueries({ queryKey: ['transacoes'] }),
      qc.invalidateQueries({ queryKey: ['itensFatura'] }),
      qc.invalidateQueries({ queryKey: ['faturas'] }),
      qc.invalidateQueries({ queryKey: ['painel'] }),
      qc.invalidateQueries({ queryKey: ['contas'] }),
      qc.invalidateQueries({ queryKey: ['cartoes'] }),
    ])
  }

  const mutation = useMutation({
    mutationFn: async () => {
      if (convertendoParaCredito) {
        await converterTransacaoParaCartao(editingTx!.id, {
          cartaoId: Number(form.cartaoId),
          escopo: editingTx!.grupoParcelaId ? escopoEdicao : 'UNICA',
        })
        return 'convertido'
      }
      if (convertendoParaDebito) {
        await converterItemParaDebito(editingItem!.id, {
          contaId: Number(form.contaId),
          escopo: editingItem!.grupoParcelaId ? escopoEdicao : 'UNICA',
          quitarNaCriacao: paga,
          dataPagamento: paga ? (form.dataPagamento || form.data) : undefined,
        })
        return 'convertido'
      }
      if (credito) {
        const payload = {
          cartaoId: Number(form.cartaoId),
          categoriaId: form.categoriaId ? Number(form.categoriaId) : undefined,
          valor: Number(form.valor),
          descricao: form.descricao || undefined,
          data: form.data,
          totalParcelas: editingItem ? undefined : (form.totalParcelas ? Number(form.totalParcelas) : undefined),
          centroCustoId: form.centroCustoId ? Number(form.centroCustoId) : null,
        }
        if (editingItem) { await atualizarItemFatura(editingItem.id, payload); return 'salvo' }
        await criarItemFatura(payload)
        return 'salvo'
      }
      const payload = {
        contaId: Number(form.contaId),
        categoriaId: form.categoriaId ? Number(form.categoriaId) : undefined,
        tipo,
        tipoPagamento: form.tipoPagamento,
        valor: Number(form.valor),
        descricao: form.descricao || undefined,
        data: form.data,
        dataVencimento: form.dataVencimento || form.data,
        dataPagamento: paga ? (form.dataPagamento || form.data) : undefined,
        quitarNaCriacao: paga,
        fixa: false,
        debitoAutomatico: tipo === 'DESPESA' ? form.debitoAutomatico : false,
        totalParcelas: form.totalParcelas ? Number(form.totalParcelas) : undefined,
        centroCustoId: form.centroCustoId ? Number(form.centroCustoId) : null,
      }
      if (editingTx) { await atualizarTransacao(editingTx.id, payload, editingTx.grupoParcelaId ? escopoEdicao : 'UNICA'); return 'salvo' }
      await criarTransacao(payload)
      return 'salvo'
    },
    onSuccess: async (resultado) => {
      await invalidarTudo()
      toast.success(resultado === 'convertido' ? 'Lançamento convertido com sucesso' : 'Transação salva')
      onClose()
    },
    onError: (e: unknown) => {
      const msg = (e as { response?: { data?: { mensagem?: string } } })?.response?.data?.mensagem
      setErro(msg ?? 'Erro ao salvar. Tente novamente.')
    },
  })

  const [escopoEdicao, setEscopoEdicao] = useState<EscopoAtualizacao>('UNICA')
  const [erro, setErro] = useState('')

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    mutation.mutate()
  }

  const set = (k: string, v: unknown) => setForm(f => ({ ...f, [k]: v }))

  const corTipo = tipo === 'DESPESA' ? 'text-red-500' : 'text-emerald-500'
  const tituloTipo = tipo === 'DESPESA' ? 'despesa' : 'receita'

  return (
    <SobreposicaoModal aoFechar={onClose}>
      <div className="cartao-modal max-w-lg">
        <div className="cartao-modal-cabecalho">
          <h2 className="text-lg font-semibold text-conteudo">
            {editing ? 'Editar ' : 'Nova '}<span className={corTipo}>{tituloTipo}</span>
          </h2>
          <button onClick={onClose} className="btn-ghost p-1.5"><X size={18} /></button>
        </div>

        <form onSubmit={handleSubmit} className="cartao-modal-corpo">
          {(editingTx?.origemRecorrenciaId || editingItem?.origemRecorrenciaId) && (
            <div className="flex items-start gap-2 p-3 rounded-xl bg-acento/10 border border-acento/30">
              <Repeat size={14} className="text-acento shrink-0 mt-0.5" />
              <span className="text-sm text-conteudo flex-1">
                Lançamento gerado por uma recorrência. Editar aqui altera <strong>apenas este mês</strong>.
              </span>
              <button type="button" onClick={() => { onClose(); navigate('/lancamentos/recorrencias') }}
                className="text-xs text-acento hover:opacity-80 shrink-0 font-medium whitespace-nowrap">
                Ir para recorrências →
              </button>
            </div>
          )}

          {/* Débito / Crédito — só se aplica a despesa. Na edição, só
              habilitado para conversões elegíveis. */}
          {tipo === 'DESPESA' && (
            <SeletorFormaPagamento
              valor={form.tipoPagamento}
              aoTrocar={p => set('tipoPagamento', p)}
              desabilitado={!!editing && !podeConverterParaCredito && !podeConverterParaDebito}
              aviso={
                convertendoParaCredito
                  ? 'Selecione o cartão abaixo — o lançamento será convertido para crédito.'
                  : convertendoParaDebito
                    ? 'Selecione a conta abaixo — o item será convertido para débito.'
                    : undefined
              }
            />
          )}

          {/* Conta/Cartão e Categoria */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="label">{credito ? 'Cartão' : 'Conta'}</label>
              {credito ? (
                <select
                  className="select" value={form.cartaoId} onChange={e => set('cartaoId', e.target.value)}
                  disabled={!!editingItem} required
                >
                  <option value="">Selecione...</option>
                  {[...cartoes].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
                </select>
              ) : (
                <select
                  className="select" value={form.contaId} onChange={e => set('contaId', e.target.value)}
                  required
                >
                  <option value="">Selecione...</option>
                  {[...contas].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
                </select>
              )}
            </div>
            <div>
              <label className="label">Categoria</label>
              <select className="select" value={form.categoriaId} onChange={e => set('categoriaId', e.target.value)}>
                <option value="">Sem categoria</option>
                {[...categorias].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(c => <option key={c.id} value={c.id}>{c.nome}</option>)}
              </select>
            </div>
          </div>
          {centrosCusto.length > 0 && (
            <div>
              <label className="label">Centro de Custo</label>
              <select className="select" value={form.centroCustoId} onChange={e => set('centroCustoId', e.target.value ? Number(e.target.value) : '')}>
                <option value="">Sem centro de custo</option>
                {[...centrosCusto].sort((a, b) => a.nome.localeCompare(b.nome, 'pt-BR')).map(cc => (
                  <option key={cc.id} value={cc.id}>{cc.nome}</option>
                ))}
              </select>
            </div>
          )}

          {erro && <p className="text-sm text-red-500">{erro}</p>}

          {/* Valor e Data */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="label">Valor (R$)</label>
              <input
                className="input" type="number" step="0.01" min="0.01" placeholder="0,00"
                value={form.valor} onChange={e => set('valor', e.target.value)} required
              />
            </div>
            <div>
              <label className="label">Data</label>
              <input
                className="input" type="date"
                value={form.data} onChange={e => set('data', e.target.value)} required
              />
            </div>
          </div>

          {/* Vencimento e pagamento — não se aplica a compras no crédito:
              ficam atreladas ao cartão, sem vencimento nem pagamento próprios,
              até a fatura fechar (vira uma despesa em débito na conta do cartão). */}
          {!credito && (
            <>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="label">Vencimento</label>
                  <input
                    className="input" type="date"
                    value={form.dataVencimento} onChange={e => set('dataVencimento', e.target.value)} required
                  />
                </div>
                {paga && (
                  <div>
                    <label className="label">Data de pagamento</label>
                    <input
                      className="input" type="date" max={hoje}
                      value={form.dataPagamento} onChange={e => set('dataPagamento', e.target.value)}
                    />
                  </div>
                )}
              </div>

              <div className="flex items-center gap-3 p-3 rounded-xl bg-superficie-2">
                <input
                  id="paga" type="checkbox"
                  checked={paga}
                  onChange={e => set('dataPagamento', e.target.checked ? (form.dataPagamento || (form.data <= hoje ? form.data : hoje)) : '')}
                  className="w-4 h-4 accent-acento"
                />
                <label htmlFor="paga" className="text-sm text-conteudo">
                  {tipo === 'RECEITA' ? 'Já foi recebida' : 'Já foi paga'}
                  <span className="block text-xs text-conteudo-suave">Desmarque para deixar como pendente (não afeta o saldo até ser paga)</span>
                </label>
              </div>
            </>
          )}

          {credito && (
            <p className="text-xs text-conteudo-suave px-1">
              Essa compra entra na fatura do cartão. No fechamento, vira uma despesa em débito na conta de pagamento do cartão.
            </p>
          )}

          {/* Descrição */}
          <div>
            <label className="label">Descrição</label>
            <input
              className="input" placeholder="Ex: Mercado, Aluguel..."
              value={form.descricao} onChange={e => set('descricao', e.target.value)}
            />
          </div>

          {/* Parcelada — não se aplica à edição de uma compra já no cartão */}
          {!editing && (
            <div>
              <label className="label">Parcelar em quantas vezes? (deixe em branco = à vista)</label>
              <input
                className="input" type="number" min="2" max="60" placeholder="Ex: 3"
                value={form.totalParcelas}
                onChange={e => set('totalParcelas', e.target.value)}
              />
              {!!form.totalParcelas && Number(form.totalParcelas) > 1 && (
                <p className="text-xs text-conteudo-suave mt-1">
                  O valor acima é o total da compra — cada parcela fica em {fmtParcela(form.valor, form.totalParcelas)}.
                </p>
              )}
            </div>
          )}

          {tipo === 'DESPESA' && form.tipoPagamento === 'DEBITO' && !editingItem && (
            <div className="flex items-center gap-3 p-3 rounded-xl bg-superficie-2">
              <input
                id="debitoAutomatico" type="checkbox"
                checked={form.debitoAutomatico}
                onChange={e => set('debitoAutomatico', e.target.checked)}
                className="w-4 h-4 accent-acento"
              />
              <label htmlFor="debitoAutomatico" className="text-sm text-conteudo">
                Débito automático (quitar no vencimento)
              </label>
            </div>
          )}

          {(editingTx?.grupoParcelaId || (editingItem?.grupoParcelaId && convertendoParaDebito)) && (
            <div className="space-y-2 p-3 rounded-xl bg-superficie-2">
              <label className="text-sm font-medium text-conteudo">
                {convertendoParaCredito || convertendoParaDebito ? 'Converter em:' : 'Aplicar alteração em:'}
              </label>
              <div className="flex gap-2">
                {(['UNICA', 'FUTURAS'] as EscopoAtualizacao[]).map(s => (
                  <button
                    key={s}
                    type="button"
                    onClick={() => setEscopoEdicao(s)}
                    className={`flex-1 py-2 text-sm rounded-lg border transition-colors
                      ${escopoEdicao === s
                        ? 'border-acento bg-acento/20 text-acento'
                        : 'border-borda text-conteudo-suave hover:border-conteudo-suave'}`}
                  >
                    {s === 'UNICA' ? 'Só este mês' : 'Este e os próximos'}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="flex gap-3 pt-2">
            <button type="button" onClick={onClose} className="flex-1 py-2.5 rounded-lg border border-borda text-conteudo-suave hover:text-conteudo hover:border-conteudo-suave transition-colors text-sm font-medium">
              Cancelar
            </button>
            <button type="submit" disabled={mutation.isPending} className="flex-1 btn-primary">
              {mutation.isPending ? 'Salvando...' : 'Salvar'}
            </button>
          </div>
        </form>
      </div>
    </SobreposicaoModal>
  )
}
