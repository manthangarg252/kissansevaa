
import React from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { Leaf, User, LogOut, MessageCircle, ChevronDown } from 'lucide-react';

interface LayoutProps {
  children: React.ReactNode;
  user: any;
  onLogout: () => void;
}

const Layout: React.FC<LayoutProps> = ({ children, user, onLogout }) => {
  const navigate = useNavigate();
  const location = useLocation();
  const { t, i18n } = useTranslation();

  const isAuthPage = location.pathname === '/login' || location.pathname === '/signup';

  const languages = [
    { code: 'en', name: 'English', native: 'English', color: 'bg-blue-600' },
    { code: 'hi', name: 'Hindi', native: 'हिन्दी', color: 'bg-emerald-600' },
    { code: 'mr', name: 'Marathi', native: 'मराठी', color: 'bg-indigo-600' },
    { code: 'pa', name: 'Punjabi', native: 'ਪੰਜਾਬੀ', color: 'bg-orange-600' },
    { code: 'sa', name: 'Sanskrit', native: 'संस्कृतम्', color: 'bg-amber-600' }
  ];

  const changeLanguage = (code: string) => {
    i18n.changeLanguage(code);
  };

  if (isAuthPage) return <>{children}</>;

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col">
      {/* Navbar */}
      <nav className="h-16 glass fixed top-0 left-0 right-0 z-50 px-6 flex items-center justify-between border-b border-slate-800">
        <div 
          className="flex items-center gap-2 cursor-pointer group" 
          onClick={() => navigate('/')}
        >
          <div className="p-2 bg-emerald-500/10 rounded-lg group-hover:bg-emerald-500/20 transition-all">
            <Leaf className="w-6 h-6 text-emerald-500" />
          </div>
          <span className="text-xl font-bold tracking-tight">KissansevaAI</span>
        </div>

        <div className="flex items-center gap-4">
          <div className="flex bg-slate-900 rounded-lg p-1 border border-slate-800 gap-1 overflow-x-auto no-scrollbar max-w-[500px]">
            {languages.map((lang) => (
              <button 
                key={lang.code}
                onClick={() => changeLanguage(lang.code)}
                className={`px-3 py-1 rounded-md text-[10px] md:text-xs font-bold transition-all whitespace-nowrap ${i18n.language === lang.code ? `${lang.color} text-white shadow-lg` : 'text-slate-400 hover:text-white'}`}
              >
                {lang.native}
              </button>
            ))}
          </div>

          <div className="flex items-center gap-3 pl-4 border-l border-slate-800">
            <button className="p-1.5 bg-slate-800 rounded-full text-slate-300 hover:text-white transition-colors">
              <User className="w-6 h-6" />
            </button>
            <button 
              onClick={onLogout}
              className="p-2 text-slate-400 hover:text-red-400 transition-colors"
              title={t('nav.logout')}
            >
              <LogOut className="w-5 h-5" />
            </button>
          </div>
        </div>
      </nav>

      <main className="flex-1 pt-20 pb-10 px-6 max-w-7xl mx-auto w-full">
        {children}
      </main>

      {/* Floating Chatbot */}
      <button 
        onClick={() => navigate('/chatbot')}
        className="fixed bottom-8 right-8 w-14 h-14 bg-emerald-600 hover:bg-emerald-500 text-white rounded-full shadow-2xl flex items-center justify-center transition-all hover:scale-110 z-50 group"
      >
        <div className="relative">
          <MessageCircle className="w-7 h-7" />
          <span className="absolute -top-1 -right-1 w-3 h-3 bg-red-500 border-2 border-slate-950 rounded-full"></span>
        </div>
        <span className="absolute right-16 bg-emerald-600 px-3 py-1 rounded-lg text-sm font-medium opacity-0 group-hover:opacity-100 pointer-events-none transition-all">{t('nav.chatbot')}</span>
      </button>
    </div>
  );
};

export default Layout;
