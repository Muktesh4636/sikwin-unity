import { BrowserRouter, Route, Routes, useLocation } from 'react-router-dom';
import { LoginSignupModalProvider } from './context/LoginSignupModalContext';
import { BottomNav } from './components/BottomNav';
import { HomePage } from './pages/HomePage';
import { ProfilePage } from './pages/ProfilePage';
import { TransactionRecordPage } from './pages/TransactionRecordPage';
import { BettingRecordPage } from './pages/BettingRecordPage';
import { DepositRecordPage } from './pages/DepositRecordPage';
import { WithdrawalRecordPage } from './pages/WithdrawalRecordPage';
import { WithdrawalAccountPage } from './pages/WithdrawalAccountPage';
import { AddBankAccountPage } from './pages/AddBankAccountPage';
import { PersonalDataPage } from './pages/PersonalDataPage';
import { SecurityPage } from './pages/SecurityPage';
import { LanguagesPage } from './pages/LanguagesPage';
import { HelpCenterPage } from './pages/HelpCenterPage';
import { ReferEarnPage } from './pages/ReferEarnPage';
import { PartnerPage } from './pages/PartnerPage';
import { DiceResultsPage } from './pages/DiceResultsPage';
import { GameGuidelinesPage } from './pages/GameGuidelinesPage';
import { WalletPage } from './pages/WalletPage';
import { WithdrawPage } from './pages/WithdrawPage';
import { DepositPage } from './pages/DepositPage';
import { LoginPage } from './pages/LoginPage';
import { SignupPage } from './pages/SignupPage';
import { ForgotPasswordPage } from './pages/ForgotPasswordPage';
import { DailyRewardPage } from './pages/DailyRewardPage';
import { LuckyDrawPage } from './pages/LuckyDrawPage';
import { LeaderboardPage } from './pages/LeaderboardPage';
import { GunduAtaGamePage } from './pages/GunduAtaGamePage';
import { MaintenanceGate } from './maintenance/MaintenanceGate';
import { isAdminUrlPath } from './utils/adminPath';

function AppRoutes() {
  const { pathname } = useLocation();
  const hideNav =
    pathname === '/login' ||
    pathname === '/signup' ||
    pathname === '/forgot-password' ||
    isAdminUrlPath(pathname);

  return (
    <>
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/ga" element={<GunduAtaGamePage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/forgot-password" element={<ForgotPasswordPage />} />
        <Route path="/me" element={<ProfilePage />} />
        <Route path="/wallet" element={<WalletPage />} />
        <Route path="/withdraw" element={<WithdrawPage />} />
        <Route path="/deposit" element={<DepositPage />} />
        <Route path="/transactions" element={<TransactionRecordPage />} />
        <Route path="/betting-record" element={<BettingRecordPage />} />
        <Route path="/deposit-record" element={<DepositRecordPage />} />
        <Route path="/withdrawal-record" element={<WithdrawalRecordPage />} />
        <Route path="/withdrawal-account" element={<WithdrawalAccountPage />} />
        <Route path="/withdrawal-account/add" element={<AddBankAccountPage />} />
        <Route path="/personal-info" element={<PersonalDataPage />} />
        <Route path="/security" element={<SecurityPage />} />
        <Route path="/languages" element={<LanguagesPage />} />
        <Route path="/help-center" element={<HelpCenterPage />} />
        <Route path="/refer" element={<ReferEarnPage />} />
        <Route path="/partner" element={<PartnerPage />} />
        <Route path="/dice-results" element={<DiceResultsPage />} />
        <Route path="/daily-reward" element={<DailyRewardPage />} />
        <Route path="/lucky-draw" element={<LuckyDrawPage />} />
        <Route path="/leaderboard" element={<LeaderboardPage />} />
        <Route path="/game-guidelines" element={<GameGuidelinesPage />} />
        {/* Do not send /admin to HomePage — that looked like a redirect away from Django admin */}
        <Route path="/admin/*" element={null} />
        <Route path="*" element={<HomePage />} />
      </Routes>
      {!hideNav && <BottomNav />}
    </>
  );
}

/**
 * WebGL app: home page at / and game at /game/index.html.
 * Bottom nav matches APK: Home, GUNDU ATA, Me.
 */
function App() {
  return (
    <BrowserRouter>
      <LoginSignupModalProvider>
        <MaintenanceGate>
          <AppRoutes />
        </MaintenanceGate>
      </LoginSignupModalProvider>
    </BrowserRouter>
  );
}

export default App;
