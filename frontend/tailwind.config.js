/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        // Sidebar — fixos, nunca mudam com o tema
        'sb-fundo':      'rgb(var(--sb-fundo) / <alpha-value>)',
        'sb-hover':      'rgb(var(--sb-hover) / <alpha-value>)',
        'sb-ativo':      'rgb(var(--sb-ativo) / <alpha-value>)',
        'sb-ativo-barra':'rgb(var(--sb-ativo-barra) / <alpha-value>)',
        'sb-texto':      'rgb(var(--sb-texto) / <alpha-value>)',
        'sb-suave':      'rgb(var(--sb-suave) / <alpha-value>)',
        'sb-borda':      'rgb(var(--sb-borda) / <alpha-value>)',
        // Conteúdo — mudam com o tema
        fundo:           'rgb(var(--fundo) / <alpha-value>)',
        superficie:      'rgb(var(--superficie) / <alpha-value>)',
        'superficie-2':  'rgb(var(--superficie-2) / <alpha-value>)',
        conteudo:        'rgb(var(--conteudo) / <alpha-value>)',
        'conteudo-suave':'rgb(var(--conteudo-suave) / <alpha-value>)',
        borda:           'rgb(var(--borda) / <alpha-value>)',
        acento:          'rgb(var(--acento) / <alpha-value>)',
        // Semânticos
        sucesso:         'rgb(var(--sucesso) / <alpha-value>)',
        'sucesso-fundo': 'rgb(var(--sucesso-fundo) / <alpha-value>)',
        perigo:          'rgb(var(--perigo) / <alpha-value>)',
        'perigo-fundo':  'rgb(var(--perigo-fundo) / <alpha-value>)',
        aviso:           'rgb(var(--aviso) / <alpha-value>)',
        'aviso-fundo':   'rgb(var(--aviso-fundo) / <alpha-value>)',
        // Pastéis
        pastel: {
          lilas:           'rgb(var(--pastel-lilas) / <alpha-value>)',
          'lilas-texto':   'rgb(var(--pastel-lilas-texto) / <alpha-value>)',
          verde:           'rgb(var(--pastel-verde) / <alpha-value>)',
          'verde-texto':   'rgb(var(--pastel-verde-texto) / <alpha-value>)',
          bege:            'rgb(var(--pastel-bege) / <alpha-value>)',
          'bege-texto':    'rgb(var(--pastel-bege-texto) / <alpha-value>)',
          azul:            'rgb(var(--pastel-azul) / <alpha-value>)',
          'azul-texto':    'rgb(var(--pastel-azul-texto) / <alpha-value>)',
          vermelho:        'rgb(var(--pastel-vermelho) / <alpha-value>)',
          'vermelho-texto':'rgb(var(--pastel-vermelho-texto) / <alpha-value>)',
        },
      },
    },
  },
  plugins: [],
}
