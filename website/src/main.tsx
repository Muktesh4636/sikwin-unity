import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import './index.css';
import App from './App.tsx';
import { AuthProvider } from './auth/AuthContext';
import { LocaleProvider } from './context/LocaleContext';
import { MaintenanceGate } from './maintenance/MaintenanceGate';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <LocaleProvider>
        <AuthProvider>
          <MaintenanceGate>
            <App />
          </MaintenanceGate>
        </AuthProvider>
      </LocaleProvider>
    </BrowserRouter>
  </StrictMode>
)
