import React, { useState, useRef, useEffect } from 'react';
import { Mic, MicOff, Loader2, Check, AlertCircle } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { Language } from '../types';
import * as voiceService from '../services/voiceService';
import { extractFieldsFromSpeech } from '../geminiService';

interface VoiceFormFillButtonProps {
  fields: string[];
  language: Language;
  onFill: (data: Record<string, any>) => void;
  className?: string;
  size?: 'sm' | 'md' | 'lg';
}

const VoiceFormFillButton: React.FC<VoiceFormFillButtonProps> = ({ 
  fields, 
  language, 
  onFill, 
  className = '',
  size = 'md'
}) => {
  const { t } = useTranslation();
  const [status, setStatus] = useState<'idle' | 'listening' | 'processing' | 'success' | 'error'>('idle');
  const [message, setMessage] = useState<string | null>(null);
  const recognitionRef = useRef<any>(null);

  useEffect(() => {
    return () => {
      if (recognitionRef.current) {
        recognitionRef.current.stop();
      }
    };
  }, []);

  const handleMicClick = () => {
    if (status === 'listening') {
      recognitionRef.current?.stop();
      return;
    }

    setStatus('listening');
    setMessage(t('voice.listening') || 'Listening...');

    const recognition = voiceService.startListening(
      language,
      async (text) => {
        if (!text.trim()) {
          setStatus('error');
          setMessage(t('voice.no_speech') || 'No speech detected.');
          setTimeout(() => setStatus('idle'), 3000);
          return;
        }

        setStatus('processing');
        setMessage(t('voice.processing') || 'Processing...');
        
        try {
          const data = await extractFieldsFromSpeech(text, fields, language);
          
          // Filter out nulls
          const filteredData: Record<string, any> = {};
          let foundAny = false;
          Object.entries(data).forEach(([key, value]) => {
            if (value !== null && value !== undefined && value !== '') {
              filteredData[key] = value;
              foundAny = true;
            }
          });

          if (foundAny) {
            onFill(filteredData);
            setStatus('success');
            setMessage(t('voice.filled') || 'Filled successfully ✅');
          } else {
            setStatus('error');
            setMessage(t('voice.not_detected') || 'Details not found.');
          }
        } catch (err) {
          console.error('Voice Extraction Error:', err);
          setStatus('error');
          setMessage(t('voice.failed') || 'Failed to parse voice.');
        } finally {
          setTimeout(() => setStatus('idle'), 4000);
        }
      },
      () => {
        // Fix: Use functional update to avoid TypeScript narrowing issues with closed-over state values that are incorrectly inferred as impossible
        setStatus(prev => prev === 'listening' ? 'idle' : prev);
      },
      (err) => {
        setStatus('error');
        setMessage(err === 'no-speech' ? (t('voice.no_speech') || 'No speech detected.') : (t('voice.error') || 'Speech error.'));
        setTimeout(() => setStatus('idle'), 3000);
      }
    );

    if (recognition) {
      recognitionRef.current = recognition;
    }
  };

  const iconSize = size === 'sm' ? 'w-4 h-4' : size === 'lg' ? 'w-6 h-6' : 'w-5 h-5';
  const padding = size === 'sm' ? 'p-1.5' : size === 'lg' ? 'p-3' : 'p-2.5';

  return (
    <div className={`flex items-center gap-3 ${className}`}>
      {status !== 'idle' && (
        <div className={`text-[10px] font-bold uppercase tracking-widest flex items-center gap-1.5 px-3 py-1.5 rounded-full border animate-in fade-in duration-300 ${
          status === 'success' ? 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20' :
          status === 'error' ? 'bg-red-500/10 text-red-400 border-red-500/20' :
          'bg-blue-500/10 text-blue-400 border-blue-500/20'
        }`}>
          {status === 'processing' && <Loader2 className="w-3 h-3 animate-spin" />}
          {status === 'success' && <Check className="w-3 h-3" />}
          {status === 'error' && <AlertCircle className="w-3 h-3" />}
          {message}
        </div>
      )}
      
      <button
        type="button"
        onClick={handleMicClick}
        title={t('voice.speak_details') || "Speak details"}
        disabled={status === 'processing'}
        className={`relative ${padding} rounded-xl transition-all active:scale-90 border border-transparent shadow-sm ${
          status === 'listening' 
            ? 'bg-blue-600 text-white shadow-[0_0_20px_rgba(37,99,235,0.4)] border-blue-400 animate-pulse' 
            : 'bg-slate-800 text-slate-400 hover:text-white hover:bg-slate-700 hover:border-slate-600'
        } disabled:opacity-50`}
      >
        {status === 'listening' ? <MicOff className={iconSize} /> : <Mic className={iconSize} />}
        {status === 'listening' && (
          <span className="absolute -top-1 -right-1 w-2.5 h-2.5 bg-red-500 rounded-full border border-slate-900"></span>
        )}
      </button>
    </div>
  );
};

export default VoiceFormFillButton;