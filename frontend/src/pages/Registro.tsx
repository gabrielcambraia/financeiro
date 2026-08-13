import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Mail, Lock, User, Phone } from 'lucide-react'
import { IMaskInput } from 'react-imask'
import { registrar } from '../api/autenticacao'
import { consultarCnpj, consultarCep } from '../api/consultas'
import { confirmarVerificacaoEmail, solicitarVerificacaoEmail } from '../api/verificacaoContato'
import { useLojaAutenticacao } from '../store/lojaAutenticacao'
import { validarCpf, validarCnpj } from '../utils/documentos'
import CampoSenha from '../components/CampoSenha'
import LayoutAuth from '../components/LayoutAuth'
import Spinner from '../components/Spinner'
import type { TipoPessoa } from '../types'

type Passo = 1 | 2 | 3

interface FormPerfil {
  nome: string
  email: string
  senha: string
  telefone: string
}

interface FormEntidade {
  tipoPessoa: TipoPessoa
  nome: string
  nomeFantasia: string
  documento: string
  inscricaoEstadual: string
  dataNascimento: string
  email: string
  telefone: string
  cep: string
  logradouro: string
  numero: string
  complemento: string
  bairro: string
  cidade: string
  uf: string
}

const entidadeInicial: FormEntidade = {
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
}

