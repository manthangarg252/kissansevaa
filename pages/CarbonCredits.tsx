import React, { useState } from 'react';
import { Zap, Calculator, Globe, Leaf, Loader2 } from 'lucide-react';
import { Language } from '../types';
import { TRANSLATIONS } from '../constants';
import { estimateCarbonCredits } from '../geminiService';
import VoiceControls from '../components/VoiceControls.tsx';

const CarbonCredits: React.FC<{ language: Language }> = ({ language }) => {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [form, setForm] = useState({
    farmSize: '10',
    cropType: 'Rice',
    method: 'Organic',
    irrigation: 'Drip'
  });

  const t = TRANSLATIONS[language];

  const handleEstimate = async () => {
    setLoading(true);
    try {
      const res = await estimateCarbonCredits(form, language);
      setResult(res || '');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-500 pb-20">
      <div className="glass p-10 rounded-[2.5rem] border border-slate-800 relative overflow-hidden shadow-2xl">
        <div className="absolute top-0 right-0 p-10 opacity-10">
          <Globe className="w-40 h-40 text-emerald-500" />
        </div>
        <div className="relative z-10 max-w-2xl space-y-4">
          <h1 className="text-5xl font-extrabold text-white tracking-tight">{t.carbon}</h1>
          <p className="text-xl text-slate-400 leading-relaxed">Estimate your potential earnings from sustainable farming practices and join the global carbon market.</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-1 space-y-6">
          <div className="glass p-8 rounded-[2rem] border border-slate-800 shadow-xl bg-slate-900/30">
            <div className="flex items-center justify-between mb-8">
              <h3 className="text-xl font-bold flex items-center gap-3">
                <Calculator className="text-emerald-500 w-6 h-6" /> Estimator Tool
              </h3>
              <div className="p-2 bg-emerald-500/10 rounded-xl">
                <Leaf className="w-4 h-4 text-emerald-500" />
              </div>
            </div>
            
            <div className="space-y-6">
              <div className="space-y-2">
                <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-widest ml-1">Farm Size (Acres)</label>
                <input type="number" value={form.farmSize} onChange={(e) => setForm({...form, farmSize: e.target.value})} className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-4 text-white outline-none focus:ring-2 focus:ring-emerald-500 transition-all" />
              </div>
              <div className="space-y-2">
                <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-widest ml-1">Primary Crop</label>
                <div className="flex gap-2">
                  <input type="text" value={form.cropType} onChange={(e) => setForm({...form, cropType: e.target.value})} className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-4 py-4 text-white outline-none focus:ring-2 focus:ring-emerald-500 transition-all" />
                  <VoiceControls enableMic lang={language} onMicResult={v => setForm({...form, cropType: v})} />
                </div>
              </div>
              <div className="space-y-2">
                <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-widest ml-1">Farming Method</label>
                <select value={form.method} onChange={(e) => setForm({...form, method: e.target.value})} className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-4 text-white outline-none focus:ring-2 focus:ring-emerald-500 transition-all cursor-pointer">
                  <option>Organic</option>
                  <option>Chemical</option>
                  <option>No-Till</option>
                  <option>Agroforestry</option>
                </select>
              </div>

              <button 
                onClick={handleEstimate}
                disabled={loading}
                className="w-full bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-800 text-white font-bold py-5 rounded-2xl flex items-center justify-center gap-2 transition-all shadow-xl shadow-emerald-900/20 active:scale-[0.98]"
              >
                {loading ? <Loader2 className="w-6 h-6 animate-spin" /> : <Zap className="w-6 h-6" />}
                {t.carbonEstimation}
              </button>
            </div>
          </div>
          
          <div className="glass p-6 rounded-2xl border border-slate-800 bg-slate-900/20">
             <div className="flex items-center gap-3 text-emerald-400 mb-2">
                <Leaf className="w-4 h-4" />
                <span className="text-xs font-bold uppercase tracking-widest">Sustainability Pro Tip</span>
             </div>
             <p className="text-[11px] text-slate-400 leading-relaxed italic">Switching to Drip Irrigation can increase your carbon absorption score by up to 15% in sandy soils.</p>
          </div>
        </div>

        <div className="lg:col-span-2">
          {result ? (
            <div className="glass p-10 rounded-[2.5rem] border border-slate-800 space-y-8 animate-in slide-in-from-bottom-6 relative shadow-2xl bg-slate-900/40">
              <div className="flex items-center justify-between">
                <div className="space-y-1">
                  <h3 className="text-3xl font-extrabold text-white tracking-tight">AI Advisory Report</h3>
                  <p className="text-[10px] font-bold text-emerald-400 uppercase tracking-widest">Carbon Market Eligibility: Verified</p>
                </div>
                <div className="flex items-center gap-4">
                  <VoiceControls enableSpeaker textToSpeak={result} lang={language} size="lg" />
                  <div className="w-16 h-16 bg-emerald-500/10 rounded-[1.5rem] flex items-center justify-center border border-emerald-500/20">
                    <Leaf className="text-emerald-500 w-8 h-8" />
                  </div>
                </div>
              </div>
              <div className="p-8 bg-slate-950/80 rounded-[2rem] border border-slate-800 prose prose-invert max-w-none prose-sm text-slate-300 leading-relaxed whitespace-pre-wrap shadow-inner font-medium">
                {result}
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
                 <button onClick={() => alert('PDF Certificate download will be available soon!')} className="py-4 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-2xl shadow-lg transition-all">Download PDF Certificate</button>
                 <button onClick={() => alert('Carbon Broker connections coming soon!')} className="py-4 bg-slate-800 hover:bg-slate-700 text-white font-bold rounded-2xl transition-all">Connect to Carbon Broker</button>
              </div>
            </div>
          ) : (
            <div className="h-full glass rounded-[2.5rem] border border-dashed border-slate-800 flex flex-col items-center justify-center text-center p-12 opacity-30 bg-slate-900/10">
              <Globe className="w-20 h-20 text-slate-700 mb-8" />
              <h3 className="text-2xl font-bold text-slate-400">Analysis Pending</h3>
              <p className="text-slate-500 mt-4 max-w-sm text-lg font-medium leading-relaxed">Fill in your farming practices to see how many carbon credits your soil can currently sequestrate.</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default CarbonCredits;
