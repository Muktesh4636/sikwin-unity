/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // Match APK themes.xml primary_yellow and Wallet/WithdrawalAccount screens
        appBg: '#121212',
        surface: '#1E1E1E',
        card: '#252525',
        bottomNav: '#1A1A1A',
        border: '#333333',
        textWhite: '#FFFFFF',
        textGrey: '#BDBDBD',
        primaryYellow: '#DAA520', // APK golden (themes.xml primary_yellow)
        goldCard: '#E6B84D',     // APK gold card background (WalletScreen, WithdrawalAccountScreen)
        goldCardDark: '#C48B22', // APK bank card gradient end
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

