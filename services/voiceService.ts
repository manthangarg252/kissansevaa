
import { Language } from '../types';

const localeMap: Record<string, string> = {
  en: 'en-IN',
  hi: 'hi-IN',
  mr: 'mr-IN',
  pa: 'pa-IN',
  sa: 'hi-IN'  // Sanskrit speech recognition and synthesis fallback to Hindi
};

const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;

export const isSTTSupported = () => !!SpeechRecognition;

export const startListening = (
  lang: Language, 
  onResult: (text: string) => void, 
  onEnd: () => void,
  onError?: (error: string) => void
) => {
  if (!SpeechRecognition) {
    onError?.("Speech Recognition not supported in this browser.");
    return null;
  }

  const recognition = new SpeechRecognition();
  recognition.lang = localeMap[lang] || 'en-IN';
  recognition.continuous = false;
  recognition.interimResults = false;

  recognition.onresult = (event: any) => {
    const transcript = event.results[0][0].transcript;
    onResult(transcript);
  };

  recognition.onend = () => onEnd();
  
  recognition.onerror = (event: any) => {
    console.debug("STT Error:", event.error);
    
    // Graceful handling of common transient errors
    if (event.error === 'no-speech') {
      // Don't show a scary error for "no-speech", just inform the UI through the callback if needed
      onError?.('no-speech');
    } else {
      onError?.(event.error);
    }
    onEnd();
  };

  try {
    recognition.start();
    return recognition;
  } catch (e) {
    console.error("Failed to start recognition", e);
    onEnd();
    return null;
  }
};

export const speak = (text: string, lang: Language, onStart?: () => void, onEnd?: () => void) => {
  if (!('speechSynthesis' in window)) return;

  window.speechSynthesis.cancel();

  const utterance = new SpeechSynthesisUtterance(text);
  const targetLocale = localeMap[lang] || 'en-IN';
  utterance.lang = targetLocale;
  
  const voices = window.speechSynthesis.getVoices();
  // Find a voice that matches the exact locale or at least the language code
  const voice = voices.find(v => v.lang === targetLocale) || 
                voices.find(v => v.lang.startsWith(targetLocale.split('-')[0]));
  
  if (voice) utterance.voice = voice;
  utterance.rate = 0.95; // Slightly slower for clarity in rural contexts
  utterance.pitch = 1;

  if (onStart) utterance.onstart = onStart;
  if (onEnd) utterance.onend = onEnd;
  utterance.onerror = () => onEnd?.();

  window.speechSynthesis.speak(utterance);
};

export const stopSpeaking = () => window.speechSynthesis.cancel();
