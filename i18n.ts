
import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import LanguageDetector from 'i18next-browser-languagedetector';

// Import translations from TS modules to avoid 'assert { type: "json" }' syntax errors in browsers
import en from './locales/en.ts';
import hi from './locales/hi.ts';
import mr from './locales/mr.ts';
import pa from './locales/pa.ts';
import sa from './locales/sa.ts';

i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: {
      en: { translation: en },
      hi: { translation: hi },
      mr: { translation: mr },
      pa: { translation: pa },
      sa: { translation: sa }
    },
    fallbackLng: 'en',
    interpolation: {
      escapeValue: false
    },
    detection: {
      order: ['localStorage', 'navigator'],
      caches: ['localStorage']
    }
  });

export default i18n;
