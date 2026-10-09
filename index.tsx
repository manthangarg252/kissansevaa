
import React, { useState, useEffect } from 'react';
import { createRoot } from 'react-dom/client';
import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import Layout from './components/Layout.tsx';
import Dashboard from './pages/Dashboard.tsx';
import Login from './pages/Login.tsx';
import Signup from './pages/Signup.tsx';
import CropDisease from './pages/CropDisease.tsx';
import Livestock from './pages/Livestock.tsx';
import MarketInsights from './pages/MarketInsights.tsx';
import IoTMonitoring from './pages/IoTMonitoring.tsx';
import GovernmentSchemes from './pages/GovernmentSchemes.tsx';
import TradersDirectory from './pages/TradersDirectory.tsx';
import CarbonCredits from './pages/CarbonCredits.tsx';
import LiveMarketPrices from './pages/LiveMarketPrices.tsx';
import Chatbot from './pages/Chatbot.tsx';
import LoansPage from './pages/LoansPage.tsx';
import { Language, User } from './types.ts';
// Import i18n instance to synchronize state and detect changes
import i18n from './i18n.ts'; 

const App = () => {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);
  
  // Fix: Initialize language state from i18n to pass down as prop
  const [language, setLanguage] = useState<Language>(() => {
    const current = i18n.language as Language;
    return (current && ['en', 'hi', 'sa', 'pa', 'mr'].includes(current)) ? current : 'en';
  });

  useEffect(() => {
    const savedUser = localStorage.getItem('ks_user');
    if (savedUser) {
      try {
        setUser(JSON.parse(savedUser));
      } catch (e) {
        localStorage.removeItem('ks_user');
      }
    }
    setLoading(false);

    // Fix: Listen for language changes (e.g., from Layout) and sync local state
    const handleLangChange = (lng: string) => {
      setLanguage(lng as Language);
    };
    i18n.on('languageChanged', handleLangChange);
    return () => {
      i18n.off('languageChanged', handleLangChange);
    };
  }, []);

  const handleLogin = (userData: User) => {
    setUser(userData);
    localStorage.setItem('ks_user', JSON.stringify(userData));
  };

  const handleLogout = () => {
    setUser(null);
    localStorage.removeItem('ks_user');
  };

  // Fix: Helper function to update language state and i18next instance
  const handleSetLanguage = (lang: Language) => {
    setLanguage(lang);
    i18n.changeLanguage(lang);
  };

  if (loading) return null;

  return (
    <HashRouter>
      <Layout 
        user={user} 
        onLogout={handleLogout}
      >
        <Routes>
          {/* Fix: Added language and setLanguage props to Login and Signup */}
          <Route path="/login" element={!user ? <Login onLogin={handleLogin} language={language} setLanguage={handleSetLanguage} /> : <Navigate to="/" />} />
          <Route path="/signup" element={!user ? <Signup onLogin={handleLogin} language={language} setLanguage={handleSetLanguage} /> : <Navigate to="/" />} />
          <Route path="/" element={user ? <Dashboard /> : <Navigate to="/login" />} />
          <Route path="/crop-disease" element={user ? <CropDisease /> : <Navigate to="/login" />} />
          <Route path="/livestock" element={user ? <Livestock /> : <Navigate to="/login" />} />
          <Route path="/market-insights" element={user ? <MarketInsights /> : <Navigate to="/login" />} />
          {/* Fix: Added language prop to IoTMonitoring, GovernmentSchemes, LoansPage, TradersDirectory, CarbonCredits, and LiveMarketPrices */}
          <Route path="/iot" element={user ? <IoTMonitoring language={language} /> : <Navigate to="/login" />} />
          <Route path="/schemes" element={user ? <GovernmentSchemes language={language} /> : <Navigate to="/login" />} />
          <Route path="/loans" element={user ? <LoansPage language={language} /> : <Navigate to="/login" />} />
          <Route path="/traders" element={user ? <TradersDirectory language={language} /> : <Navigate to="/login" />} />
          <Route path="/carbon" element={user ? <CarbonCredits language={language} /> : <Navigate to="/login" />} />
          <Route path="/live-prices" element={user ? <LiveMarketPrices language={language} /> : <Navigate to="/login" />} />
          <Route path="/chatbot" element={user ? <Chatbot /> : <Navigate to="/login" />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Layout>
    </HashRouter>
  );
};

const container = document.getElementById('root');
if (container) {
  const root = createRoot(container);
  root.render(<App />);
}
