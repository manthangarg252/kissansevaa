import React, { useState, useMemo, useRef, useEffect } from 'react';
import { 
  Dog, 
  MessageSquare, 
  Upload, 
  CheckCircle2, 
  Loader2, 
  Info, 
  AlertCircle, 
  HeartPulse, 
  ShieldCheck, 
  Stethoscope, 
  Droplets,
  Mic,
  MicOff,
  Volume2,
  Square,
  Send,
  User as UserIcon,
  Bot,
  Sparkles
} from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { getGeminiChatResponse, analyzeLivestockDisease } from '../geminiService';
import { fileToBase64, fileToDataUrl } from '../utils';
import VoiceControls from '../components/VoiceControls.tsx';

interface Message {
  id: number;
  role: 'user' | 'ai';
  text: string;
}

const Livestock: React.FC = () => {
  const { t, i18n } = useTranslation();
  const [activeTab, setActiveTab] = useState<'detection' | 'assistant'>('detection');
  
  // Detection State
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [imageBase64, setImageBase64] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [diagResult, setDiagResult] = useState<any>(null);
  const [animalType, setAnimalType] = useState('Cattle');

  // Assistant Chat State
  const [chatHistory, setChatHistory] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [chatLoading, setChatLoading] = useState(false);
  
  const chatScrollRef = useRef<HTMLDivElement>(null);

  // Auto-scroll chat
  useEffect(() => {
    if (chatScrollRef.current) {
      chatScrollRef.current.scrollTop = chatScrollRef.current.scrollHeight;
    }
  }, [chatHistory, chatLoading]);

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      try {
        const [base64, dataUrl] = await Promise.all([
          fileToBase64(file),
          fileToDataUrl(file)
        ]);
        setImageBase64(base64);
        setImagePreview(dataUrl);
      } catch (err) {
        console.error("Failed to process image", err);
      }
    }
  };

  const handleDiagnose = async () => {
    if (!imageBase64) return;
    setLoading(true);
    try {
      const res = await analyzeLivestockDisease(imageBase64, animalType, i18n.language as any);
      setDiagResult(res);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleChat = async (e?: React.FormEvent, voiceText?: string) => {
    if (e) e.preventDefault();
    const messageToSend = voiceText || input;
    if (!messageToSend.trim() || chatLoading) return;

    const userMsg: Message = { id: Date.now(), role: 'user', text: messageToSend };
    setInput('');
    setChatHistory(prev => [...prev, userMsg]);
    setChatLoading(true);

    try {
      const aiRes = await getGeminiChatResponse(messageToSend, 'Livestock Assistant', i18n.language as any);
      const aiMsg: Message = { id: Date.now() + 1, role: 'ai', text: aiRes || '' };
      setChatHistory(prev => [...prev, aiMsg]);
    } finally {
      setChatLoading(false);
    }
  };

  // Construct the narrative for TTS in detection tab
  const speechText = useMemo(() => {
    if (!diagResult) return "";
    const conditionLabel = diagResult.conditionName;
    const matchLabel = `${t('common.match')}: ${diagResult.confidence}`;
    let text = `${t('livestock.diag_result')}. ${t('livestock.health_check')}: ${conditionLabel}. ${matchLabel}. ${diagResult.summary || ""}. `;
    if (diagResult.symptoms?.length > 0) text += `${t('livestock.detection_tab')}: ${diagResult.symptoms.join('. ')}. `;
    if (diagResult.treatment?.length > 0) text += `${t('livestock.diag_result')} ${t('livestock.run_diagnostics')}: ${diagResult.treatment.join('. ')}. `;
    if (diagResult.prevention?.length > 0) text += `${t('crop.prevention')}: ${diagResult.prevention.join('. ')}. `;
    if (diagResult.vetAdvice) text += `${t('livestock.vet_advice')}: ${diagResult.vetAdvice}. `;
    return text;
  }, [diagResult, t]);

  return (
    <div className="space-y-8 animate-in fade-in duration-500 pb-20">
      <div className="flex bg-slate-900 p-1 rounded-[1.5rem] border border-slate-800 w-fit shadow-xl">
        <button 
          onClick={() => setActiveTab('detection')}
          className={`px-8 py-4 rounded-2xl font-bold flex items-center gap-3 transition-all ${activeTab === 'detection' ? 'bg-emerald-600 text-white shadow-lg' : 'text-slate-500 hover:text-white'}`}
        >
          <Dog className="w-5 h-5" /> {t('livestock.detection_tab')}
        </button>
        <button 
          onClick={() => setActiveTab('assistant')}
          className={`px-8 py-4 rounded-2xl font-bold flex items-center gap-3 transition-all ${activeTab === 'assistant' ? 'bg-emerald-600 text-white shadow-lg' : 'text-slate-500 hover:text-white'}`}
        >
          <MessageSquare className="w-5 h-5" /> {t('livestock.assistant_tab')}
        </button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-10">
        {activeTab === 'detection' ? (
          <>
            <div className="glass p-10 rounded-[2.5rem] border border-slate-800 space-y-8 shadow-2xl bg-slate-900/40">
              <h2 className="text-3xl font-extrabold text-white flex items-center gap-4 tracking-tight">
                <HeartPulse className="text-emerald-500 w-8 h-8" /> {t('livestock.health_check')}
              </h2>
              <p className="text-slate-400 leading-relaxed font-medium">Capture a clear photo of the animal's eyes, skin, or mouth for an instant AI health diagnostic.</p>
              
              <div className="space-y-6">
                <div>
                  <label className="block text-[10px] font-bold text-slate-500 uppercase tracking-widest mb-3 ml-1">{t('livestock.category')}</label>
                  <select 
                    value={animalType}
                    onChange={(e) => setAnimalType(e.target.value)}
                    className="w-full bg-slate-950 border border-slate-800 rounded-2xl px-5 py-4 text-white focus:ring-2 focus:ring-emerald-500 outline-none transition-all cursor-pointer font-bold"
                  >
                    <option>Cattle</option>
                    <option>Poultry</option>
                    <option>Sheep/Goat</option>
                    <option>Pig</option>
                  </select>
                </div>

                <div className={`relative h-72 border-2 border-dashed rounded-[2rem] flex flex-col items-center justify-center transition-all overflow-hidden ${imagePreview ? 'border-emerald-500/50 bg-emerald-500/5' : 'border-slate-800 bg-slate-950/50 hover:border-slate-700'}`}>
                  {imagePreview ? <img src={imagePreview} className="w-full h-full object-cover" /> : <div className="flex flex-col items-center gap-4"><Upload className="w-16 h-16 text-slate-700" /><p className="text-slate-500 font-bold uppercase tracking-widest text-xs">Tap to upload image</p></div>}
                  <input type="file" onChange={handleImageUpload} className="absolute inset-0 opacity-0 cursor-pointer" />
                </div>

                <button 
                  onClick={handleDiagnose}
                  disabled={loading || !imageBase64}
                  className="w-full bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-800 text-white font-bold py-5 rounded-2xl flex items-center justify-center gap-3 transition-all shadow-xl shadow-emerald-900/20 active:scale-[0.98]"
                >
                  {loading ? <Loader2 className="w-6 h-6 animate-spin" /> : <CheckCircle2 className="w-6 h-6" />}
                  {t('livestock.run_diagnostics')}
                </button>
              </div>
            </div>

            <div className="space-y-6">
              {diagResult ? (
                <div className="glass p-10 rounded-[2.5rem] border border-slate-800 space-y-8 animate-in slide-in-from-right-8 shadow-2xl bg-slate-900/50">
                  <div className="flex items-center justify-between">
                    <div className="flex items-center gap-4">
                      <div className="p-3 bg-emerald-500/10 rounded-2xl">
                        <Stethoscope className="w-8 h-8 text-emerald-500" />
                      </div>
                      <h3 className="text-2xl font-bold text-white tracking-tight">Diagnostics Result</h3>
                    </div>
                    <div className="flex items-center gap-3">
                      <VoiceControls enableSpeaker textToSpeak={speechText} lang={i18n.language as any} size="lg" />
                      <span className="text-emerald-400 text-[10px] font-black uppercase tracking-widest border border-emerald-500/20 px-4 py-1.5 rounded-full bg-emerald-500/10">
                        {diagResult.confidence} Match
                      </span>
                    </div>
                  </div>

                  <div className="p-8 bg-slate-950/80 border border-slate-800 rounded-[2rem] space-y-4 shadow-inner">
                    <div className="space-y-2">
                      <h4 className="text-3xl font-black text-emerald-400 tracking-tight leading-tight">{diagResult.conditionName}</h4>
                      <p className="text-slate-300 text-lg leading-relaxed italic font-medium">
                        {diagResult.summary}
                      </p>
                    </div>

                    {diagResult.symptoms?.length > 0 && (
                      <div className="pt-6 border-t border-slate-800/50">
                        <p className="text-[10px] text-slate-500 font-black uppercase tracking-[0.2em] mb-4">Diagnostic Indicators</p>
                        <div className="flex flex-wrap gap-2">
                          {diagResult.symptoms.map((s: string, idx: number) => (
                            <span key={idx} className="bg-slate-900 text-slate-400 text-[10px] font-black px-4 py-2 rounded-xl border border-slate-800 uppercase">
                              {s}
                            </span>
                          ))}
                        </div>
                      </div>
                    )}
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                    <div className="p-6 bg-emerald-500/5 border border-emerald-500/10 rounded-[1.75rem] shadow-lg">
                      <h5 className="text-emerald-400 font-black text-[10px] mb-4 flex items-center gap-2 uppercase tracking-[0.2em]">
                        <HeartPulse className="w-4 h-4" /> Treatment Regimen
                      </h5>
                      <ul className="space-y-3">
                        {diagResult.treatment?.map((tr: string, idx: number) => (
                          <li key={idx} className="text-slate-400 text-xs font-medium flex gap-3">
                            <span className="text-emerald-500">•</span> {tr}
                          </li>
                        ))}
                      </ul>
                    </div>
                    <div className="p-6 bg-blue-500/5 border border-blue-500/10 rounded-[1.75rem] shadow-lg">
                      <h5 className="text-blue-400 font-black text-[10px] mb-4 flex items-center gap-2 uppercase tracking-[0.2em]">
                        <Droplets className="w-4 h-4" /> Biosafety Steps
                      </h5>
                      <ul className="space-y-3">
                        {diagResult.prevention?.map((pr: string, idx: number) => (
                          <li key={idx} className="text-slate-400 text-xs font-medium flex gap-3">
                            <span className="text-blue-500">•</span> {pr}
                          </li>
                        ))}
                      </ul>
                    </div>
                  </div>

                  <div className="p-8 bg-amber-500/10 border-l-4 border-amber-500 rounded-r-[1.5rem] shadow-lg">
                    <p className="text-amber-500 text-[10px] font-black mb-2 flex items-center gap-2 uppercase tracking-[0.2em]">
                      <AlertCircle className="w-4 h-4" /> Veterinary Mandate
                    </p>
                    <p className="text-slate-200 text-sm italic leading-relaxed font-bold">{diagResult.vetAdvice || diagResult.vetConsultationAdvice}</p>
                  </div>
                </div>
              ) : (
                <div className="h-full flex flex-col items-center justify-center text-center p-20 glass rounded-[2.5rem] border border-dashed border-slate-800 opacity-20 bg-slate-900/10">
                  <div className="w-24 h-24 bg-slate-950 rounded-full flex items-center justify-center mb-8 border border-slate-800 shadow-inner">
                    <Info className="w-12 h-12 text-slate-700" />
                  </div>
                  <h3 className="text-2xl font-bold text-slate-500 tracking-tight">System Ready</h3>
                  <p className="text-slate-600 mt-4 max-w-xs text-lg font-medium">Awaiting visual input for multispectral condition analysis.</p>
                </div>
              )}
            </div>
          </>
        ) : (
          <div className="lg:col-span-2 glass rounded-[3rem] border border-slate-800 flex flex-col h-[700px] overflow-hidden shadow-2xl bg-slate-950/20">
            {/* Chat Header */}
            <div className="px-8 py-6 bg-slate-900/80 border-b border-slate-800 flex items-center justify-between">
              <div className="flex items-center gap-4">
                <div className="w-14 h-14 bg-emerald-500/20 rounded-2xl flex items-center justify-center relative shadow-lg">
                  <Bot className="w-8 h-8 text-emerald-500" />
                  <div className="absolute -bottom-1 -right-1 w-4 h-4 bg-emerald-500 border-4 border-slate-900 rounded-full"></div>
                </div>
                <div>
                  <h3 className="text-xl font-bold text-white tracking-tight">VetExpert AI Assistant</h3>
                  <div className="flex items-center gap-2">
                     <span className="w-2 h-2 bg-emerald-500 rounded-full animate-pulse"></span>
                     <span className="text-[10px] font-bold text-emerald-500 uppercase tracking-widest">Active Consultation</span>
                  </div>
                </div>
              </div>
              <div className="flex items-center gap-3">
                 <div className="p-2 bg-slate-800 rounded-xl text-slate-500 text-[10px] font-black uppercase tracking-widest border border-slate-700">
                    Multilingual Console
                 </div>
              </div>
            </div>

            {/* Chat Messages */}
            <div ref={chatScrollRef} className="flex-1 overflow-y-auto p-8 space-y-8 no-scrollbar bg-slate-950/40">
              {chatHistory.length === 0 && (
                <div className="h-full flex flex-col items-center justify-center text-center opacity-30 px-10">
                  <div className="p-6 bg-slate-900 rounded-3xl mb-6 shadow-inner border border-slate-800">
                    <MessageSquare className="w-16 h-16 text-slate-600" />
                  </div>
                  <h3 className="text-2xl font-bold text-white mb-2 tracking-tight">Voice-Activated Assistance</h3>
                  <p className="text-lg text-slate-400 max-w-md font-medium leading-relaxed">Describe symptoms in your native language or ask about specific vaccination schedules and feed formulations.</p>
                </div>
              )}
              {chatHistory.map((msg) => (
                <div key={msg.id} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'} animate-in slide-in-from-bottom-4 duration-300`}>
                  <div className={`max-w-[80%] flex gap-4 ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}>
                    <div className={`w-10 h-10 shrink-0 rounded-2xl flex items-center justify-center shadow-lg ${msg.role === 'user' ? 'bg-blue-600 shadow-blue-900/20' : 'bg-emerald-600 shadow-emerald-900/20'}`}>
                      {msg.role === 'user' ? <UserIcon className="w-5 h-5 text-white" /> : <Bot className="w-5 h-5 text-white" />}
                    </div>
                    <div className={`p-6 rounded-3xl relative shadow-xl ${msg.role === 'user' ? 'bg-blue-600/90 text-white rounded-tr-none' : 'bg-slate-900 border border-slate-800 text-slate-100 rounded-tl-none font-medium'}`}>
                      <p className="text-base whitespace-pre-wrap leading-relaxed">{msg.text}</p>
                      
                      {msg.role === 'ai' && (
                        <div className="mt-4 flex justify-end border-t border-slate-800/50 pt-4">
                          <VoiceControls enableSpeaker textToSpeak={msg.text} lang={i18n.language as any} size="sm" />
                        </div>
                      )}
                    </div>
                  </div>
                </div>
              ))}
              {chatLoading && (
                <div className="flex justify-start">
                   <div className="bg-slate-900 border border-slate-800 p-6 rounded-3xl rounded-tl-none shadow-xl flex items-center gap-4">
                      <div className="flex gap-1.5">
                         <div className="w-2 h-2 bg-emerald-500 rounded-full animate-bounce delay-0"></div>
                         <div className="w-2 h-2 bg-emerald-500 rounded-full animate-bounce delay-150"></div>
                         <div className="w-2 h-2 bg-emerald-500 rounded-full animate-bounce delay-300"></div>
                      </div>
                      <span className="text-[10px] font-black uppercase text-slate-500 tracking-widest">Processing Query</span>
                   </div>
                </div>
              )}
            </div>

            {/* Chat Input */}
            <form onSubmit={handleChat} className="p-8 bg-slate-900/80 border-t border-slate-800">
              <div className="relative flex items-center gap-4">
                <div className="flex-1 relative group">
                  <input 
                    type="text" 
                    value={input}
                    onChange={(e) => setInput(e.target.value)}
                    placeholder={t('livestock.chat_hint')}
                    className="w-full bg-slate-950 border border-slate-800 rounded-[1.5rem] pl-8 pr-16 py-5 text-white placeholder-slate-600 outline-none focus:ring-2 focus:ring-emerald-500 transition-all shadow-inner font-medium"
                  />
                  <div className="absolute right-4 top-1/2 -translate-y-1/2">
                    <VoiceControls 
                      enableMic 
                      lang={i18n.language as any} 
                      onMicResult={(text) => setInput(text)}
                      autoSend
                      onSend={() => handleChat()}
                      size="md"
                    />
                  </div>
                </div>
                
                <button 
                  type="submit" 
                  disabled={chatLoading || !input.trim()} 
                  className="bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-800 w-16 h-16 flex items-center justify-center rounded-2xl transition-all shadow-xl shadow-emerald-950/40 active:scale-90 flex-shrink-0"
                  title="Send Console"
                >
                  <Send className="w-7 h-7 text-white" />
                </button>
              </div>
              <div className="mt-4 flex justify-center">
                 <p className="text-[10px] text-slate-500 uppercase tracking-[0.3em] font-black flex items-center gap-2">
                    <Sparkles className="w-3 h-3 text-emerald-500" /> Multilingual Voice Matrix Enabled
                 </p>
              </div>
            </form>
          </div>
        )}
      </div>
    </div>
  );
};

export default Livestock;
