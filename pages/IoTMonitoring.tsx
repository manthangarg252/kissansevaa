import React, { useState, useEffect } from 'react';
import { Thermometer, Droplets, Wind, Zap, RefreshCcw, Sparkles, Loader2, Info, AlertTriangle } from 'lucide-react';
import { Language, SensorData } from '../types';
import { TRANSLATIONS } from '../constants';
import VoiceControls from '../components/VoiceControls.tsx';
import { getIoTInsights } from '../geminiService';

const StatCard: React.FC<{ label: string, value: string, icon: React.ReactNode, color: string, trend: string, lang: Language }> = ({ label, value, icon, color, trend, lang }) => (
  <div className="glass p-6 rounded-[2rem] border border-slate-800 hover:border-slate-700 transition-all group relative overflow-hidden">
    <div className="absolute top-0 right-0 p-6 opacity-5 -z-10 group-hover:opacity-10 transition-opacity">
      {icon}
    </div>
    <div className="flex items-center justify-between mb-4">
      <div className={`p-3 rounded-2xl ${color} group-hover:scale-110 transition-transform shadow-lg shadow-black/20`}>
        {icon}
      </div>
      <div className="flex items-center gap-2">
        <VoiceControls 
          enableSpeaker 
          textToSpeak={`${label} is currently ${value}`} 
          lang={lang} 
          size="sm"
        />
        <span className="text-[10px] font-bold text-emerald-400 uppercase tracking-widest bg-emerald-500/10 px-2 py-0.5 rounded-full">{trend}</span>
      </div>
    </div>
    <div className="space-y-1">
      <p className="text-slate-500 text-xs font-bold uppercase tracking-widest">{label}</p>
      <h3 className="text-3xl font-extrabold text-white tracking-tight">{value}</h3>
    </div>
    <div className="mt-4 pt-4 border-t border-slate-800/50 flex items-center justify-between text-[10px] text-slate-600 font-bold uppercase tracking-[0.2em]">
      <span>Sensor: Live</span>
      <div className="flex items-center gap-1">
        <span className="w-1.5 h-1.5 bg-emerald-500 rounded-full animate-pulse"></span>
        Active
      </div>
    </div>
  </div>
);

