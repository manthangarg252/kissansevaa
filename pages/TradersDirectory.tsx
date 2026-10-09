import React, { useState, useMemo } from 'react';
import { Users, Search, MapPin, Phone, MessageSquare, Filter, Building2, ChevronDown, Star, X, Check, Sparkles, Loader2, Bot } from 'lucide-react';
import { Language, Trader } from '../types';
import { TRANSLATIONS, TRADERS as INITIAL_TRADERS } from '../constants';
import VoiceControls from '../components/VoiceControls.tsx';
import { getTraderAdvisory } from '../geminiService';

const TradersDirectory: React.FC<{ language: Language }> = ({ language }) => {
  const [search, setSearch] = useState('');
  const [selectedLocation, setSelectedLocation] = useState('');
  const [traders, setTraders] = useState<Trader[]>(INITIAL_TRADERS);
  
  // Assistant State
  const [assistantQuery, setAssistantQuery] = useState('');
  const [assistantResponse, setAssistantResponse] = useState<string | null>(null);
  const [isAssistantLoading, setIsAssistantLoading] = useState(false);

  const [ratingModal, setRatingModal] = useState<{ isOpen: boolean; traderId: string | null; traderName: string }>({
    isOpen: false,
    traderId: null,
    traderName: ''
  });
  const [userRating, setUserRating] = useState(0);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const t = TRANSLATIONS[language];

  const locations = useMemo(() => {
    return Array.from(new Set(traders?.map(trader => trader.location))).sort();
  }, [traders]);

  const filteredTraders = (traders || []).filter(trader => {
    const matchesSearch = 
      trader.name.toLowerCase().includes(search.toLowerCase()) ||
      trader.dealsIn?.some(crop => crop.toLowerCase().includes(search.toLowerCase()));
    
    const matchesLocation = selectedLocation === '' || trader.location === selectedLocation;

    return matchesSearch && matchesLocation;
  });

  const handleAskAssistant = async () => {
    if (!assistantQuery.trim()) return;
    setIsAssistantLoading(true);
    setAssistantResponse(null);
    try {
      const res = await getTraderAdvisory(assistantQuery, language);
      setAssistantResponse(res);
    } catch (err) {
      console.error(err);
    } finally {
      setIsAssistantLoading(false);
    }
  };

  const handleOpenRatingModal = (trader: Trader) => {
    setRatingModal({
      isOpen: true,
      traderId: trader.id,
      traderName: trader.name
    });
    setUserRating(0);
  };

  const handleSubmitRating = async () => {
    if (userRating === 0 || !ratingModal.traderId) return;
    setIsSubmitting(true);
    await new Promise(resolve => setTimeout(resolve, 800));
    setTraders(prevTraders => prevTraders.map(t => {
      if (t.id === ratingModal.traderId) {
        const newReviewCount = (t.reviewCount || 0) + 1;
        const newRating = Number((((t.rating || 0) * (t.reviewCount || 0) + userRating) / newReviewCount).toFixed(1));
        return { ...t, rating: newRating, reviewCount: newReviewCount };
      }
      return t;
    }));
    setIsSubmitting(false);
    setRatingModal({ isOpen: false, traderId: null, traderName: '' });
  };

  return (
    <div className="space-y-12 animate-in fade-in duration-700 pb-20">
      <div className="relative text-center max-w-4xl mx-auto space-y-4 py-12">
        <div className="absolute inset-0 hero-blur opacity-50 -z-10"></div>
        <h1 className="text-5xl md:text-7xl font-extrabold text-white tracking-tight leading-tight uppercase">
          {language === 'hi' ? 'व्यापारी और खरीदार' : 'Traders & Buyers'}
        </h1>
        <p className="text-xl text-slate-400 font-medium max-w-2xl mx-auto leading-relaxed">
          {language === 'hi' ? 'अपने क्षेत्र के सत्यापित क्षेत्रीय खरीदारों और व्यापारियों से सीधे जुड़ें।' : 'Connect directly with verified regional buyers and traders in your area.'}
        </p>
      </div>

      {/* AI Trading Assistant */}
      <div className="glass p-10 rounded-[3rem] border border-emerald-500/20 bg-emerald-500/5 relative overflow-hidden shadow-2xl">
        <div className="absolute top-0 right-0 p-10 opacity-5 -z-10">
           <Bot className="w-48 h-48 text-emerald-500" />
        </div>
        <div className="relative z-10 space-y-8">
          <div className="flex items-center gap-4">
            <div className="p-4 bg-emerald-500/20 rounded-2xl shadow-lg shadow-emerald-900/30">
              <Sparkles className="w-8 h-8 text-emerald-500" />
            </div>
            <div>
              <h2 className="text-3xl font-bold text-white tracking-tight">AI Trading Advisor</h2>
              <p className="text-sm text-slate-500 font-bold uppercase tracking-widest">Market Insights & Negotiation Guide</p>
            </div>
          </div>

          <div className="flex flex-col md:flex-row gap-4">
            <div className="flex-1 relative group">
              <input 
                type="text" 
                value={assistantQuery}
                onChange={(e) => setAssistantQuery(e.target.value)}
                placeholder="Ask about price trends or negotiation tips..."
                className="w-full bg-slate-950 border border-slate-800 rounded-[1.5rem] pl-8 pr-20 py-5 text-white placeholder-slate-600 outline-none focus:ring-2 focus:ring-emerald-500 transition-all shadow-inner"
              />
              <div className="absolute right-4 top-1/2 -translate-y-1/2">
                <VoiceControls 
                  enableMic 
                  lang={language} 
                  onMicResult={(text) => setAssistantQuery(text)}
                  autoSend
                  onSend={handleAskAssistant}
                />
              </div>
            </div>
            <button 
              onClick={handleAskAssistant}
              disabled={isAssistantLoading || !assistantQuery.trim()}
              className="bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-800 px-10 py-5 rounded-[1.5rem] font-bold text-white transition-all shadow-xl shadow-emerald-950/40 flex items-center justify-center gap-3 active:scale-[0.98]"
            >
              {isAssistantLoading ? <Loader2 className="w-6 h-6 animate-spin" /> : <MessageSquare className="w-6 h-6" />}
              Generate Advisory
            </button>
          </div>

          {assistantResponse && (
            <div className="animate-in slide-in-from-top-6 duration-500 p-10 bg-slate-900/80 rounded-[2.5rem] border border-emerald-500/20 shadow-2xl space-y-6">
              <div className="flex justify-between items-center">
                <div className="flex items-center gap-3">
                   <Bot className="w-6 h-6 text-emerald-500" />
                   <h4 className="text-emerald-400 font-bold text-sm uppercase tracking-[0.2em]">KissanSeva Analyst Perspective</h4>
                </div>
                <VoiceControls enableSpeaker textToSpeak={assistantResponse} lang={language} size="lg" />
              </div>
              <div className="prose prose-invert max-w-none text-slate-300 leading-relaxed text-lg whitespace-pre-wrap font-medium">
                {assistantResponse}
              </div>
              <div className="pt-4 flex gap-4">
                 <div className="flex items-center gap-2 bg-slate-950 px-4 py-2 rounded-xl border border-slate-800">
                    <span className="w-2 h-2 bg-emerald-500 rounded-full"></span>
                    <span className="text-[10px] font-bold uppercase tracking-widest text-slate-500">Live Trend Applied</span>
                 </div>
              </div>
            </div>
          )}
        </div>
      </div>

      {/* Main Directory Search & Filter */}
      <div className="glass p-8 rounded-[3rem] border border-slate-800 flex flex-col lg:flex-row gap-6 items-center shadow-2xl bg-slate-900/30">
        <div className="relative group flex-1 w-full">
          <Search className="absolute left-6 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500 group-focus-within:text-emerald-500 transition-colors" />
          <input 
            type="text" 
            placeholder={language === 'hi' ? 'नाम या फसल के आधार पर खोजें...' : 'Search for specific buyers or crop agents...'}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full bg-slate-950 border border-slate-800 rounded-[1.5rem] pl-16 pr-16 py-5 text-white placeholder-slate-600 focus:ring-2 focus:ring-emerald-500 outline-none transition-all shadow-inner"
          />
          <div className="absolute right-6 top-1/2 -translate-y-1/2">
            <VoiceControls enableMic lang={language} onMicResult={setSearch} />
          </div>
        </div>

        <div className="relative group w-full lg:w-80">
          <Filter className="absolute left-6 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500 group-focus-within:text-emerald-500 transition-colors z-10" />
          <select
            value={selectedLocation}
            onChange={(e) => setSelectedLocation(e.target.value)}
            className="w-full bg-slate-950 border border-slate-800 rounded-[1.5rem] pl-16 pr-10 py-5 text-white focus:ring-2 focus:ring-emerald-500 outline-none transition-all appearance-none cursor-pointer relative font-bold"
          >
            <option value="">{language === 'hi' ? 'सभी स्थान' : 'All Regions'}</option>
            {locations.map(loc => (
              <option key={loc} value={loc} className="bg-slate-900 text-white font-bold">{loc}</option>
            ))}
          </select>
          <div className="absolute inset-y-0 right-6 flex items-center pointer-events-none">
            <ChevronDown className="w-6 h-6 text-slate-500" />
          </div>
        </div>
      </div>

      {/* Grid of Trader Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
        {filteredTraders.map((trader) => (
          <div key={trader.id} className="glass p-10 rounded-[2.5rem] border border-slate-800 hover:border-emerald-500/30 transition-all duration-500 flex flex-col group relative overflow-hidden shadow-xl bg-slate-900/40">
            <div className="absolute -top-32 -right-32 w-64 h-64 bg-emerald-500/5 blur-[100px] group-hover:bg-emerald-500/10 transition-all duration-700"></div>
            
            <div className="flex items-start gap-6 mb-8">
              <div className="w-20 h-20 bg-slate-950 rounded-[1.75rem] flex items-center justify-center border border-slate-800 group-hover:border-emerald-500/50 transition-all shadow-inner overflow-hidden flex-shrink-0">
                {trader.imageUrl ? (
                  <img src={trader.imageUrl} alt={trader.name} className="w-full h-full object-cover" />
                ) : (
                  <Building2 className="w-10 h-10 text-emerald-500" />
                )}
              </div>
              <div className="space-y-1 pt-2">
                <h3 className="text-2xl font-bold text-white group-hover:text-emerald-400 transition-colors leading-tight tracking-tight">
                  {trader.name}
                </h3>
                <div className="flex items-center gap-3">
                  <div className="flex items-center gap-1.5 bg-amber-500/10 px-3 py-1 rounded-lg text-amber-500 text-xs font-black border border-amber-500/20 shadow-sm">
                    <Star className="w-3 h-3 fill-current" />
                    {trader.rating}
                  </div>
                  <span className="text-slate-500 text-[10px] font-black uppercase tracking-widest">
                    {trader.reviewCount} Reports
                  </span>
                </div>
              </div>
            </div>

            <div className="space-y-6 mb-10 flex-1 relative z-10">
              <div className="flex items-center gap-3 text-slate-400 text-sm font-bold bg-slate-950/50 p-3 rounded-xl border border-slate-800/50">
                <MapPin className="w-5 h-5 text-emerald-500" /> 
                {trader.location}
              </div>

              <div className="p-6 bg-slate-950/80 border border-slate-800/80 rounded-3xl flex items-center justify-between group/phone transition-all hover:border-emerald-500/30 shadow-inner">
                <div className="flex flex-col">
                  <span className="text-[10px] font-black text-slate-600 uppercase tracking-[0.2em] mb-1">Trader ID: #0{trader.id}</span>
                  <span className="text-xl font-black text-white tracking-tighter font-mono">
                    {trader.phone}
                  </span>
                </div>
                <a 
                  href={`tel:${trader.phone?.replace(/\s+/g, '')}`}
                  className="w-12 h-12 bg-emerald-600 hover:bg-emerald-500 text-white rounded-2xl transition-all shadow-lg flex items-center justify-center active:scale-90"
                >
                  <Phone className="w-6 h-6" />
                </a>
              </div>

              <div>
                <p className="text-[10px] font-black text-slate-600 uppercase tracking-[0.2em] mb-4 flex items-center gap-2">
                  <Check className="w-3 h-3 text-emerald-500" /> Commodities Traded
                </p>
                <div className="flex flex-wrap gap-2">
                  {trader.dealsIn?.map((crop, idx) => (
                    <span key={idx} className="bg-emerald-500/5 border border-emerald-500/10 text-emerald-400 text-[10px] font-black px-4 py-2 rounded-xl uppercase tracking-wider">
                      {crop}
                    </span>
                  ))}
                </div>
              </div>
            </div>

            <div className="flex flex-col gap-3 mt-auto relative z-10">
              <button className="w-full py-5 bg-emerald-600/10 hover:bg-emerald-600 text-emerald-400 hover:text-white font-black rounded-[1.5rem] transition-all border border-emerald-500/20 flex items-center justify-center gap-3 text-sm shadow-xl active:scale-[0.98]">
                <MessageSquare className="w-5 h-5" /> Negotiate Trade
              </button>
              <button 
                onClick={() => handleOpenRatingModal(trader)}
                className="w-full py-3 text-[10px] font-black uppercase tracking-[0.3em] text-slate-500 hover:text-amber-500 transition-colors flex items-center justify-center gap-2"
              >
                <Star className="w-4 h-4" /> Verify Credentials
              </button>
            </div>
          </div>
        ))}
      </div>

      {/* Rating Modal */}
      {ratingModal.isOpen && (
        <div className="fixed inset-0 bg-black/70 backdrop-blur-sm flex items-center justify-center z-50 animate-in fade-in duration-300 p-4">
          <div className="glass p-10 rounded-[2.5rem] border border-slate-800 w-full max-w-md space-y-8 animate-in zoom-in-95 duration-300 shadow-2xl">
            <div className="flex items-center justify-between">
              <div className="space-y-1">
                <h3 className="text-xl font-bold text-white tracking-tight">Rate Trader</h3>
                <p className="text-sm text-slate-400">{ratingModal.traderName}</p>
              </div>
              <button 
                onClick={() => setRatingModal({ isOpen: false, traderId: null, traderName: '' })}
                className="p-2 text-slate-400 hover:text-white bg-slate-800 rounded-xl transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>
            
            <div className="flex justify-center gap-3 py-4">
              {[1, 2, 3, 4, 5].map((star) => (
                <button 
                  key={star}
                  onClick={() => setUserRating(star)}
                  className="group transition-transform hover:scale-110 active:scale-90"
                >
                  <Star className={`w-10 h-10 transition-all ${
                    star <= userRating 
                      ? 'text-amber-400 fill-amber-400 drop-shadow-[0_0_8px_rgba(251,191,36,0.4)]' 
                      : 'text-slate-700 group-hover:text-slate-500'
                  }`} />
                </button>
              ))}
            </div>

            {userRating > 0 && (
              <p className="text-center text-sm font-bold text-amber-400 animate-in fade-in">
                {userRating === 5 ? 'Excellent!' : userRating >= 4 ? 'Great!' : userRating >= 3 ? 'Good' : userRating >= 2 ? 'Fair' : 'Poor'}
              </p>
            )}

            <button
              onClick={handleSubmitRating}
              disabled={userRating === 0 || isSubmitting}
              className="w-full py-4 bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-800 disabled:text-slate-500 text-white font-bold rounded-2xl transition-all flex items-center justify-center gap-2 shadow-xl active:scale-[0.98]"
            >
              {isSubmitting ? (
                <><span className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin" /> Submitting...</>
              ) : (
                <><Check className="w-5 h-5" /> Submit Rating</>
              )}
            </button>
          </div>
        </div>
      )}
    </div>
  );
};

export default TradersDirectory;
