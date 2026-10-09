
import React, { useState, useRef, useEffect, useCallback } from 'react';
import { Send, User, Bot, Loader2, Mic, Volume2, VolumeX, Square, CircleStop } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { getGeminiChatResponse } from '../geminiService';

// Locale mapping for Speech APIs
const localeMap: Record<string, string> = {
  en: 'en-IN',
  hi: 'hi-IN',
  sa: 'hi-IN', 
  pa: 'pa-IN',
  mr: 'mr-IN'
};

const Chatbot: React.FC = () => {
  const { t, i18n } = useTranslation();
  const [messages, setMessages] = useState<{ role: 'user' | 'ai', text: string, mode: string, id: number }[]>([]);
  const [input, setInput] = useState('');
  const [loading, setLoading] = useState(false);
  const [mode, setMode] = useState('General Assistant');
  const [isListening, setIsListening] = useState(false);
  const [autoSpeak, setAutoSpeak] = useState(false);
  const [speakingMsgId, setSpeakingMsgId] = useState<number | null>(null);
  const scrollRef = useRef<HTMLDivElement>(null);
  
  const recognitionRef = useRef<any>(null);

  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  useEffect(() => {
    const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
    if (SpeechRecognition) {
      recognitionRef.current = new SpeechRecognition();
      recognitionRef.current.continuous = false;
      recognitionRef.current.interimResults = false;

      recognitionRef.current.onresult = (event: any) => {
        const transcript = event.results[0][0].transcript;
        setInput(prev => prev + transcript);
        setIsListening(false);
      };

      recognitionRef.current.onerror = (event: any) => {
        console.error('Speech recognition error:', event.error);
        setIsListening(false);
      };

      recognitionRef.current.onend = () => {
        setIsListening(false);
      };
    }
  }, []);

  useEffect(() => {
    if (recognitionRef.current) {
      recognitionRef.current.lang = localeMap[i18n.language] || 'en-IN';
    }
  }, [i18n.language]);

  const stopSpeaking = useCallback(() => {
    window.speechSynthesis.cancel();
    setSpeakingMsgId(null);
  }, []);

  const speakText = useCallback((text: string, msgId: number) => {
    stopSpeaking();
    const utterance = new SpeechSynthesisUtterance(text);
    utterance.lang = localeMap[i18n.language] || 'en-IN';
    utterance.rate = 1;
    
    utterance.onstart = () => setSpeakingMsgId(msgId);
    utterance.onend = () => setSpeakingMsgId(null);
    utterance.onerror = () => setSpeakingMsgId(null);

    window.speechSynthesis.speak(utterance);
  }, [i18n.language, stopSpeaking]);

  const handleSend = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    if (!input.trim() || loading) return;

    const userMsg = input;
    const msgId = Date.now();
    setInput('');
    setMessages(prev => [...prev, { role: 'user', text: userMsg, mode, id: msgId }]);
    setLoading(true);

    try {
      const aiRes = await getGeminiChatResponse(userMsg, mode, i18n.language as any);
      const aiText = aiRes || '';
      const aiMsgId = Date.now() + 1;
      setMessages(prev => [...prev, { role: 'ai', text: aiText, mode, id: aiMsgId }]);
      
      if (autoSpeak) {
        speakText(aiText, aiMsgId);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const toggleListening = () => {
    if (!recognitionRef.current) {
      alert("Speech Recognition not supported in this browser.");
      return;
    }

    if (isListening) {
      recognitionRef.current.stop();
    } else {
      recognitionRef.current.start();
      setIsListening(true);
    }
  };

  const modes = [
    { name: 'Crop Assistant 🌾', id: 'Crop' },
    { name: 'Livestock Assistant 🐄', id: 'Livestock' },
    { name: 'Government Assistant 🏛️', id: 'Gov' },
    { name: 'Market Assistant 📈', id: 'Market' },
    { name: 'Carbon Credits Assistant 🌱', id: 'Carbon' }
  ];

  return (
    <div className="max-w-4xl mx-auto h-[calc(100vh-12rem)] flex flex-col glass rounded-3xl border border-slate-800 overflow-hidden shadow-2xl animate-in zoom-in duration-300">
      {/* Header */}
      <div className="px-6 py-4 bg-slate-900/50 border-b border-slate-800 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="w-10 h-10 bg-emerald-500/20 rounded-full flex items-center justify-center relative">
            <Bot className="w-6 h-6 text-emerald-500" />
            {(isListening || speakingMsgId !== null) && (
              <span className={`absolute inset-0 rounded-full ${isListening ? 'bg-blue-500' : 'bg-emerald-500'} animate-ping opacity-25`}></span>
            )}
          </div>
          <div>
            <h2 className="font-bold text-white">KissanSevaAI Bot</h2>
            <div className="flex items-center gap-2">
              <span className={`text-[10px] font-bold uppercase tracking-wider ${isListening ? 'text-blue-400' : speakingMsgId !== null ? 'text-amber-400' : 'text-emerald-400'}`}>
                {isListening ? 'Listening...' : speakingMsgId !== null ? 'Speaking...' : 'Online & Thinking'}
              </span>
            </div>
          </div>
        </div>
        
        <div className="flex items-center gap-3">
          {speakingMsgId !== null && (
            <button onClick={stopSpeaking} className="p-2 bg-red-500/10 text-red-500 rounded-lg border border-red-500/20 hover:bg-red-500/20 flex items-center gap-2 text-[10px] font-bold uppercase">
              <CircleStop className="w-4 h-4" /> Stop
            </button>
          )}

          <button 
            onClick={() => setAutoSpeak(!autoSpeak)}
            className={`flex items-center gap-2 px-3 py-1.5 rounded-lg border transition-all text-[10px] font-bold uppercase tracking-wider ${autoSpeak ? 'bg-emerald-600/20 border-emerald-500 text-emerald-400' : 'bg-slate-800 border-slate-700 text-slate-400 hover:text-white'}`}
          >
            {autoSpeak ? <Volume2 className="w-3.5 h-3.5" /> : <VolumeX className="w-3.5 h-3.5" />}
            Auto Speak {autoSpeak ? 'ON' : 'OFF'}
          </button>
          
          <select 
            value={mode}
            onChange={(e) => setMode(e.target.value)}
            className="bg-slate-800 border border-slate-700 rounded-lg px-3 py-1.5 text-xs font-bold text-white outline-none focus:ring-2 focus:ring-emerald-500"
          >
            {modes.map(m => <option key={m.id} value={m.name}>{m.name}</option>)}
          </select>
        </div>
      </div>

      {/* Chat Area */}
      <div ref={scrollRef} className="flex-1 overflow-y-auto p-6 space-y-6 no-scrollbar bg-slate-950/20">
        {messages.length === 0 && (
          <div className="h-full flex flex-col items-center justify-center text-center opacity-40">
            <Bot className="w-16 h-16 text-slate-700 mb-4" />
            <h3 className="text-xl font-bold text-slate-400">Welcome to KissanSevaAI Assistant</h3>
            <p className="text-slate-500 max-w-xs mt-2 text-sm">Select a specialized mode above or just start chatting/speaking in your language.</p>
          </div>
        )}
        {messages.map((msg) => (
          <div key={msg.id} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'} animate-in slide-in-from-bottom-2`}>
            <div className={`max-w-[85%] flex gap-3 ${msg.role === 'user' ? 'flex-row-reverse' : ''}`}>
              <div className={`w-8 h-8 shrink-0 rounded-full flex items-center justify-center ${msg.role === 'user' ? 'bg-blue-600' : 'bg-emerald-600'}`}>
                {msg.role === 'user' ? <User className="w-4 h-4 text-white" /> : <Bot className="w-4 h-4 text-white" />}
              </div>
              <div className={`p-4 rounded-2xl shadow-sm relative group ${msg.role === 'user' ? 'bg-blue-600 text-white rounded-tr-none' : 'bg-slate-900 border border-slate-800 text-slate-200 rounded-tl-none'} ${speakingMsgId === msg.id ? 'ring-2 ring-emerald-500' : ''}`}>
                <p className="text-sm whitespace-pre-wrap leading-relaxed">{msg.text}</p>
                <div className="flex items-center justify-between mt-3">
                  <div className={`text-[10px] font-bold uppercase tracking-widest opacity-40 ${msg.role === 'user' ? 'text-white' : 'text-emerald-400'}`}>
                    {msg.mode}
                  </div>
                  {msg.role === 'ai' && (
                    <button onClick={() => speakingMsgId === msg.id ? stopSpeaking() : speakText(msg.text, msg.id)} className={`p-1.5 rounded-md transition-all flex items-center gap-1.5 ${speakingMsgId === msg.id ? 'bg-emerald-600 text-white' : 'bg-slate-800 text-slate-400 hover:text-emerald-400'}`}>
                      {speakingMsgId === msg.id ? <Square className="w-3 h-3 fill-current" /> : <Volume2 className="w-3.5 h-3.5" />}
                    </button>
                  )}
                </div>
              </div>
            </div>
          </div>
        ))}
        {loading && <div className="flex justify-start"><Loader2 className="w-4 h-4 animate-spin text-emerald-500" /></div>}
      </div>

      {/* Input Area */}
      <form onSubmit={handleSend} className="p-6 bg-slate-900/50 border-t border-slate-800 flex gap-4">
        <input 
          type="text" 
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={isListening ? "Listening..." : "Type your query here..."}
          className={`flex-1 bg-slate-950 border rounded-2xl px-6 py-4 text-white outline-none focus:ring-2 focus:ring-emerald-500 transition-all ${isListening ? 'border-blue-500' : 'border-slate-800'}`}
        />
        <div className="flex gap-2">
          <button type="button" onClick={toggleListening} className={`w-14 h-14 flex items-center justify-center rounded-2xl transition-all ${isListening ? 'bg-blue-600 shadow-lg' : 'bg-slate-800 hover:bg-slate-700'}`}>
            <Mic className={`w-6 h-6 ${isListening ? 'text-white' : 'text-slate-400'}`} />
          </button>
          <button type="submit" disabled={loading || !input.trim()} className="bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-800 w-14 h-14 flex items-center justify-center rounded-2xl transition-all shadow-lg">
            <Send className="w-6 h-6 text-white" />
          </button>
        </div>
      </form>
    </div>
  );
};

export default Chatbot;
