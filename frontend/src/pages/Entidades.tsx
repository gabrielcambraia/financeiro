import { useRef, useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { toast } from 'sonner'
import { IMaskInput } from 'react-imask'
import {
  listarEntidades, criarEntidade, atualizarEntidade, excluirEntidade, buscarAssinatura, type DadosEntidade
} from '../api/entidades'
import { consultarCnpj, consultarCep } from '../api/consultas'
import { validarCpf, validarCnpj, formatarCpf, formatarCnpj } from '../utils/documentos'
import SobreposicaoModal from '../components/SobreposicaoModal'
import ModalConfirmacao from '../components/ModalConfirmacao'
import Spinner from '../components/Spinner'
import type { Assinatura, Entidade, TipoPessoa } from '../types'

function rotuloTipoPessoa(t: TipoPessoa) {
  return t === 'FISICA' ? 'Pessoa Física' : 'Pessoa Jurídica'
}

function formatarDocumento(documento: string, tipoPessoa: TipoPessoa) {
  return tipoPessoa === 'FISICA' ? formatarCpf(documento) : formatarCnpj(documento)
}

const dadosVazios = (): DadosEntidade => ({
  tipoPessoa: 'FISICA',
  nome: '',
  nomeFantasia: '',
  documento: '',
  inscricaoEstadual: '',
  dataNascimento: '',
  email: '',
  telefone: '',
  cep: '',
  logradouro: '',
  numero: '',
  complemento: '',
  bairro: '',
  cidade: '',
  uf: '',
})

export default function Entidades() {
  const qc = useQueryClient()
  const { data: entidades = [], isLoading } = useQuery({ queryKey: ['entidades'], queryFn: listarEntidades })
  const { data: assinatura } = useQuery<Assinatura>({ queryKey: ['assinatura'], queryFn: buscarAssinatura })

  const [modalAberto, setModalAberto] = useState(false)
  const [editando, setEditando] = useState<Entidade | null>(null)
  const [form, setForm] = useState<DadosEntidade>(dadosVazios())
  const [erro, setErro] = useState('')
  const [confirmarExcluir, setConfirmarExcluir] = useState<Entidade | null>(null)
  const [erroCpf, setErroCpf] = useState('')
  const [erroCnpj, setErroCnpj] = useState('')
  const [carregandoCnpj, setCarregandoCnpj] = useState(false)
  const [carregandoCep, setCarregandoCep] = useState(false)
  const numeroRef = useRef<HTMLInputElement>(null)
  const ultimoCnpjBuscado = useRef('')
  const ultimoCepBuscado = useRef('')

  const invalidar = () => {
    qc.invalidateQueries({ queryKey: ['entidades'] })
    qc.invalidateQueries({ queryKey: ['assinatura'] })
  }

  const criar = useMutation({
    mutationFn: criarEntidade,
    onSuccess: () => { invalidar(); toast.success('Entidade salva'); fecharModal() },
    onError: (e: unknown) => {
      const err = e as { response?: { data?: { mensagem?: string } } }
      setErro(err?.response?.data?.mensagem ?? 'Erro ao salvar')
    },
  })

  const atualizar = useMutation({
    mutationFn: ({ id, dados }: { id: number; dados: DadosEntidade }) => atualizarEntidade(id, dados),
    onSuccess: () => { invalidar(); toast.success('Entidade salva'); fecharModal() },
    onError: (e: unknown) => {
      const err = e as { response?: { data?: { mensagem?: string } } }
      setErro(err?.response?.data?.mensagem ?? 'Erro ao salvar')
    },
  })

  const excluir = useMutation({
    mutationFn: excluirEntidade,
    onSuccess: () => { invalidar(); setConfirmarExcluir(null); toast.success('Entidade excluída') },
  })

  const resetErros = () => {
    setErroCpf('')
    setErroCnpj('')
    ultimoCnpjBuscado.current = ''
    ultimoCepBuscado.current = ''
  }

  const abrirNova = () => {
    setEditando(null)
    setForm(dadosVazios())
    setErro('')
    resetErros()
    setModalAberto(true)
  }

  const abrirEditar = (ent: Entidade) => {
    setEditando(ent)
    setForm({
      tipoPessoa: ent.tipoPessoa,
      nome: ent.nome,
      nomeFantasia: ent.nomeFantasia ?? '',
      documento: ent.documento,
      inscricaoEstadual: ent.inscricaoEstadual ?? '',
      dataNascimento: ent.dataNascimento ?? '',
      email: ent.email ?? '',
      telefone: ent.telefone ? ent.telefone.replace(/\D/g, '') : '',
      cep: ent.cep ? ent.cep.replace(/\D/g, '') : '',
      logradouro: ent.logradouro ?? '',
      numero: ent.numero ?? '',
      complemento: ent.complemento ?? '',
      bairro: ent.bairro ?? '',
      cidade: ent.cidade ?? '',
      uf: ent.uf ?? '',
    })
    setErro('')
    resetErros()
    setModalAberto(true)
  }

  const fecharModal = () => { setModalAberto(false); setEditando(null); resetErros() }

  const setF = (k: keyof DadosEntidade, v: string) => setForm(f => ({ ...f, [k]: v }))

  const alterarTipoPessoa = (t: TipoPessoa) => {
    setForm(f => ({ ...f, tipoPessoa: t, documento: '' }))
    setErroCpf('')
    setErroCnpj('')
  }

  const handleCnpjBuscar = async (cnpj: string) => {
    if (cnpj === ultimoCnpjBuscado.current) return
    ultimoCnpjBuscado.current = cnpj
    setCarregandoCnpj(true)
    try {
      const dados = await consultarCnpj(cnpj)
      setForm(f => ({
        ...f,
        nome: f.nome || dados.razaoSocial,
        nomeFantasia: f.nomeFantasia || dados.nomeFantasia || '',
        email: f.email || dados.email || '',
        telefone: f.telefone || (dados.telefone ? dados.telefone.replace(/\D/g, '') : ''),
        cep: f.cep || dados.cep || '',
        logradouro: f.logradouro || dados.logradouro || '',
        numero: f.numero || dados.numero || '',
        complemento: f.complemento || dados.complemento || '',
        bairro: f.bairro || dados.bairro || '',
        cidade: f.cidade || dados.cidade || '',
        uf: f.uf || dados.uf || '',
      }))
    } catch {
      // toast exibido pelo interceptor do cliente axios
    } finally {
      setCarregandoCnpj(false)
    }
  }

  const handleCepBuscar = async (cep: string) => {
    if (cep === ultimoCepBuscado.current) return
    ultimoCepBuscado.current = cep
    setCarregandoCep(true)
    try {
      const dados = await consultarCep(cep)
      setForm(f => ({
        ...f,
        logradouro: f.logradouro || dados.logradouro || '',
        bairro: f.bairro || dados.bairro || '',
        cidade: f.cidade || dados.cidade || '',
        uf: f.uf || dados.uf || '',
      }))
      numeroRef.current?.focus()
    } catch {
      // toast exibido pelo interceptor do cliente axios
    } finally {
      setCarregandoCep(false)
    }
  }

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    if (editando) {
      atualizar.mutate({ id: editando.id, dados: form })
    } else {
      criar.mutate(form)
    }
  }

  const atingiuLimite = assinatura ? assinatura.entidadesUsadas >= assinatura.limiteEntidades : false
  const salvando = criar.isPending || atualizar.isPending

  return (
    <div className="p-6 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-conteudo">Entidades</h1>
          {assinatura && (
            <p className="text-sm text-conteudo-suave mt-1">
              {assinatura.entidadesUsadas}/{assinatura.limiteEntidades} entidades • Plano {assinatura.nomePlano}
            </p>
          )}
        </div>
        <button
          onClick={abrirNova}
          disabled={atingiuLimite}
          title={atingiuLimite ? `Limite do plano ${assinatura?.nomePlano} atingido` : 'Nova entidade'}
          className="btn-primary disabled:opacity-40 disabled:cursor-not-allowed"
        >
          + Nova entidade
        </button>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-12"><Spinner /></div>
      ) : entidades.length === 0 ? (
        <p className="text-conteudo-suave text-center py-12">Nenhuma entidade cadastrada.</p>
      ) : (
        <div className="space-y-3">
          {entidades.map(ent => (
            <div key={ent.id} className="card flex items-center justify-between gap-4">
              <div className="min-w-0">
                <p className="font-medium text-conteudo truncate">{ent.nome}</p>
                <p className="text-sm text-conteudo-suave">
                  {rotuloTipoPessoa(ent.tipoPessoa)} · {formatarDocumento(ent.documento, ent.tipoPessoa)}
                  {ent.cidade && ` · ${ent.cidade}${ent.uf ? `/${ent.uf}` : ''}`}
                </p>
              </div>
              <div className="flex gap-2 shrink-0">
                <button onClick={() => abrirEditar(ent)}
                  className="text-sm text-acento hover:underline">Editar</button>
                <button onClick={() => setConfirmarExcluir(ent)}
                  className="text-sm text-red-500 hover:underline">Excluir</button>
              </div>
            </div>
          ))}
        </div>
      )}

      <ModalConfirmacao
        aberto={confirmarExcluir !== null}
        titulo="Excluir entidade"
        aoFechar={() => !excluir.isPending && setConfirmarExcluir(null)}
        botoes={[{ rotulo: 'Excluir', variante: 'perigo', aoClicar: () => excluir.mutate(confirmarExcluir!.id), carregando: excluir.isPending }]}
      >
        {`Deseja excluir a entidade "${confirmarExcluir?.nome}"? Esta ação não pode ser desfeita.`}
      </ModalConfirmacao>

      {modalAberto && (
        <SobreposicaoModal aoFechar={fecharModal}>
          <div className="cartao-modal w-full max-w-xl">
            <div className="cartao-modal-cabecalho">
              <h2 className="text-lg font-semibold text-conteudo">
                {editando ? 'Editar entidade' : 'Nova entidade'}
              </h2>
              <button onClick={fecharModal} className="btn-ghost p-1.5 text-sm">✕</button>
            </div>
            <form onSubmit={handleSubmit} className="cartao-modal-corpo space-y-3">
              <div className="flex gap-2">
                {(['FISICA', 'JURIDICA'] as TipoPessoa[]).map(t => (
                  <button key={t} type="button"
                    onClick={() => alterarTipoPessoa(t)}
                    className={`flex-1 py-1.5 rounded text-sm border transition-colors ${
                      form.tipoPessoa === t
                        ? 'border-acento bg-acento text-white'
                        : 'border-borda text-conteudo-suave hover:border-acento'
                    }`}>
                    {rotuloTipoPessoa(t)}
                  </button>
                ))}
              </div>

              <div>
                <label className="label">{form.tipoPessoa === 'FISICA' ? 'Nome completo' : 'Razão social'}</label>
                <input className="input" required value={form.nome} onChange={e => setF('nome', e.target.value)} />
              </div>

              {form.tipoPessoa === 'JURIDICA' && (
                <div>
                  <label className="label">Nome fantasia</label>
                  <input className="input" value={form.nomeFantasia} onChange={e => setF('nomeFantasia', e.target.value)} />
                </div>
              )}

              <div>
                <label className="label">{form.tipoPessoa === 'FISICA' ? 'CPF' : 'CNPJ'}</label>
                {form.tipoPessoa === 'FISICA' ? (
                  <>
                    <IMaskInput
                      mask="000.000.000-00"
                      unmask={true}
                      className={`input ${erroCpf ? 'border-red-500 focus:border-red-500' : ''}`}
                      required
                      value={form.documento}
                      onAccept={(val) => {
                        const v = val as string
                        setF('documento', v)
                        if (v.length === 11) setErroCpf(validarCpf(v) ? '' : 'CPF inválido')
                        else setErroCpf('')
                      }}
                    />
                    {erroCpf && <p className="text-xs text-red-500 mt-1">{erroCpf}</p>}
                  </>
                ) : (
                  <>
                    <div className="relative">
                      <IMaskInput
                        mask="SS.SSS.SSS/SSSS-00"
                        definitions={{ S: /[A-Za-z0-9]/ } as Record<string, RegExp>}
                        unmask={true}
                        className={`input ${erroCnpj ? 'border-red-500 focus:border-red-500' : ''} ${carregandoCnpj ? 'pr-9' : ''}`}
                        required
                        value={form.documento}
                        onAccept={(val) => {
                          const v = (val as string).toUpperCase()
                          setF('documento', v)
                          if (v.length === 14) {
                            if (!validarCnpj(v)) { setErroCnpj('CNPJ inválido'); return }
                            setErroCnpj('')
                            handleCnpjBuscar(v)
                          } else setErroCnpj('')
                        }}
                      />
                      {carregandoCnpj && (
                        <div className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none">
                          <Spinner />
                        </div>
                      )}
                    </div>
                    {erroCnpj && <p className="text-xs text-red-500 mt-1">{erroCnpj}</p>}
                  </>
                )}
              </div>

              {form.tipoPessoa === 'FISICA' && (
                <div>
                  <label className="label">Data de nascimento</label>
                  <input className="input" type="date" value={form.dataNascimento}
                    onChange={e => setF('dataNascimento', e.target.value)} />
                </div>
              )}

              {form.tipoPessoa === 'JURIDICA' && (
                <div>
                  <label className="label">Inscrição estadual</label>
                  <input className="input" value={form.inscricaoEstadual}
                    onChange={e => setF('inscricaoEstadual', e.target.value)} />
                </div>
              )}

              <div className="grid grid-cols-2 gap-2">
                <div>
                  <label className="label">E-mail</label>
                  <input className="input" type="email" value={form.email} onChange={e => setF('email', e.target.value)} />
                </div>
                <div>
                  <label className="label">Telefone</label>
                  <IMaskInput
                    mask={[{ mask: '(00) 0000-0000' }, { mask: '(00) 00000-0000' }]}
                    unmask={true}
                    className="input"
                    value={form.telefone}
                    onAccept={(val) => setF('telefone', val as string)}
                  />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-2">
                <div>
                  <label className="label">CEP</label>
                  <div className="relative">
                    <IMaskInput
                      mask="00000-000"
                      unmask={true}
                      className={`input ${carregandoCep ? 'pr-9' : ''}`}
                      value={form.cep}
                      onAccept={(val) => {
                        const v = val as string
                        setF('cep', v)
                        if (v.length === 8) handleCepBuscar(v)
                      }}
                    />
                    {carregandoCep && (
                      <div className="absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none">
                        <Spinner />
                      </div>
                    )}
                  </div>
                </div>
                <div className="col-span-2">
                  <label className="label">Logradouro</label>
                  <input className="input" value={form.logradouro} onChange={e => setF('logradouro', e.target.value)} />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-2">
                <div>
                  <label className="label">Número</label>
                  <input ref={numeroRef} className="input" value={form.numero} onChange={e => setF('numero', e.target.value)} />
                </div>
                <div className="col-span-2">
                  <label className="label">Complemento</label>
                  <input className="input" value={form.complemento} onChange={e => setF('complemento', e.target.value)} />
                </div>
              </div>

              <div className="grid grid-cols-3 gap-2">
                <div>
                  <label className="label">Bairro</label>
                  <input className="input" value={form.bairro} onChange={e => setF('bairro', e.target.value)} />
                </div>
                <div>
                  <label className="label">Cidade</label>
                  <input className="input" value={form.cidade} onChange={e => setF('cidade', e.target.value)} />
                </div>
                <div>
                  <label className="label">UF</label>
                  <input className="input" maxLength={2} value={form.uf}
                    onChange={e => setF('uf', e.target.value.toUpperCase())} />
                </div>
              </div>

              {erro && <p className="text-sm text-red-500">{erro}</p>}

              <div className="flex gap-2 pt-1">
                <button type="button" onClick={fecharModal}
                  className="flex-1 py-2 rounded border border-borda text-conteudo-suave hover:bg-superficie-destaque">
                  Cancelar
                </button>
                <button type="submit" disabled={salvando} className="flex-1 btn-primary">
                  {salvando ? 'Salvando...' : 'Salvar'}
                </button>
              </div>
            </form>
          </div>
        </SobreposicaoModal>
      )}
    </div>
  )
}
