
import React, { useState, useRef, useEffect } from 'react';
import { Mic, MicOff, Volume2, Square, Loader2 } from 'lucide-react';
import * as voiceService from '../services/voiceService';
import { Language } from '../types';

interface VoiceControlsProps {
  enableMic?: boolean;
  enableSpeaker?: boolean;
  onMicResult?: (text: string) => void;
  onMicError?: (error: string) => void;
  textToSpeak?: string;
  lang: Language;
  className?: string;
  autoSend?: boolean;
  onSend?: () => void;
  size?: 'sm' | 'md' | 'lg';
}

const VoiceControls: React.FC<VoiceControlsProps> = ({ 
  enableMic = false, 
  enableSpeaker = false, 
  onMicResult, 
  onMicError,
  textToSpeak, 
  lang, 
  className = '', 
  autoSend = false, 
  onSend,
  size = 'md'
}) => {
  const [isListening, setIsListening] = useState(false);
  const [isSpeaking, setIsSpeaking] = useState(false);
  const recognitionRef = useRef<any>(null);

  useEffect(() => {
    return () => {
      voiceService.stopSpeaking();
      if (recognitionRef.current) {
        recognitionRef.current.stop();
      }
    };
  }, []);

  const toggleMic = () => {
    if (isListening) {
      recognitionRef.current?.stop();
      setIsListening(false);
      return;
    }

    voiceService.stopSpeaking(); // Stop any playback if user starts speaking

    const recognition = voiceService.startListening(
      lang,
      (text) => {
        onMicResult?.(text);
        if (autoSend && onSend) {
          setTimeout(() => onSend(), 500);
        }
      },
      () => setIsListening(false),
      (err) => {
        // Handle specific error codes
        if (onMicError) {
          onMicError(err);
        } else {
          console.debug("Mic error:", err);
        }
        setIsListening(false);
      }
    );

    if (recognition) {
      recognitionRef.current = recognition;
      setIsListening(true);
    }
  };

  const toggleSpeak = () => {
    if (isSpeaking) {
      voiceService.stopSpeaking();
      setIsSpeaking(false);
      return;
    }

    if (textToSpeak) {
      voiceService.speak(
        textToSpeak,
        lang,
        () => setIsSpeaking(true),
        () => setIsSpeaking(false)
      );
    }
  };

  const iconSize = size === 'sm' ? 'w-4 h-4' : size === 'lg' ? 'w-6 h-6' : 'w-5 h-5';
  const padding = size === 'sm' ? 'p-1.5' : size === 'lg' ? 'p-4' : 'p-2.5';

  return (
    <div className={`flex items-center gap-2 ${className}`}>
      {enableMic && (
        <button
          type="button"
          onClick={toggleMic}
          disabled={isSpeaking}
          className={`relative ${padding} rounded-xl transition-all active:scale-90 border border-transparent shadow-sm ${
            isListening 
              ? 'bg-blue-600 text-white shadow-[0_0_20px_rgba(37,99,235,0.4)] border-blue-400 animate-pulse' 
              : 'bg-slate-800 text-slate-400 hover:text-white hover:bg-slate-700 hover:border-slate-600'
          } disabled:opacity-50`}
        >
          {isListening ? <MicOff className={iconSize} /> : <Mic className={iconSize} />}
          {isListening && (
            <span className="absolute -top-1 -right-1 w-3 h-3 bg-red-500 rounded-full border-2 border-slate-900 animate-bounce"></span>
          )}
        </button>
      )}

      {enableSpeaker && textToSpeak && (
        <button
          type="button"
          onClick={toggleSpeak}
          disabled={isListening}
          className={`relative ${padding} rounded-xl transition-all active:scale-90 border border-transparent shadow-sm ${
            isSpeaking 
              ? 'bg-emerald-600 text-white shadow-[0_0_20px_rgba(16,185,129,0.4)] border-emerald-400' 
              : 'bg-slate-800 text-slate-400 hover:text-emerald-400 hover:bg-slate-700 hover:border-emerald-900/30'
          } disabled:opacity-50`}
        >
          {isSpeaking ? <Square className={`${iconSize} fill-current`} /> : <Volume2 className={iconSize} />}
          {isSpeaking && (
            <div className="absolute -top-1 -right-1 flex gap-0.5">
              <span className="w-1.5 h-1.5 bg-emerald-400 rounded-full animate-ping"></span>
            </div>
          )}
        </button>
      )}
    </div>
  );
};

export default VoiceControls;
