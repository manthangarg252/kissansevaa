
import React, { useState } from 'react';
import { Gavel, ExternalLink, ShieldCheck, Droplets, Leaf, IndianRupee, Sparkles, Loader2, X, Search, Cpu, TreeDeciduous, Building2, ShoppingCart, FlaskConical, Wheat, Fish, Dog } from 'lucide-react';
import { Language } from '../types';
import { TRANSLATIONS, SCHEMES } from '../constants';
import { getSchemeRecommendations } from '../geminiService';
import VoiceControls from '../components/VoiceControls.tsx';
import VoiceFormFillButton from '../components/VoiceFormFillButton.tsx';

const CategoryIcon: React.FC<{ category: string }> = ({ category }) => {
  switch (category) {
    case 'Irrigation': return <Droplets className="w-4 h-4" />;
    case 'Organic': return <Leaf className="w-4 h-4" />;
    case 'Insurance': return <ShieldCheck className="w-4 h-4" />;
    case 'Financial': return <IndianRupee className="w-4 h-4" />;
    case 'Income Support': return <IndianRupee className="w-4 h-4" />;
    case 'Technology': return <Cpu className="w-4 h-4" />;
    case 'Horticulture': return <TreeDeciduous className="w-4 h-4" />;
    case 'Infrastructure': return <Building2 className="w-4 h-4" />;
    case 'Marketing': return <ShoppingCart className="w-4 h-4" />;
    case 'Soil Health': return <FlaskConical className="w-4 h-4" />;
    case 'Seeds': return <Wheat className="w-4 h-4" />;
    case 'Fisheries': return <Fish className="w-4 h-4" />;
    case 'Animal Husbandry': return <Dog className="w-4 h-4" />;
    default: return <Gavel className="w-4 h-4" />;
  }
};

