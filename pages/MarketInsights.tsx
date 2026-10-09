
import React, { useState } from 'react';
import { BarChart3, Sprout, Loader2, Info, Sparkles, BarChart2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { getMarketRecommendations } from '../geminiService';
import VoiceControls from '../components/VoiceControls.tsx';
import VoiceFormFillButton from '../components/VoiceFormFillButton.tsx';

const MarketInsights: React.FC = () => {
  const { t, i18n } = useTranslation();
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [form, setForm] = useState({
    location: 'Rajasthan',
    soilType: 'Sandy',
    farmSize: '5',
    currentCrop: 'Wheat',
    irrigation: 'Borewell'
  });

  const handleGetStrategy = async () => {
    setLoading(true);
    try {
      const res = await getMarketRecommendations(form, i18n.language as any);
      setResult(res || '');
    } finally {
      setLoading(false);
    }
  };

  const handleVoiceFill = (data: Record<string, any>) => {
    setForm(prev => ({ ...prev, ...data }));
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      <div className="glass p-10 rounded-[2.5rem] border border-slate-800">
        <h1 className="text-4xl font-extrabold text-white mb-2">{t('market.title')}</h1>
        <p className="text-slate-400">{t('market.subtitle')}</p>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-1 glass p-8 rounded-3xl border border-slate-800 space-y-6 h-fit relative">
          <div className="flex items-center justify-between">
            <h3 className="text-xl font-bold flex items-center gap-2">
              <Info className="text-emerald-500 w-5 h-5" /> {t('market.farm_profile')}
            </h3>
            
            <VoiceFormFillButton 
              fields={['location', 'soilType', 'currentCrop']} 
              language={i18n.language as any} 
              onFill={handleVoiceFill}
              size="md"
            />
          </div>
          
          <div className="space-y-4">
            <div className="relative group">
              <label className="block text-xs font-bold text-slate-500 uppercase tracking-widest mb-2">{t('common.location')}</label>
              <input 
                type="text" 
                value={form.location}
                onChange={(e) => setForm({...form, location: e.target.value})}
                className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:ring-2 focus:ring-emerald-500 outline-none transition-all"
                placeholder="e.g. Nagpur, Maharashtra"
              />
            </div>
            
            <div>
              <label className="block text-xs font-bold text-slate-500 uppercase tracking-widest mb-2">{t('common.soil_type')}</label>
              <select 
                value={form.soilType}
                onChange={(e) => setForm({...form, soilType: e.target.value})}
                className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:ring-2 focus:ring-emerald-500 outline-none"
              >
                <option value="Sandy">Sandy</option>
                <option value="Loamy">Loamy</option>
                <option value="Clay">Clay</option>
                <option value="Silty">Silty</option>
              </select>
            </div>

            <div className="relative">
              <label className="block text-xs font-bold text-slate-500 uppercase tracking-widest mb-2">{t('market.current_crop')}</label>
              <input 
                type="text" 
                value={form.currentCrop}
                onChange={(e) => setForm({...form, currentCrop: e.target.value})}
                className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:ring-2 focus:ring-emerald-500 outline-none transition-all"
                placeholder="e.g. Wheat"
              />
            </div>

            <div>
              <label className="block text-xs font-bold text-slate-500 uppercase tracking-widest mb-2">Farm Size (Acres)</label>
              <input 
                type="number" 
                value={form.farmSize}
                onChange={(e) => setForm({...form, farmSize: e.target.value})}
                className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:ring-2 focus:ring-emerald-500 outline-none transition-all"
              />
            </div>

            <button 
              onClick={handleGetStrategy}
              disabled={loading}
              className="w-full bg-emerald-600 hover:bg-emerald-500 text-white font-bold py-4 rounded-xl flex items-center justify-center gap-2 transition-all shadow-lg shadow-emerald-900/20 active:scale-[0.98]"
            >
              {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : <Sprout className="w-5 h-5" />}
              {t('market.get_recommendations')}
            </button>
          </div>

          <div className="mt-4 p-4 bg-slate-950/50 rounded-2xl border border-slate-800">
             <p className="text-[10px] text-slate-500 font-bold uppercase tracking-widest mb-2 flex items-center gap-2">
                <Sparkles className="w-3 h-3 text-emerald-500" /> {t('market.voice_hint_title') || 'Voice Activation'}
             </p>
             <p className="text-[11px] text-slate-400 italic leading-relaxed">
                {t('market.voice_hint_text') || 'Tap the mic and say: "My location is Punjab, soil is loamy, crop is wheat."'}
             </p>
          </div>
        </div>

        <div className="lg:col-span-2">
          {result ? (
            <div className="glass p-8 rounded-3xl border border-slate-800 space-y-6 animate-in slide-in-from-bottom-4 relative shadow-2xl">
              <div className="flex items-center justify-between">
                <h3 className="text-2xl font-bold text-emerald-400 tracking-tight">{t('market.strategy_title')}</h3>
                <div className="flex items-center gap-3">
                  <VoiceControls enableSpeaker textToSpeak={result} lang={i18n.language as any} />
                  <div className="p-2 bg-emerald-500/10 rounded-xl">
                    <BarChart3 className="text-emerald-500 w-6 h-6" />
                  </div>
                </div>
              </div>
              <div className="p-6 bg-slate-950/50 rounded-2xl border border-slate-800 prose prose-invert max-w-none prose-sm text-slate-300 leading-relaxed whitespace-pre-wrap">
                {result}
              </div>
            </div>
          ) : (
            <div className="h-full glass rounded-[2rem] border border-dashed border-slate-800 flex flex-col items-center justify-center text-center p-12 opacity-50 bg-slate-900/30">
              <BarChart2 className="w-16 h-16 text-slate-700 mb-6" />
              <h3 className="text-xl font-bold text-slate-400">{t('market.awaiting_profile')}</h3>
              <p className="text-slate-500 mt-2 max-w-sm">{t('market.profile_hint')}</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default MarketInsights;