export default function Registro() {
  const navigate = useNavigate()
  const definirSessao = useLojaAutenticacao(s => s.definirSessao)
  const [passo, setPasso] = useState<Passo>(1)
  const [perfil, setPerfil] = useState<FormPerfil>({ nome: '', email: '', senha: '', telefone: '' })
  const [entidade, setEntidade] = useState<FormEntidade>(entidadeInicial)
  const [codigoEmail, setCodigoEmail] = useState('')
  const [erro, setErro] = useState('')
  const [carregando, setCarregando] = useState(false)

  const [erroCpf, setErroCpf] = useState('')
  const [erroCnpj, setErroCnpj] = useState('')
  const [carregandoCnpj, setCarregandoCnpj] = useState(false)
  const [carregandoCep, setCarregandoCep] = useState(false)
  const numeroRef = useRef<HTMLInputElement>(null)
  const ultimoCnpjBuscado = useRef('')
  const ultimoCepBuscado = useRef('')

  const setPf = (k: keyof FormPerfil, v: string) => setPerfil(p => ({ ...p, [k]: v }))
  const setEnt = (k: keyof FormEntidade, v: string) => setEntidade(e => ({ ...e, [k]: v }))

  const alterarTipoPessoa = (t: TipoPessoa) => {
    setEntidade(e => ({ ...e, tipoPessoa: t, documento: '' }))
    setErroCpf('')
    setErroCnpj('')
    ultimoCnpjBuscado.current = ''
  }

  const handleCnpjBuscar = async (cnpj: string) => {
    if (cnpj === ultimoCnpjBuscado.current) return
    ultimoCnpjBuscado.current = cnpj
    setCarregandoCnpj(true)
    try {
      const dados = await consultarCnpj(cnpj)
      setEntidade(e => ({
        ...e,
        nome: e.nome || dados.razaoSocial,
        nomeFantasia: e.nomeFantasia || dados.nomeFantasia || '',
        email: e.email || dados.email || '',
        telefone: e.telefone || (dados.telefone ? dados.telefone.replace(/\D/g, '') : ''),
        cep: e.cep || dados.cep || '',
        logradouro: e.logradouro || dados.logradouro || '',
        numero: e.numero || dados.numero || '',
        complemento: e.complemento || dados.complemento || '',
        bairro: e.bairro || dados.bairro || '',
        cidade: e.cidade || dados.cidade || '',
        uf: e.uf || dados.uf || '',
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
      setEntidade(e => ({
        ...e,
        logradouro: e.logradouro || dados.logradouro || '',
        bairro: e.bairro || dados.bairro || '',
        cidade: e.cidade || dados.cidade || '',
        uf: e.uf || dados.uf || '',
      }))
      numeroRef.current?.focus()
    } catch {
      // toast exibido pelo interceptor do cliente axios
    } finally {
      setCarregandoCep(false)
    }
  }

  const submitPasso1 = (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setPasso(2)
  }

  const submitPasso2 = async (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setCarregando(true)
    try {
      const resposta = await registrar({
        nome: perfil.nome,
        email: perfil.email,
        senha: perfil.senha,
        telefone: perfil.telefone || undefined,
        entidade: {
          tipoPessoa: entidade.tipoPessoa,
          nome: entidade.nome,
          nomeFantasia: entidade.nomeFantasia || undefined,
          documento: entidade.documento,
          inscricaoEstadual: entidade.inscricaoEstadual || undefined,
          dataNascimento: entidade.dataNascimento || undefined,
          email: entidade.email || undefined,
          telefone: entidade.telefone || undefined,
          cep: entidade.cep || undefined,
          logradouro: entidade.logradouro || undefined,
          numero: entidade.numero || undefined,
          complemento: entidade.complemento || undefined,
          bairro: entidade.bairro || undefined,
          cidade: entidade.cidade || undefined,
          uf: entidade.uf || undefined,
        },
      })
      definirSessao(resposta)
      setPasso(3)
    } catch (err: unknown) {
      const e = err as { response?: { status?: number; data?: { mensagem?: string } } }
      const msg = e?.response?.data?.mensagem
      if (e?.response?.status === 409) setErro('E-mail ou documento já cadastrado')
      else if (e?.response?.status === 422) setErro(msg ?? 'Documento inválido')
      else setErro('Não foi possível criar a conta')
    } finally {
      setCarregando(false)
    }
  }

  const submitPasso3 = async (e: React.FormEvent) => {
    e.preventDefault()
    setErro('')
    setCarregando(true)
    try {
      await confirmarVerificacaoEmail(codigoEmail)
      navigate('/')
    } catch {
      setErro('Código inválido ou expirado')
    } finally {
      setCarregando(false)
    }
  }

  const reenviarCodigo = async () => {
    try {
      await solicitarVerificacaoEmail()
    } catch {
      // silencioso
    }
  }

  return (
    <LayoutAuth>
      {/* Progress bar */}
      <div className="flex gap-1 mb-6">
        {([1, 2, 3] as Passo[]).map(p => (
          <div
            key={p}
            className={`h-1 flex-1 rounded-full transition-colors ${passo >= p ? 'bg-acento' : 'bg-borda'}`}
          />
        ))}
      </div>

      {passo === 1 && (
        <form onSubmit={submitPasso1} className="space-y-[18px]">
          <div>
            <h2 className="text-2xl font-bold text-conteudo mb-1.5">Criar sua conta</h2>
            <p className="text-sm text-conteudo-suave mb-5">Comece a organizar suas finanças hoje</p>
          </div>

          <div>
            <label className="label">Nome completo</label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-conteudo-suave pointer-events-none">
                <User size={15} />
              </span>
              <input
                className="input pl-10"
                required
                placeholder="João Silva"
                value={perfil.nome}
                onChange={e => setPf('nome', e.target.value)}
              />
            </div>
          </div>

          <div>
            <label className="label">E-mail</label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-conteudo-suave pointer-events-none">
                <Mail size={15} />
              </span>
              <input
                className="input pl-10"
                type="email"
                required
                placeholder="voce@email.com"
                value={perfil.email}
                onChange={e => setPf('email', e.target.value)}
              />
            </div>
          </div>

          <div>
            <label className="label">Senha</label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-conteudo-suave pointer-events-none z-10">
                <Lock size={15} />
              </span>
              <CampoSenha
                className="input pl-10"
                required
                minLength={8}
                placeholder="Mín. 8 caracteres"
                value={perfil.senha}
                onChange={e => setPf('senha', e.target.value)}
              />
            </div>
          </div>

          <div>
            <label className="label">
              Telefone <span className="text-conteudo-suave text-xs font-normal">(opcional)</span>
            </label>
            <div className="relative">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-conteudo-suave pointer-events-none">
                <Phone size={15} />
              </span>
              <IMaskInput
                mask={[{ mask: '(00) 0000-0000' }, { mask: '(00) 00000-0000' }]}
                unmask={true}
                className="input pl-10"
                placeholder="(11) 99999-9999"
                value={perfil.telefone}
                onAccept={(val) => setPf('telefone', val as string)}
              />
            </div>
          </div>

          {erro && <p className="text-sm text-perigo">{erro}</p>}

          <button
            type="submit"
            className="w-full py-3 bg-acento text-white rounded-[10px] text-[15px] font-semibold hover:brightness-110 transition-all mt-2"
          >
            Continuar
          </button>

          <p className="text-sm text-conteudo-suave text-center">
            Ao criar sua conta você aceita os{' '}
            <span className="text-acento font-medium cursor-default">Termos de Uso</span>
            {' '}e a{' '}
            <span className="text-acento font-medium cursor-default">Política de Privacidade</span>.
          </p>

          <p className="text-sm text-conteudo-suave text-center">
            Já tem conta?{' '}
            <Link to="/login" className="text-acento hover:underline font-medium">Entrar</Link>
          </p>
        </form>
      )}

      {passo === 2 && (
        <form onSubmit={submitPasso2} className="space-y-4">
          <div>
            <h2 className="text-xl font-bold text-conteudo mb-1">Dados da entidade</h2>
            <p className="text-xs text-conteudo-suave mb-4">
              Seu espaço começa no plano Individual com 1 entidade.
            </p>
          </div>

          <div className="flex gap-2">
            {(['FISICA', 'JURIDICA'] as TipoPessoa[]).map(t => (
              <button
                key={t}
                type="button"
                onClick={() => alterarTipoPessoa(t)}
                className={`flex-1 py-2 rounded-lg text-sm font-medium border transition-colors ${
                  entidade.tipoPessoa === t
                    ? 'border-acento bg-acento text-white'
                    : 'border-borda text-conteudo-suave hover:border-acento'
                }`}
              >
                {t === 'FISICA' ? 'Pessoa Física' : 'Pessoa Jurídica'}
              </button>
            ))}
          </div>

          <div>
            <label className="label">{entidade.tipoPessoa === 'FISICA' ? 'Nome completo' : 'Razão social'}</label>
            <input className="input" required value={entidade.nome}
              onChange={e => setEnt('nome', e.target.value)} />
          </div>

          {entidade.tipoPessoa === 'JURIDICA' && (
            <div>
              <label className="label">Nome fantasia <span className="text-conteudo-suave text-xs font-normal">(opcional)</span></label>
              <input className="input" value={entidade.nomeFantasia}
                onChange={e => setEnt('nomeFantasia', e.target.value)} />
            </div>
          )}

          <div>
            <label className="label">{entidade.tipoPessoa === 'FISICA' ? 'CPF' : 'CNPJ'}</label>
            {entidade.tipoPessoa === 'FISICA' ? (
              <>
                <IMaskInput
                  mask="000.000.000-00"
                  unmask={true}
                  className={`input ${erroCpf ? 'border-red-500 focus:border-red-500' : ''}`}
                  required
                  value={entidade.documento}
                  onAccept={(val) => {
                    const v = val as string
                    setEnt('documento', v)
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
                    prepare={(s: string) => s.toUpperCase()}
                    unmask={true}
                    className={`input ${erroCnpj ? 'border-red-500 focus:border-red-500' : ''} ${carregandoCnpj ? 'pr-9' : ''}`}
                    required
                    value={entidade.documento}
                    onAccept={(val) => {
                      const v = val as string
                      setEnt('documento', v)
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

          {entidade.tipoPessoa === 'FISICA' && (
            <div>
              <label className="label">Data de nascimento <span className="text-conteudo-suave text-xs font-normal">(opcional)</span></label>
              <input className="input" type="date" value={entidade.dataNascimento}
                onChange={e => setEnt('dataNascimento', e.target.value)} />
            </div>
          )}

          {entidade.tipoPessoa === 'JURIDICA' && (
            <div>
              <label className="label">Inscrição estadual <span className="text-conteudo-suave text-xs font-normal">(opcional)</span></label>
              <input className="input" value={entidade.inscricaoEstadual}
                onChange={e => setEnt('inscricaoEstadual', e.target.value)} />
            </div>
          )}

          <div>
            <label className="label">E-mail <span className="text-conteudo-suave text-xs font-normal">(opcional)</span></label>
            <input className="input" type="email" value={entidade.email}
              onChange={e => setEnt('email', e.target.value)} />
          </div>

          <div>
            <label className="label">Telefone <span className="text-conteudo-suave text-xs font-normal">(opcional)</span></label>
            <IMaskInput
              mask={[{ mask: '(00) 0000-0000' }, { mask: '(00) 00000-0000' }]}
              unmask={true}
              className="input"
              value={entidade.telefone}
              onAccept={(val) => setEnt('telefone', val as string)}
            />
          </div>

          <div className="grid grid-cols-3 gap-2">
            <div>
              <label className="label">CEP</label>
              <div className="relative">
                <IMaskInput
                  mask="00000-000"
                  unmask={true}
                  className={`input ${carregandoCep ? 'pr-9' : ''}`}
                  value={entidade.cep}
                  onAccept={(val) => {
                    const v = val as string
                    setEnt('cep', v)
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
              <input className="input" value={entidade.logradouro}
                onChange={e => setEnt('logradouro', e.target.value)} />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-2">
            <div>
              <label className="label">Número</label>
              <input ref={numeroRef} className="input" value={entidade.numero}
                onChange={e => setEnt('numero', e.target.value)} />
            </div>
            <div className="col-span-2">
              <label className="label">Complemento</label>
              <input className="input" value={entidade.complemento}
                onChange={e => setEnt('complemento', e.target.value)} />
            </div>
          </div>

          <div className="grid grid-cols-3 gap-2">
            <div>
              <label className="label">Bairro</label>
              <input className="input" value={entidade.bairro}
                onChange={e => setEnt('bairro', e.target.value)} />
            </div>
            <div>
              <label className="label">Cidade</label>
              <input className="input" value={entidade.cidade}
                onChange={e => setEnt('cidade', e.target.value)} />
            </div>
            <div>
              <label className="label">UF</label>
              <input className="input" maxLength={2} value={entidade.uf}
                onChange={e => setEnt('uf', e.target.value.toUpperCase())} />
            </div>
          </div>

          {erro && <p className="text-sm text-perigo">{erro}</p>}

          <div className="flex gap-2 pt-1">
            <button
              type="button"
              onClick={() => setPasso(1)}
              className="flex-1 py-2.5 rounded-lg border border-borda text-conteudo-suave hover:bg-superficie-2 text-sm font-medium transition-colors"
            >
              Voltar
            </button>
            <button
              type="submit"
              disabled={carregando}
              className="flex-1 py-2.5 bg-acento text-white rounded-lg text-sm font-semibold hover:brightness-110 transition-all disabled:opacity-60"
            >
              {carregando ? 'Criando...' : 'Criar conta'}
            </button>
          </div>
        </form>
      )}

      {passo === 3 && (
        <form onSubmit={submitPasso3} className="space-y-[18px]">
          <div>
            <h2 className="text-2xl font-bold text-conteudo mb-1.5">Verifique seu e-mail</h2>
            <p className="text-sm text-conteudo-suave mb-5">
              Enviamos um código de 6 dígitos para{' '}
              <strong className="text-conteudo">{perfil.email}</strong>.
            </p>
          </div>

          <div>
            <label className="label">Código de verificação</label>
            <input
              className="input text-center text-2xl tracking-widest"
              required
              maxLength={6}
              pattern="\d{6}"
              value={codigoEmail}
              onChange={e => setCodigoEmail(e.target.value.replace(/\D/g, ''))}
              placeholder="000000"
            />
          </div>

          {erro && <p className="text-sm text-perigo">{erro}</p>}

          <button
            type="submit"
            disabled={carregando}
            className="w-full py-3 bg-acento text-white rounded-[10px] text-[15px] font-semibold hover:brightness-110 transition-all disabled:opacity-60"
          >
            {carregando ? 'Verificando...' : 'Verificar e entrar'}
          </button>

          <p className="text-sm text-center text-conteudo-suave">
            Não recebeu?{' '}
            <button type="button" onClick={reenviarCodigo} className="text-acento hover:underline font-medium">
              Reenviar código
            </button>
          </p>
          <p className="text-sm text-center text-conteudo-suave">
            <button type="button" onClick={() => navigate('/')} className="text-acento hover:underline font-medium">
              Pular por enquanto
            </button>
          </p>
        </form>
      )}
    </LayoutAuth>
  )
}
