
import React, { useState, useEffect, useCallback, useRef } from 'react';
import { Volume2, CircleStop, Loader2 } from 'lucide-react';

interface TTSButtonProps {
  text: string;
  lang: string;
  className?: string;
  disabled?: boolean;
}

// Map app language codes to Speech API locales
const localeMap: Record<string, string> = {
  en: 'en-IN',
  hi: 'hi-IN',
  mr: 'mr-IN',
  pa: 'hi-IN', // Punjabi often falls back well to hi-IN if pa-IN isn't present
  sa: 'hi-IN'  // Sanskrit is best read by Hindi phonemes in standard TTS
};

const TTSButton: React.FC<TTSButtonProps> = ({ text, lang, className = '', disabled = false }) => {
  const [isSpeaking, setIsSpeaking] = useState(false);
  const [isSupported, setIsSupported] = useState(true);
  const utteranceRef = useRef<SpeechSynthesisUtterance | null>(null);

  useEffect(() => {
    if (!('speechSynthesis' in window)) {
      setIsSupported(false);
    }

    return () => {
      window.speechSynthesis.cancel();
    };
  }, []);

  const handleToggleSpeech = useCallback(() => {
    if (isSpeaking) {
      window.speechSynthesis.cancel();
      setIsSpeaking(false);
      return;
    }

    // Cancel any existing speech
    window.speechSynthesis.cancel();

    const utterance = new SpeechSynthesisUtterance(text);
    
    // Set language and find best voice
    const targetLocale = localeMap[lang] || 'en-IN';
    utterance.lang = targetLocale;
    
    // Optional: Try to find an Indian voice explicitly
    const voices = window.speechSynthesis.getVoices();
    const voice = voices.find(v => v.lang === targetLocale) || voices.find(v => v.lang.startsWith(targetLocale.split('-')[0]));
    if (voice) utterance.voice = voice;

    utterance.rate = 0.95; // Slightly slower for better clarity
    utterance.pitch = 1;

    utterance.onstart = () => setIsSpeaking(true);
    utterance.onend = () => setIsSpeaking(false);
    utterance.onerror = () => setIsSpeaking(false);

    utteranceRef.current = utterance;
    window.speechSynthesis.speak(utterance);
  }, [text, lang, isSpeaking]);

  if (!isSupported) return null;

  return (
    <button
      onClick={handleToggleSpeech}
      disabled={disabled}
      title={isSpeaking ? "Stop" : "Listen"}
      className={`relative flex items-center gap-2 px-4 py-2 rounded-xl font-bold text-xs uppercase tracking-widest transition-all active:scale-95 ${
        isSpeaking 
          ? 'bg-red-500/20 text-red-400 border border-red-500/30' 
          : 'bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 hover:bg-emerald-500/20'
      } disabled:opacity-50 disabled:cursor-not-allowed ${className}`}
    >
      {isSpeaking ? (
        <>
          <CircleStop className="w-4 h-4" />
          <span>Stop</span>
          <span className="absolute -top-1 -right-1 flex h-3 w-3">
            <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-red-400 opacity-75"></span>
            <span className="relative inline-flex rounded-full h-3 w-3 bg-red-500"></span>
          </span>
        </>
      ) : (
        <>
          <Volume2 className="w-4 h-4" />
          <span>Listen</span>
        </>
      )}
    </button>
  );
};

export default TTSButton;
