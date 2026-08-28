/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        brand: {
          50:  '#eef0ff',
          100: '#e2e2fb',
          400: '#818cf8',
          500: '#6366f1',
          600: '#4f46e5',
          700: '#4338ca',
          900: '#1e1b4b',
        },
        ink: {
          900: '#14161f',
          800: '#1e2230',
          700: '#25283a',
          600: '#4b4e5c',
          500: '#5b5e6d',
          400: '#6b6e7d',
          300: '#9497a6',
          200: '#c3c5cf',
        },
        line: {
          100: '#f5f6f9',
          150: '#f1f2f6',
          200: '#ecedf2',
          300: '#eef0f4',
          400: '#e7e9ee',
          500: '#dcdee4',
        },
        app: { bg: '#f6f7f9' },
        success: { bg: '#eafbf1', text: '#15803d', dot: '#22c55e' },
        warning: { bg: '#fff8e6', text: '#b7791f' },
        info:    { bg: '#eef3ff', text: '#2563eb' },
        accent:  { bg: '#f3ecff', text: '#7c3aed' },
        danger:  {
          bg: '#fdecec', text: '#c0392b', border: '#f4d9d9',
          strong: '#b3352f', strongText: '#a83f3a',
        },
        chip: { bg: '#eef0f4', text: '#5b5e6d' },
      },
      fontFamily: {
        sans:    ['"Public Sans"', 'system-ui', 'sans-serif'],
        heading: ['Sora', 'system-ui', 'sans-serif'],
      },
      borderRadius: {
        chip:   '5px',
        field:  '8px',
        input:  '10px',
        nav:    '9px',
        card:   '14px',
        'login-card':  '18px',
        'result-card': '20px',
      },
    },
  },
  plugins: [],
}
