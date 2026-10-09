
import React, { useState, useRef } from 'react';
import { Mic, MicOff, Loader2 } from 'lucide-react';
import { startListening } from '../services/voiceService';
import { Language } from '../types';

interface MicButtonProps {
  onResult: (text: string) => void;
  lang: Language;
  className?: string;
  autoSend?: boolean;
  onSend?: () => void;
}

const MicButton: React.FC<MicButtonProps> = ({ onResult, lang, className = '', autoSend = false, onSend }) => {
  const [isListening, setIsListening] = useState(false);
  const recognitionRef = useRef<any>(null);

  const toggleMic = () => {
    if (isListening) {
      recognitionRef.current?.stop();
      setIsListening(false);
      return;
    }

    const recognition = startListening(
      lang,
      (text) => {
        onResult(text);
        if (autoSend && onSend) {
          setTimeout(() => onSend(), 500);
        }
      },
      () => setIsListening(false),
      (err) => {
        console.error(err);
        setIsListening(false);
      }
    );

    if (recognition) {
      recognitionRef.current = recognition;
      setIsListening(true);
    }
  };

  return (
    <button
      type="button"
      onClick={toggleMic}
      className={`relative p-3 rounded-xl transition-all active:scale-90 ${
        isListening 
          ? 'bg-blue-600 text-white shadow-[0_0_15px_rgba(37,99,235,0.5)]' 
          : 'bg-slate-800 text-slate-400 hover:text-white hover:bg-slate-700'
      } ${className}`}
      title={isListening ? "Stop Listening" : "Voice Input"}
    >
      {isListening ? (
        <>
          <MicOff className="w-5 h-5" />
          <span className="absolute inset-0 rounded-xl bg-blue-500 animate-ping opacity-20"></span>
        </>
      ) : (
        <Mic className="w-5 h-5" />
      )}
    </button>
  );
};

export default MicButton;
