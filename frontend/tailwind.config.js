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
        pastel: {
          lilas: '#ede9fe',
          'lilas-texto': '#6d28d9',
          verde: '#d1fae5',
          'verde-texto': '#047857',
          bege: '#fef3c7',
          'bege-texto': '#b45309',
          azul: '#dbeafe',
          'azul-texto': '#1d4ed8',
        },
      },
    },
  },
  plugins: [],
}
