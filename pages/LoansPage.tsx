
import React, { useState } from 'react';
import { 
  IndianRupee, 
  Calculator, 
  Sparkles, 
  Loader2, 
  CheckCircle2, 
  ArrowRight, 
  FileText,
  Building2
} from 'lucide-react';
import { Language, LoanAdvisorResult } from '../types';
import { TRANSLATIONS } from '../constants';
import { getLoanAdvice } from '../geminiService';
import VoiceControls from '../components/VoiceControls.tsx';
import VoiceFormFillButton from '../components/VoiceFormFillButton.tsx';

const LoansPage: React.FC<{ language: Language }> = ({ language }) => {
  const t = TRANSLATIONS[language];
  const [loading, setLoading] = useState(false);
  const [aiResult, setAiResult] = useState<LoanAdvisorResult | null>(null);
  
  const [form, setForm] = useState({
    name: '',
    phone: '',
    state: '',
    farmSize: '',
    cropType: 'Wheat',
    landOwnership: 'Owned',
    annualIncome: '',
    loanAmount: '',
    purpose: 'Seeds',
    existingLoan: 'No',
    kccHolder: 'No',
    aadhaarLinked: 'Yes'
  });

  const [emiForm, setEmiForm] = useState({
    amount: 100000,
    rate: 7,
    tenure: 12
  });
  const [emiResult, setEmiResult] = useState<{ emi: number, totalInterest: number, totalPayable: number } | null>(null);

  const handleCheckEligibility = async () => {
    setLoading(true);
    try {
      const advice = await getLoanAdvice(form, language);
      setAiResult(advice);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleVoiceFill = (data: Record<string, any>) => {
    setForm(prev => ({ ...prev, ...data }));
  };

  const calculateEMI = () => {
    const P = emiForm.amount;
    const r = emiForm.rate / 12 / 100;
    const n = emiForm.tenure;
    const emi = (P * r * Math.pow(1 + r, n)) / (Math.pow(1 + r, n) - 1);
    const totalPayable = emi * n;
    const totalInterest = totalPayable - P;
    setEmiResult({ emi: Math.round(emi), totalInterest: Math.round(totalInterest), totalPayable: Math.round(totalPayable) });
  };

  return (
    <div className="space-y-12 animate-in fade-in duration-700 pb-20">
      <div className="glass p-12 rounded-[3rem] border border-slate-800 relative overflow-hidden shadow-2xl">
        <div className="absolute top-0 right-0 p-12 opacity-5 -z-10 rotate-12 translate-x-10 -translate-y-10">
          <IndianRupee className="w-64 h-64 text-emerald-500" />
        </div>
        <div className="relative z-10 max-w-3xl space-y-6">
          <div className="w-fit p-4 bg-emerald-500/10 rounded-2xl border border-emerald-500/20">
            <Building2 className="w-10 h-10 text-emerald-500" />
          </div>
          <h1 className="text-5xl font-extrabold text-white tracking-tight leading-tight">{t.loans}</h1>
          <p className="text-xl text-slate-400 leading-relaxed max-w-2xl">{t.loansSubtitle}</p>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-10">
        <div className="glass p-10 rounded-[2.5rem] border border-slate-800 space-y-8 bg-slate-900/30">
          <div className="flex items-center justify-between">
            <h2 className="text-2xl font-bold flex items-center gap-3 tracking-tight">
              <FileText className="text-emerald-500 w-7 h-7" /> Farm Profile & Request
            </h2>
            <VoiceFormFillButton 
              fields={['name', 'state', 'farmSize', 'annualIncome']} 
              language={language} 
              onFill={handleVoiceFill} 
              size="md"
            />
          </div>
          
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
            <div className="space-y-2">
              <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-widest ml-1">Full Name</label>
              <input type="text" value={form.name} onChange={e => setForm({...form, name: e.target.value})} className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-4 text-white outline-none focus:ring-2 focus:ring-emerald-500 transition-all" placeholder="John Doe" />
            </div>
            <div className="space-y-2">
              <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-widest ml-1">District/State</label>
              <input type="text" value={form.state} onChange={e => setForm({...form, state: e.target.value})} className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-4 text-white outline-none focus:ring-2 focus:ring-emerald-500 transition-all" placeholder="Pune, MH" />
            </div>
            <div>
              <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-widest mb-2 ml-1">Land Size (Acres)</label>
              <input type="number" value={form.farmSize} onChange={e => setForm({...form, farmSize: e.target.value})} className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-4 text-white outline-none focus:ring-2 focus:ring-emerald-500 transition-all" />
            </div>
            <div>
              <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-widest mb-2 ml-1">Income Range</label>
              <select value={form.annualIncome} onChange={e => setForm({...form, annualIncome: e.target.value})} className="w-full bg-slate-950 border border-slate-800 rounded-xl px-4 py-4 text-white outline-none focus:ring-2 focus:ring-emerald-500 transition-all cursor-pointer">
                <option value="">Select Range</option>
                <option value="Low">Under ₹1,00,000</option>
                <option value="Medium">₹1,00,000 - ₹5,00,000</option>
                <option value="High">Above ₹5,00,000</option>
              </select>
            </div>
          </div>

          <button 
            onClick={handleCheckEligibility}
            disabled={loading}
            className="w-full bg-emerald-600 hover:bg-emerald-500 text-white font-bold py-5 rounded-2xl flex items-center justify-center gap-2 transition-all shadow-xl shadow-emerald-900/20 active:scale-[0.98]"
          >
            {loading ? <Loader2 className="w-6 h-6 animate-spin" /> : <Sparkles className="w-6 h-6" />}
            {t.checkEligibility}
          </button>
        </div>

        <div className="space-y-6">
          {aiResult ? (
            <div className="glass p-10 rounded-[2.5rem] border border-slate-800 space-y-8 animate-in slide-in-from-right-6 relative shadow-2xl bg-slate-900/50">
              <div className="flex items-center justify-between">
                <div className="space-y-1">
                  <h3 className="text-2xl font-bold text-white tracking-tight">AI Advisor Result</h3>
                  <p className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">Report ID: KS-LN-{Date.now().toString(36).toUpperCase().slice(-6)}</p>
                </div>
                <div className="flex items-center gap-4">
                  <VoiceControls 
                    enableSpeaker 
                    textToSpeak={`Recommendation: ${aiResult.recommendedLoan}. Eligibility Level is ${aiResult.eligibilityLevel}. Next steps: ${aiResult.nextSteps.join(', ')}`} 
                    lang={language} 
                    size="lg"
                  />
                  <span className={`px-5 py-2 rounded-full text-xs font-bold uppercase tracking-widest border ${
                    aiResult.eligibilityLevel === 'High' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/30' :
                    aiResult.eligibilityLevel === 'Medium' ? 'bg-amber-500/10 text-amber-400 border-amber-500/30' : 'bg-red-500/10 text-red-400 border-red-500/30'
                  }`}>
                    {aiResult.eligibilityLevel} Eligibility
                  </span>
                </div>
              </div>

              <div className="p-8 bg-slate-950/80 rounded-[2rem] border border-slate-800 shadow-inner group">
                <p className="text-[10px] text-slate-600 font-bold uppercase mb-2 tracking-widest">Best Product</p>
                <h4 className="text-2xl font-bold text-emerald-400 group-hover:text-emerald-300 transition-colors">{aiResult.recommendedLoan}</h4>
                <div className="flex gap-8 mt-4">
                  <div className="space-y-1">
                     <p className="text-[9px] text-slate-600 font-bold uppercase">Estimated Amount</p>
                     <p className="text-lg font-bold text-white">{aiResult.amountRange}</p>
                  </div>
                  <div className="space-y-1">
                     <p className="text-[9px] text-slate-600 font-bold uppercase">Rate (Annual)</p>
                     <p className="text-lg font-bold text-white">{aiResult.estimatedInterestRange}</p>
                  </div>
                </div>
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-8">
                <div className="space-y-4">
                  <p className="text-[10px] text-slate-500 font-bold uppercase tracking-widest flex items-center gap-2">
                    <CheckCircle2 className="w-3 h-3 text-emerald-500" /> Documents Needed
                  </p>
                  <ul className="space-y-2">
                    {aiResult.documents?.map((d, i) => (
                      <li key={i} className="text-sm text-slate-300 flex items-center gap-3">
                        <div className="w-1.5 h-1.5 bg-emerald-500/50 rounded-full shrink-0" /> {d}
                      </li>
                    ))}
                  </ul>
                </div>
                <div className="space-y-4">
                  <p className="text-[10px] text-slate-500 font-bold uppercase tracking-widest flex items-center gap-2">
                    <ArrowRight className="w-3 h-3 text-emerald-500" /> Next Steps
                  </p>
                  <ul className="space-y-2">
                    {aiResult.nextSteps?.map((s, i) => (
                      <li key={i} className="text-sm text-slate-300 flex items-center gap-3">
                        <ArrowRight className="w-3 h-3 text-emerald-500 shrink-0" /> {s}
                      </li>
                    ))}
                  </ul>
                </div>
              </div>
              
              <button className="w-full py-4 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-2xl shadow-lg transition-all active:scale-[0.98]">Proceed to Bank Application</button>
            </div>
          ) : (
            <div className="h-full glass rounded-[2.5rem] border border-dashed border-slate-800 flex flex-col items-center justify-center text-center p-12 opacity-30 bg-slate-900/10">
              <Sparkles className="w-20 h-20 text-slate-700 mb-8" />
              <h3 className="text-2xl font-bold text-slate-400 tracking-tight">Financial Hub Active</h3>
              <p className="text-slate-500 mt-4 max-w-sm text-lg leading-relaxed">Let AI scan your farm data to find government-backed subsidies and low-interest credit lines suitable for your profile.</p>
            </div>
          )}
        </div>
      </div>

      <div className="glass p-12 rounded-[3rem] border border-slate-800 shadow-2xl bg-slate-900/20">
        <div className="flex items-center justify-between mb-10">
           <h2 className="text-3xl font-extrabold text-white tracking-tight flex items-center gap-4">
            <div className="p-3 bg-emerald-500/10 rounded-2xl">
              <Calculator className="text-emerald-500 w-8 h-8" />
            </div>
            Smart EMI Calculator
          </h2>
        </div>
        
        <div className="flex flex-col lg:flex-row gap-16">
          <div className="flex-1 space-y-10">
            <div className="space-y-6">
              <div className="flex justify-between items-end">
                <div className="space-y-1">
                  <p className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Loan Amount</p>
                  <p className="text-4xl font-extrabold text-white">₹{emiForm.amount.toLocaleString()}</p>
                </div>
                <div className="p-3 bg-emerald-500/5 rounded-xl border border-emerald-500/10">
                   <IndianRupee className="w-5 h-5 text-emerald-500" />
                </div>
              </div>
              <input type="range" min="10000" max="5000000" step="10000" value={emiForm.amount} onChange={e => setEmiForm({...emiForm, amount: +e.target.value})} className="w-full h-2.5 bg-slate-800 rounded-lg appearance-none cursor-pointer accent-emerald-500" />
              <div className="flex justify-between text-[10px] font-bold text-slate-600 uppercase tracking-widest">
                 <span>10K</span>
                 <span>50 Lakhs</span>
              </div>
            </div>
            
            <div className="grid grid-cols-2 gap-10">
              <div className="space-y-3">
                <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest ml-1">Rate (%)</label>
                <input type="number" value={emiForm.rate} onChange={e => setEmiForm({...emiForm, rate: +e.target.value})} className="w-full bg-slate-950 border border-slate-800 rounded-xl px-5 py-4 text-white outline-none focus:ring-2 focus:ring-emerald-500 transition-all font-bold" />
              </div>
              <div className="space-y-3">
                <label className="text-[10px] font-bold text-slate-500 uppercase tracking-widest ml-1">Tenure (Months)</label>
                <input type="number" value={emiForm.tenure} onChange={e => setEmiForm({...emiForm, tenure: +e.target.value})} className="w-full bg-slate-950 border border-slate-800 rounded-xl px-5 py-4 text-white outline-none focus:ring-2 focus:ring-emerald-500 transition-all font-bold" />
              </div>
            </div>

            <button onClick={calculateEMI} className="w-full bg-emerald-600 hover:bg-emerald-500 text-white font-bold py-5 rounded-[1.5rem] transition-all shadow-xl shadow-emerald-900/20 active:scale-[0.98]">{t.calculateEMI}</button>
          </div>

          <div className="flex-1 bg-slate-950/80 rounded-[2.5rem] border border-slate-800 p-10 flex flex-col justify-center text-center shadow-inner relative overflow-hidden group">
            <div className="absolute inset-0 bg-emerald-500/5 opacity-0 group-hover:opacity-100 transition-opacity"></div>
            {emiResult ? (
              <div className="space-y-10 animate-in zoom-in relative z-10">
                <div className="flex justify-end absolute top-0 right-0">
                  <VoiceControls 
                    enableSpeaker 
                    textToSpeak={`Your monthly EMI will be ${emiResult.emi} rupees. Total interest payable over the term is ${emiResult.totalInterest} rupees.`} 
                    lang={language} 
                  />
                </div>
                <div className="space-y-3">
                  <p className="text-slate-500 text-[10px] font-bold uppercase tracking-[0.3em] mb-1">{t.monthlyEMI}</p>
                  <h3 className="text-6xl font-black text-white tracking-tight">₹{emiResult.emi.toLocaleString()}</h3>
                </div>
                <div className="grid grid-cols-2 gap-8 pt-10 border-t border-slate-800/50">
                  <div className="space-y-1 text-left">
                    <p className="text-slate-600 text-[9px] font-bold uppercase tracking-widest">{t.totalInterest}</p>
                    <p className="text-2xl font-bold text-amber-500">₹{emiResult.totalInterest.toLocaleString()}</p>
                  </div>
                  <div className="space-y-1 text-right">
                    <p className="text-slate-600 text-[9px] font-bold uppercase tracking-widest">{t.totalPayable}</p>
                    <p className="text-2xl font-bold text-white">₹{emiResult.totalPayable.toLocaleString()}</p>
                  </div>
                </div>
                <div className="pt-4">
                   <p className="text-[10px] text-slate-500 italic">This is an indicative estimate based on reduced balance method.</p>
                </div>
              </div>
            ) : (
              <div className="text-center py-20 opacity-30">
                <Calculator className="w-20 h-20 mx-auto mb-6 text-slate-700" />
                <p className="text-xl font-bold text-slate-400">Calculate your monthly farm commitment</p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default LoansPage;
