/** @type {import('tailwindcss').Config} */
export default {
    content: [
        "./index.html",
        "./src/**/*.{js,ts,jsx,tsx}",
    ],
    darkMode: 'class',
    theme: {
        extend: {
            fontFamily: {
                sans: ['Inter', 'system-ui', 'sans-serif'],
            },
            colors: {
                primary: {
                    50: '#ecfdf5',
                    100: '#d1fae5',
                    200: '#a7f3d0',
                    300: '#6ee7b7',
                    400: '#3ecf8e',
                    500: '#22b876',
                    600: '#189d63',
                    700: '#147d51',
                    800: '#116240',
                    900: '#0d4c33',
                    950: '#06110d',
                },
                dark: {
                    50: '#eafaf2',
                    100: '#d5f0e3',
                    200: '#a8ddc4',
                    300: '#7bc9a5',
                    400: '#4f7d68',
                    500: '#3f5c50',
                    600: '#2c453a',
                    700: '#1c3229',
                    800: '#163829',
                    850: '#0d1f18',
                    900: '#06110d',
                    950: '#030805',
                }
            },
            animation: {
                'fade-in': 'fadeIn 0.2s ease-out',
                'slide-up': 'slideUp 0.3s ease-out',
                'slide-in-right': 'slideInRight 0.3s ease-out',
                'scale-in': 'scaleIn 0.2s ease-out',
                'pulse-subtle': 'pulseSubtle 2s ease-in-out infinite',
                'glow': 'glow 2s ease-in-out infinite',
                'shimmer': 'shimmer 2s linear infinite',
            },
            keyframes: {
                fadeIn: {
                    '0%': { opacity: '0' },
                    '100%': { opacity: '1' },
                },
                slideUp: {
                    '0%': { opacity: '0', transform: 'translateY(10px)' },
                    '100%': { opacity: '1', transform: 'translateY(0)' },
                },
                slideInRight: {
                    '0%': { opacity: '0', transform: 'translateX(20px)' },
                    '100%': { opacity: '1', transform: 'translateX(0)' },
                },
                scaleIn: {
                    '0%': { opacity: '0', transform: 'scale(0.95)' },
                    '100%': { opacity: '1', transform: 'scale(1)' },
                },
                pulseSubtle: {
                    '0%, 100%': { opacity: '1' },
                    '50%': { opacity: '0.8' },
                },
                glow: {
                    '0%, 100%': { boxShadow: '0 0 20px rgba(62, 207, 142, 0.3)' },
                    '50%': { boxShadow: '0 0 30px rgba(62, 207, 142, 0.5)' },
                },
                shimmer: {
                    '0%': { backgroundPosition: '-200% 0' },
                    '100%': { backgroundPosition: '200% 0' },
                },
            },
            backdropBlur: {
                xs: '2px',
            },
            boxShadow: {
                'glow': '0 0 20px rgba(62, 207, 142, 0.3)',
                'glow-lg': '0 0 40px rgba(62, 207, 142, 0.4)',
                'inner-glow': 'inset 0 0 20px rgba(62, 207, 142, 0.1)',
            },
        },
    },
    plugins: [],
}