const IoTMonitoring: React.FC<{ language: Language }> = ({ language }) => {
  // Deterministic seed based on current time for realistic but smooth simulation
  const getSimulatedData = () => {
    const now = new Date();
    const hour = now.getHours();
    const minute = now.getMinutes();
    const timeOfDay = hour + minute / 60;
    
    // Diurnal temperature pattern: cooler at night, warmer midday
    const baseTemp = 26;
    const tempVariation = 4 * Math.sin((timeOfDay - 6) * Math.PI / 12); // peaks at noon
    const temperature = +(baseTemp + tempVariation + (Math.sin(now.getSeconds() / 30) * 0.5)).toFixed(1);
    
    // Humidity inversely correlated with temperature
    const baseHumidity = 65;
    const humidityVariation = -8 * Math.sin((timeOfDay - 6) * Math.PI / 12);
    const humidity = +Math.min(95, Math.max(35, baseHumidity + humidityVariation + (Math.cos(now.getSeconds() / 20) * 2))).toFixed(0);
    
    // Soil moisture: slowly decreasing during day, slight recovery at night
    const baseMoisture = 48;
    const moistureVariation = -5 * Math.sin((timeOfDay - 8) * Math.PI / 16);
    const moisture = +Math.min(80, Math.max(20, baseMoisture + moistureVariation)).toFixed(0);
    
    // pH: relatively stable with minor drift
    const ph = +(6.5 + 0.3 * Math.sin(timeOfDay / 3) + 0.1 * Math.sin(now.getMinutes() / 10)).toFixed(1);
    
    return { temperature, humidity, moisture, ph, timestamp: now.toLocaleTimeString() };
  };

  const [data, setData] = useState<SensorData>(getSimulatedData());

  const [aiInsight, setAiInsight] = useState<string | null>(null);
  const [isLoadingInsight, setIsLoadingInsight] = useState(false);

  const t = TRANSLATIONS[language];

  // Fetch AI Insights based on current sensor state
  const fetchInsights = async () => {
    setIsLoadingInsight(true);
    try {
      const res = await getIoTInsights(data, language);
      setAiInsight(res);
    } catch (e) {
      console.error(e);
    } finally {
      setIsLoadingInsight(false);
    }
  };

  useEffect(() => {
    const interval = setInterval(() => {
      setData(getSimulatedData());
    }, 15000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    fetchInsights();
  }, [language]);

  // Dynamic alert based on actual sensor readings
  const getAlertMessage = () => {
    if (data.moisture < 35) return 'Critical Alert: Soil moisture low — irrigation recommended';
    if (data.temperature > 35) return 'Warning: High temperature detected — consider shade nets';
    if (data.humidity > 85) return 'Alert: High humidity — monitor for fungal infections';
    if (data.ph < 6.0) return 'Alert: Acidic soil pH detected — lime application may be needed';
    if (data.ph > 7.5) return 'Alert: Alkaline soil pH — consider gypsum treatment';
    return 'All parameters within optimal range';
  };

  // Realistic trend calculations
  const getTrend = (value: number, optimal: number, range: number) => {
    const diff = ((value - optimal) / range) * 100;
    if (diff > 5) return `+${diff.toFixed(1)}%`;
    if (diff < -5) return `${diff.toFixed(1)}%`;
    return 'Optimal';
  };

  // Generate deterministic historical bar chart data based on time
  const generateHistoricalData = () => {
    const now = new Date();
    const baseHour = now.getHours();
    return Array.from({ length: 30 }).map((_, i) => {
      const hourOffset = (i / 30) * 24;
      const simHour = (baseHour - 24 + hourOffset + 24) % 24;
      // Diurnal pattern for visual realism
      const value = 40 + 30 * Math.sin((simHour - 6) * Math.PI / 12) + 10 * Math.sin(i * 0.7);
      return Math.min(100, Math.max(10, value));
    });
  };

  const historicalData = generateHistoricalData();

  return (
    <div className="space-y-10 animate-in fade-in duration-500 pb-20">
      <div className="flex flex-col md:flex-row items-center justify-between gap-6 glass p-8 rounded-[2.5rem] border border-slate-800 shadow-2xl">
        <div className="space-y-1">
          <h1 className="text-3xl font-extrabold text-white tracking-tight">{t.iot}</h1>
          <p className="text-slate-400">Real-time telemetry from your connected farm sensors.</p>
        </div>
        <div className="flex items-center gap-3 w-full md:w-auto">
          <button 
            onClick={fetchInsights}
            disabled={isLoadingInsight}
            className="flex-1 md:flex-none flex items-center justify-center gap-2 bg-emerald-600/10 hover:bg-emerald-600/20 px-6 py-3 rounded-2xl border border-emerald-500/20 transition-all text-xs font-bold text-emerald-400 uppercase tracking-widest"
          >
            <Sparkles className={`w-4 h-4 ${isLoadingInsight ? 'animate-pulse' : ''}`} />
            Analyze Telemetry
          </button>
          <div className="flex items-center gap-2 bg-slate-900 px-6 py-3 rounded-2xl border border-slate-800 shadow-inner">
            <RefreshCcw className="w-4 h-4 text-emerald-500 animate-spin-slow" />
            <span className="text-[10px] font-bold text-slate-500 uppercase tracking-widest">{t.syncing}</span>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        <StatCard label={t.temp} value={`${data.temperature}°C`} icon={<Thermometer className="w-6 h-6 text-orange-400" />} color="bg-orange-500/10" trend={getTrend(data.temperature, 28, 10)} lang={language} />
        <StatCard label={t.humidity} value={`${data.humidity}%`} icon={<Wind className="w-6 h-6 text-blue-400" />} color="bg-blue-500/10" trend={getTrend(data.humidity, 60, 20)} lang={language} />
        <StatCard label={t.moisture} value={`${data.moisture}%`} icon={<Droplets className="w-6 h-6 text-emerald-400" />} color="bg-emerald-500/10" trend={getTrend(data.moisture, 50, 15)} lang={language} />
        <StatCard label={t.ph} value={`${data.ph} pH`} icon={<Zap className="w-6 h-6 text-purple-400" />} color="bg-purple-500/10" trend={data.ph >= 6.2 && data.ph <= 7.2 ? 'Stable' : 'Warning'} lang={language} />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2 glass p-10 rounded-[2.5rem] border border-emerald-500/20 bg-emerald-500/5 relative overflow-hidden shadow-2xl">
          <div className="flex items-center justify-between mb-8">
            <div className="flex items-center gap-4">
              <div className="p-4 bg-emerald-500/20 rounded-[1.5rem] shadow-lg shadow-emerald-900/20">
                <Sparkles className="w-8 h-8 text-emerald-500" />
              </div>
              <div className="space-y-1">
                <h3 className="text-2xl font-bold text-white tracking-tight">AI Farm Intelligence</h3>
                <p className="text-xs text-slate-500 font-bold uppercase tracking-widest">Real-time interpretation</p>
              </div>
            </div>
            <VoiceControls enableSpeaker textToSpeak={aiInsight || ''} lang={language} size="lg" />
          </div>

          {isLoadingInsight ? (
            <div className="flex flex-col items-center justify-center py-16 space-y-6">
              <Loader2 className="w-12 h-12 text-emerald-500 animate-spin" />
              <div className="space-y-2 text-center">
                <p className="text-sm text-slate-300 animate-pulse font-bold uppercase tracking-widest">Processing Satellite & Ground Data...</p>
                <p className="text-xs text-slate-500 italic">This usually takes a few seconds</p>
              </div>
            </div>
          ) : aiInsight ? (
            <div className="space-y-6 animate-in fade-in slide-in-from-bottom-4 duration-500">
              <div className="prose prose-invert max-w-none text-slate-300 leading-relaxed text-lg whitespace-pre-wrap font-medium">
                {aiInsight}
              </div>
              <div className={`flex items-center gap-3 p-4 border rounded-2xl w-fit shadow-lg ${
                data.moisture < 35 || data.temperature > 35 
                  ? 'bg-red-500/10 border-red-500/20 shadow-red-950/40' 
                  : 'bg-emerald-500/10 border-emerald-500/20 shadow-emerald-950/40'
              }`}>
                <AlertTriangle className={`w-5 h-5 ${data.moisture < 35 || data.temperature > 35 ? 'text-red-400' : 'text-emerald-400'}`} />
                <span className={`text-xs font-bold uppercase tracking-widest ${data.moisture < 35 || data.temperature > 35 ? 'text-red-400' : 'text-emerald-400'}`}>{getAlertMessage()}</span>
              </div>
            </div>
          ) : (
            <div className="text-center py-20 opacity-30 flex flex-col items-center">
              <Info className="w-16 h-16 mb-4 text-slate-700" />
              <p className="text-lg font-bold text-slate-500">Awaiting sensor data verification...</p>
            </div>
          )}
        </div>

        <div className="lg:col-span-1 glass p-10 rounded-[2.5rem] border border-slate-800 space-y-8 shadow-2xl">
          <div className="space-y-2">
            <h3 className="text-xl font-bold text-white tracking-tight">{t.historical}</h3>
            <p className="text-xs text-slate-500 uppercase tracking-widest font-bold">Past 24 Hours Activity</p>
          </div>
          
          <div className="h-64 flex items-end gap-1.5 overflow-hidden px-2 relative group">
            {historicalData.map((val, i) => (
              <div 
                key={i} 
                className="flex-1 bg-emerald-500/10 hover:bg-emerald-500/60 transition-all rounded-t-lg relative group/bar" 
                style={{ height: `${val}%` }}
              >
                <div className="absolute -top-8 left-1/2 -translate-x-1/2 bg-slate-900 text-[10px] px-2 py-1 rounded opacity-0 group-hover/bar:opacity-100 transition-opacity whitespace-nowrap border border-slate-700 pointer-events-none">
                  Value: {Math.round(val)}
                </div>
              </div>
            ))}
            <div className="absolute inset-x-0 bottom-0 h-0.5 bg-slate-800"></div>
          </div>
          
          <div className="flex justify-between text-[10px] text-slate-600 font-bold uppercase tracking-[0.2em] pt-4">
            <span>06 AM</span>
            <span>12 PM</span>
            <span>06 PM</span>
            <span className="text-emerald-500">Now</span>
          </div>

          <div className="space-y-4 pt-6 border-t border-slate-800/50">
             <div className="flex items-center justify-between">
                <span className="text-xs text-slate-400">Irrigation Cycles</span>
                <span className="text-xs font-bold text-white">{data.moisture < 40 ? '6 Today' : data.moisture < 50 ? '4 Today' : '2 Today'}</span>
             </div>
             <div className="flex items-center justify-between">
                <span className="text-xs text-slate-400">Average Temp</span>
                <span className="text-xs font-bold text-white">{(data.temperature - 1.5).toFixed(1)}°C</span>
             </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default IoTMonitoring;
