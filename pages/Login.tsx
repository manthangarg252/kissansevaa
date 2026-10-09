
import React, { useState } from 'react';
import { User, Lock, ArrowRight, Leaf, Eye, EyeOff, Loader2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';
import { Language } from '../types.ts';
import { TRANSLATIONS } from '../constants.tsx';

interface LoginProps {
  onLogin: (user: any) => void;
  language: Language;
  setLanguage: (lang: Language) => void;
}

const Login: React.FC<LoginProps> = ({ onLogin, language, setLanguage }) => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [showPassword, setShowPassword] = useState(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  const t = TRANSLATIONS[language];

  const validateEmail = (emailStr: string) => {
    return String(emailStr)
      .toLowerCase()
      .match(
        /^(([^<>()[\]\\.,;:\s@"]+(\.[^<>()[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/
      );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    if (!email) {
      setError(t.emailRequired);
      return;
    }
    if (!validateEmail(email)) {
      setError(t.invalidEmail);
      return;
    }
    if (!password) {
      setError(t.passwordRequired);
      return;
    }

    setLoading(true);
    // Simulate auth delay
    setTimeout(() => {
      setLoading(false);
      onLogin({ id: '1', email, name: email.split('@')[0] });
      navigate('/');
    }, 1500);
  };

  const LanguageToggle = () => (
    <div className="absolute top-8 right-8 flex bg-slate-900/50 p-1 rounded-xl border border-slate-700/50 backdrop-blur-sm">
      <button
        onClick={() => setLanguage('en')}
        className={`px-4 py-1.5 rounded-lg text-xs font-bold transition-all ${language === 'en' ? 'bg-emerald-600 text-white shadow-lg' : 'text-slate-400 hover:text-white'}`}
      >
        English
      </button>
      <button
        onClick={() => setLanguage('hi')}
        className={`px-4 py-1.5 rounded-lg text-xs font-bold transition-all ${language === 'hi' ? 'bg-emerald-600 text-white shadow-lg' : 'text-slate-400 hover:text-white'}`}
      >
        हिन्दी
      </button>
    </div>
  );

  return (
    <div className="min-h-screen relative flex items-center justify-center p-6 overflow-hidden">
      <div 
        className="absolute inset-0 z-0 scale-105"
        style={{
          backgroundImage: 'url("https://images.unsplash.com/photo-1500382017468-9049fed747ef?auto=format&fit=crop&q=80&w=2000")',
          backgroundSize: 'cover',
          backgroundPosition: 'center'
        }}
      >
        <div className="absolute inset-0 bg-slate-950/80 backdrop-blur-sm"></div>
      </div>

      <LanguageToggle />

      <div className="relative z-10 w-full max-w-md animate-in fade-in zoom-in duration-700">
        <div className="glass p-10 rounded-[2.5rem] border border-slate-700/50 shadow-2xl relative overflow-hidden">
          <div className="absolute -top-24 -left-24 w-48 h-48 bg-emerald-500/5 blur-[80px]"></div>
          
          <div className="text-center mb-10">
            <div className="inline-flex p-4 bg-emerald-500/10 rounded-3xl mb-6 border border-emerald-500/20">
              <Leaf className="w-8 h-8 text-emerald-500" />
            </div>
            <h1 className="text-4xl font-extrabold text-white mb-2 tracking-tight">{t.loginTitle}</h1>
            <p className="text-slate-400 text-sm font-medium">{t.loginSubtitle}</p>
          </div>

          <form onSubmit={handleSubmit} className="space-y-6">
            {error && (
              <div className="p-4 bg-red-500/10 border border-red-500/20 rounded-2xl text-red-400 text-xs font-bold flex items-center gap-2 animate-in slide-in-from-top-2">
                <div className="w-1 h-1 bg-red-400 rounded-full animate-pulse"></div>
                {error}
              </div>
            )}

            <div className="space-y-2">
              <label className="text-xs font-bold text-slate-500 uppercase tracking-widest ml-1">{t.emailLabel}</label>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
                  <User className="w-5 h-5 text-slate-500 group-focus-within:text-emerald-500 transition-colors" />
                </div>
                <input 
                  type="email" 
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full bg-slate-900/50 border border-slate-700/50 rounded-2xl pl-12 pr-4 py-4 text-white placeholder-slate-600 focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500 outline-none transition-all"
                  placeholder={t.emailPlaceholder}
                />
              </div>
            </div>

            <div className="space-y-2">
              <div className="flex justify-between items-center ml-1">
                <label className="text-xs font-bold text-slate-500 uppercase tracking-widest">{t.passwordLabel}</label>
                <button type="button" className="text-[10px] font-bold text-emerald-500 hover:text-emerald-400 uppercase tracking-widest">{t.forgotPassword}</button>
              </div>
              <div className="relative group">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
                  <Lock className="w-5 h-5 text-slate-500 group-focus-within:text-emerald-500 transition-colors" />
                </div>
                <input 
                  type={showPassword ? "text" : "password"}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="w-full bg-slate-900/50 border border-slate-700/50 rounded-2xl pl-12 pr-12 py-4 text-white placeholder-slate-600 focus:ring-2 focus:ring-emerald-500/50 focus:border-emerald-500 outline-none transition-all"
                  placeholder={t.passwordPlaceholder}
                />
                <button 
                  type="button"
                  onClick={() => setShowPassword(!showPassword)}
                  className="absolute inset-y-0 right-0 pr-4 flex items-center text-slate-500 hover:text-emerald-500 transition-colors"
                >
                  {showPassword ? <EyeOff className="w-5 h-5" /> : <Eye className="w-5 h-5" />}
                </button>
              </div>
            </div>

            <button 
              type="submit"
              disabled={loading}
              className="w-full bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-800 disabled:cursor-not-allowed text-white font-bold py-4 rounded-2xl transition-all shadow-xl shadow-emerald-900/20 flex items-center justify-center gap-2 group mt-8"
            >
              {loading ? (
                <>
                  <Loader2 className="w-5 h-5 animate-spin" />
                  {t.signingIn}
                </>
              ) : (
                <>
                  {t.signIn}
                  <ArrowRight className="w-5 h-5 group-hover:translate-x-1 transition-transform" />
                </>
              )}
            </button>
          </form>

          <div className="mt-8 text-center">
            <p className="text-slate-400 text-sm font-medium">
              {t.noAccount} 
              <button 
                onClick={() => navigate('/signup')}
                className="text-emerald-400 hover:text-emerald-300 font-bold ml-1 transition-colors"
              >
                {t.signUp}
              </button>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;
