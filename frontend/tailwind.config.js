/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#eef2ff',
          100: '#e0e7ff',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#4338ca',
        },
        fundo: 'rgb(var(--fundo) / <alpha-value>)',
        superficie: 'rgb(var(--superficie) / <alpha-value>)',
        'superficie-2': 'rgb(var(--superficie-2) / <alpha-value>)',
        conteudo: 'rgb(var(--conteudo) / <alpha-value>)',
        'conteudo-suave': 'rgb(var(--conteudo-suave) / <alpha-value>)',
        borda: 'rgb(var(--borda) / <alpha-value>)',
        acento: 'rgb(var(--acento) / <alpha-value>)',
        campo: 'rgb(var(--campo) / <alpha-value>)',
        'campo-texto': 'rgb(var(--campo-texto) / <alpha-value>)',
        'campo-texto-suave': 'rgb(var(--campo-texto-suave) / <alpha-value>)',
        pastel: {
          lilas: 'rgb(var(--pastel-lilas) / <alpha-value>)',
          'lilas-texto': 'rgb(var(--pastel-lilas-texto) / <alpha-value>)',
          verde: 'rgb(var(--pastel-verde) / <alpha-value>)',
          'verde-texto': 'rgb(var(--pastel-verde-texto) / <alpha-value>)',
          bege: 'rgb(var(--pastel-bege) / <alpha-value>)',
          'bege-texto': 'rgb(var(--pastel-bege-texto) / <alpha-value>)',
          azul: 'rgb(var(--pastel-azul) / <alpha-value>)',
          'azul-texto': 'rgb(var(--pastel-azul-texto) / <alpha-value>)',
          vermelho: 'rgb(var(--pastel-vermelho) / <alpha-value>)',
          'vermelho-texto': 'rgb(var(--pastel-vermelho-texto) / <alpha-value>)',
        },
      },
    },
  },
  plugins: [],
}
