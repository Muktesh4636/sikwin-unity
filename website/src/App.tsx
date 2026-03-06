import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { BottomNav } from './components/BottomNav';
import { LoginPage } from './pages/LoginPage';
import { HomePage } from './pages/HomePage';
import { WalletPage } from './pages/WalletPage';
import { WithdrawPage } from './pages/WithdrawPage';
import { LeaderboardPage } from './pages/LeaderboardPage';
import { DiceResultsPage } from './pages/DiceResultsPage';
import { HelpCenterPage } from './pages/HelpCenterPage';
import { ProfilePage } from './pages/ProfilePage';
import { DepositPage } from './pages/DepositPage';
import { TransactionRecordPage } from './pages/TransactionRecordPage';
import { BettingRecordPage } from './pages/BettingRecordPage';
import { WithdrawalRecordPage } from './pages/WithdrawalRecordPage';
import { DepositRecordPage } from './pages/DepositRecordPage';
import { WithdrawalAccountPage } from './pages/WithdrawalAccountPage';
import { AddBankAccountPage } from './pages/AddBankAccountPage';
import { PersonalDataPage } from './pages/PersonalDataPage';
import { SecurityPage } from './pages/SecurityPage';
import { LanguagesPage } from './pages/LanguagesPage';
import { ReferEarnPage } from './pages/ReferEarnPage';
import { PartnerPage } from './pages/PartnerPage';
import { RequireAuth } from './routing/RequireAuth';

function App() {
  const loc = useLocation();
  const hideNav = loc.pathname === '/login' || loc.pathname === '/signup';

  return (
    <div className="app-shell">
      <Routes>
        <Route path="/" element={<Navigate to="/home" replace />} />

        <Route path="/login" element={<LoginPage />} />
        {/* Signup will be added next; keep route for links */}
        <Route path="/signup" element={<div className="mobile-frame app-shell grid place-items-center text-textGrey">Signup (next)</div>} />

        <Route
          path="/home"
          element={
            <RequireAuth>
              <HomePage />
            </RequireAuth>
          }
        />
        <Route
          path="/wallet"
          element={
            <RequireAuth>
              <WalletPage />
            </RequireAuth>
          }
        />
        <Route
          path="/withdraw"
          element={
            <RequireAuth>
              <WithdrawPage />
            </RequireAuth>
          }
        />
        <Route
          path="/deposit"
          element={
            <RequireAuth>
              <DepositPage />
            </RequireAuth>
          }
        />
        <Route
          path="/transactions"
          element={
            <RequireAuth>
              <TransactionRecordPage />
            </RequireAuth>
          }
        />
        <Route
          path="/betting-record"
          element={
            <RequireAuth>
              <BettingRecordPage />
            </RequireAuth>
          }
        />
        <Route
          path="/withdrawal-record"
          element={
            <RequireAuth>
              <WithdrawalRecordPage />
            </RequireAuth>
          }
        />
        <Route
          path="/deposit-record"
          element={
            <RequireAuth>
              <DepositRecordPage />
            </RequireAuth>
          }
        />
        <Route
          path="/leaderboard"
          element={
            <RequireAuth>
              <LeaderboardPage />
            </RequireAuth>
          }
        />
        <Route
          path="/dice-results"
          element={
            <RequireAuth>
              <DiceResultsPage />
            </RequireAuth>
          }
        />
        <Route
          path="/help-center"
          element={
            <RequireAuth>
              <HelpCenterPage />
            </RequireAuth>
          }
        />
        <Route
          path="/me"
          element={
            <RequireAuth>
              <ProfilePage />
            </RequireAuth>
          }
        />
        <Route
          path="/gundu-ata"
          element={
            <RequireAuth>
              <div className="mobile-frame app-shell flex flex-col items-center justify-center gap-4 px-4 text-center">
                <span className="text-4xl font-black text-primaryYellow">GUNDU ATA</span>
                <p className="text-textGrey">Coming soon on website</p>
              </div>
            </RequireAuth>
          }
        />
        <Route
          path="/refer"
          element={
            <RequireAuth>
              <ReferEarnPage />
            </RequireAuth>
          }
        />
        <Route
          path="/withdrawal-account"
          element={
            <RequireAuth>
              <WithdrawalAccountPage />
            </RequireAuth>
          }
        />
        <Route
          path="/withdrawal-account/add"
          element={
            <RequireAuth>
              <AddBankAccountPage />
            </RequireAuth>
          }
        />
        <Route
          path="/personal-info"
          element={
            <RequireAuth>
              <PersonalDataPage />
            </RequireAuth>
          }
        />
        <Route
          path="/security"
          element={
            <RequireAuth>
              <SecurityPage />
            </RequireAuth>
          }
        />
        <Route
          path="/languages"
          element={
            <RequireAuth>
              <LanguagesPage />
            </RequireAuth>
          }
        />
        <Route
          path="/partner"
          element={
            <RequireAuth>
              <PartnerPage />
            </RequireAuth>
          }
        />
        <Route
          path="/game-guidelines"
          element={
            <RequireAuth>
              <div className="mobile-frame app-shell grid place-items-center px-4 text-center text-textGrey">
                Game Guidelines — Coming soon
              </div>
            </RequireAuth>
          }
        />

        <Route path="*" element={<div className="mobile-frame app-shell grid place-items-center text-textGrey">Not found</div>} />
      </Routes>

      {!hideNav ? <BottomNav /> : null}
    </div>
  );
}

export default App;