const GovernmentSchemes: React.FC<{ language: Language }> = ({ language }) => {
  const [showAIModal, setShowAIModal] = useState(false);
  const [loading, setLoading] = useState(false);
  const [aiResult, setAiResult] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [form, setForm] = useState({
    state: 'Maharashtra',
    farmSize: '2.5',
    crop: 'Cotton',
    soilType: 'Black Soil',
    landType: 'Self-owned'
  });

  const handleGetAIAdvice = async () => {
    setLoading(true);
    try {
      const res = await getSchemeRecommendations(form, language);
      setAiResult(res || '');
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleVoiceFill = (data: Record<string, any>) => {
    setForm(prev => ({ ...prev, ...data }));
  };

  const filteredSchemes = SCHEMES.filter(s => 
    s.name.toLowerCase().includes(search.toLowerCase()) || 
    s.category.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      <div className="glass p-10 rounded-[2.5rem] border border-slate-800 flex flex-col md:flex-row md:items-center justify-between gap-6 shadow-2xl">
        <div className="space-y-2">
          <h1 className="text-4xl font-extrabold text-white tracking-tight">{TRANSLATIONS[language].schemes}</h1>
          <p className="text-slate-400">Discover and apply for state and central government initiatives tailored for you.</p>
        </div>
        <div className="flex flex-col sm:flex-row items-center gap-4">
          <div className="relative group w-full sm:w-64">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-slate-500 group-focus-within:text-emerald-500 transition-colors" />
            <input 
              type="text" 
              placeholder="Search schemes..." 
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              className="w-full bg-slate-950 border border-slate-800 rounded-xl pl-10 pr-4 py-3 text-sm text-white focus:ring-2 focus:ring-emerald-500/50 outline-none transition-all"
            />
          </div>
          <button 
            onClick={() => setShowAIModal(true)}
            className="bg-emerald-600 hover:bg-emerald-500 text-white px-8 py-4 rounded-2xl font-bold flex items-center gap-2 transition-all shadow-xl shadow-emerald-900/20 group active:scale-95"
          >
            <Sparkles className="w-5 h-5 group-hover:animate-pulse" />
            {TRANSLATIONS[language].recommendations}
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        {filteredSchemes.map((scheme) => (
          <div key={scheme.id} className="glass p-8 rounded-[2rem] border border-slate-800 hover:border-emerald-500/30 transition-all flex flex-col group relative overflow-hidden">
            <div className="absolute top-0 right-0 p-6 opacity-5 -z-10 group-hover:opacity-10 transition-opacity">
              <CategoryIcon category={scheme.category} />
            </div>
            
            <div className="flex items-center justify-between mb-4">
              <span className="px-3 py-1 bg-emerald-500/10 text-emerald-400 rounded-full text-[10px] font-bold flex items-center gap-2 uppercase tracking-widest border border-emerald-500/20">
                <CategoryIcon category={scheme.category} />
                {scheme.category}
              </span>
              <div className="flex items-center gap-2">
                <VoiceControls 
                  enableSpeaker 
                  textToSpeak={`${scheme.name}. ${scheme.description}. Eligibility: ${scheme.eligibility.join(', ')}`} 
                  lang={language} 
                  size="sm"
                />
              </div>
            </div>
            
            <h3 className="text-xl font-bold text-white mb-3 group-hover:text-emerald-400 transition-colors tracking-tight">
              {scheme.name}
            </h3>
            <p className="text-slate-400 text-sm mb-6 flex-1 leading-relaxed">
              {scheme.description}
            </p>

            <div className="space-y-6">
              <div>
                <p className="text-[10px] font-bold text-slate-500 uppercase tracking-widest mb-3">{TRANSLATIONS[language].eligibility}</p>
                <div className="flex flex-wrap gap-2">
                  {scheme.eligibility.map((item, idx) => (
                    <span key={idx} className="bg-slate-900 border border-slate-800 text-slate-400 text-[10px] font-bold px-3 py-1.5 rounded-lg">
                      • {item}
                    </span>
                  ))}
                </div>
              </div>
              <a 
                href={scheme.link} 
                target="_blank" 
                rel="noopener noreferrer"
                className="inline-flex items-center gap-2 bg-emerald-600 hover:bg-emerald-500 text-white text-sm font-bold px-8 py-3.5 rounded-xl transition-all w-full justify-center shadow-lg active:scale-[0.98]"
              >
                {TRANSLATIONS[language].officialSite} <ExternalLink className="w-4 h-4" />
              </a>
            </div>
          </div>
        ))}
      </div>

      {/* AI Recommendation Modal */}
      {showAIModal && (
        <div className="fixed inset-0 z-[100] flex items-center justify-center p-6 bg-slate-950/80 backdrop-blur-md animate-in fade-in duration-300">
          <div className="glass w-full max-w-2xl rounded-[2.5rem] border border-slate-800 overflow-hidden shadow-2xl flex flex-col max-h-[90vh]">
            <div className="p-8 border-b border-slate-800 flex items-center justify-between bg-slate-900/50">
              <div className="flex items-center gap-3">
                <div className="p-3 bg-emerald-500/20 rounded-2xl">
                  <Sparkles className="w-6 h-6 text-emerald-500" />
                </div>
                <h2 className="text-2xl font-bold text-white tracking-tight">Personalized AI Schemes</h2>
              </div>
              <div className="flex items-center gap-4">
                <VoiceFormFillButton 
                  fields={['state', 'farmSize', 'crop']} 
                  language={language} 
                  onFill={handleVoiceFill} 
                  size="md"
                />
                <button onClick={() => { setShowAIModal(false); setAiResult(null); }} className="p-2 hover:bg-slate-800 rounded-xl transition-colors text-slate-400 hover:text-white">
                  <X className="w-6 h-6" />
                </button>
              </div>
            </div>

            <div className="p-8 overflow-y-auto space-y-6 flex-1 no-scrollbar">
              {!aiResult ? (
                <div className="space-y-6">
                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
                    <div className="space-y-2">
                      <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-widest ml-1">State</label>
                      <input type="text" value={form.state} onChange={e => setForm({...form, state: e.target.value})} className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-white outline-none focus:ring-2 focus:ring-emerald-500 transition-all" />
                    </div>
                    <div>
                      <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-widest mb-2 ml-1">Farm Size (Acres)</label>
                      <input type="number" value={form.farmSize} onChange={e => setForm({...form, farmSize: e.target.value})} className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-white outline-none focus:ring-2 focus:ring-emerald-500 transition-all" />
                    </div>
                  </div>
                  <div className="space-y-2">
                    <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-widest ml-1">Main Crop</label>
                    <input type="text" value={form.crop} onChange={e => setForm({...form, crop: e.target.value})} className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-3 text-white outline-none focus:ring-2 focus:ring-emerald-500 transition-all" />
                  </div>
                  <button 
                    onClick={handleGetAIAdvice}
                    disabled={loading}
                    className="w-full bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-800 text-white font-bold py-4 rounded-2xl flex items-center justify-center gap-2 mt-4 shadow-xl active:scale-[0.98] transition-all"
                  >
                    {loading ? <Loader2 className="w-5 h-5 animate-spin" /> : <Sparkles className="w-5 h-5" />}
                    {TRANSLATIONS[language].apply_hint}
                  </button>
                </div>
              ) : (
                <div className="animate-in slide-in-from-bottom-4 duration-500 space-y-4">
                  <div className="flex justify-end sticky top-0 bg-slate-950/80 backdrop-blur-sm p-2 rounded-xl border border-slate-800 mb-4">
                    <VoiceControls enableSpeaker textToSpeak={aiResult} lang={language} />
                  </div>
                  <div className="p-8 bg-slate-900/50 rounded-3xl border border-slate-800 prose prose-invert prose-sm max-w-none whitespace-pre-wrap text-slate-300 leading-relaxed shadow-inner">
                    {aiResult}
                  </div>
                  <button 
                    onClick={() => setAiResult(null)}
                    className="w-full py-4 text-emerald-400 font-bold text-sm hover:bg-emerald-500/5 rounded-2xl border border-emerald-500/20 transition-all"
                  >
                    Try with different information
                  </button>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default GovernmentSchemes;
