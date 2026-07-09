interface PropsSeletorCor {
  cores: string[]
  corSelecionada: string
  aoSelecionar: (cor: string) => void
  tamanho?: 'sm' | 'md'
}

export default function SeletorCor({ cores, corSelecionada, aoSelecionar, tamanho = 'md' }: PropsSeletorCor) {
  const classeTamanho = tamanho === 'sm' ? 'w-7 h-7' : 'w-8 h-8'

  return (
    <div className="flex gap-2 flex-wrap">
      {cores.map(c => (
        <button key={c} type="button" onClick={() => aoSelecionar(c)}
          className={`${classeTamanho} rounded-full border-2 transition-all ${corSelecionada === c ? 'border-conteudo scale-110' : 'border-transparent'}`}
          style={{ background: c }} />
      ))}
    </div>
  )
}
