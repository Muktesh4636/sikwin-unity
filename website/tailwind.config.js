/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Match APK themes.xml primary_yellow and Wallet/WithdrawalAccount screens
        appBg: '#000000', // Dual Cards home (APK default)
        surface: '#1E1E1E',
        card: '#0D0D0D',
        bottomNav: '#0A0A0A',
        border: '#333333',
        textWhite: '#FFFFFF',
        textGrey: '#BDBDBD',
        primaryYellow: '#FFD54F', // Dual Cards gold mid
        goldCard: '#E6B84D',
        goldCardDark: '#C9A227',
        success: '#4CAF50',
        error: '#F44336',
      },
      boxShadow: {
        card: '0 10px 30px rgba(0,0,0,0.35)',
      },
    },
  },
  plugins: [],
}

