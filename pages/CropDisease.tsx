
import React, { useState } from 'react';
import { Upload, CheckCircle2, Loader2 } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import { analyzeCropDisease } from '../geminiService';
import { fileToBase64, fileToDataUrl } from '../utils';
import AnalysisResultCard, { CropDiagnosisResult } from '../components/AnalysisResultCard.tsx';

const CropDisease: React.FC = () => {
  const { t, i18n } = useTranslation();
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [imageBase64, setImageBase64] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<CropDiagnosisResult | null>(null);
  
  // Farm Profile State
  const [location, setLocation] = useState('Nagpur, Maharashtra');
  const [soilType, setSoilType] = useState('Loamy');

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (file) {
      if (file.size > 5 * 1024 * 1024) {
        setError("Image size too large. Please upload an image smaller than 5MB.");
        return;
      }
      try {
        const [base64, dataUrl] = await Promise.all([
          fileToBase64(file),
          fileToDataUrl(file)
        ]);
        setImageBase64(base64);
        setImagePreview(dataUrl);
        setError(null);
      } catch (err) {
        setError("Failed to process image");
      }
    }
  };

  const handleDetect = async () => {
    if (!imageBase64) {
      setError("Please upload an image of the crop leaf first.");
      return;
    }
    
    setLoading(true);
    setResult(null);
    setError(null);
    
    try {
      const data = await analyzeCropDisease(
        imageBase64, 
        location, 
        soilType, 
        i18n.language as any
      );
      
      if (data && data.diseaseName) {
        setResult(data);
      } else {
        throw new Error("Received an invalid response from the analysis service.");
      }
    } catch (err: any) {
      console.error(err);
      setError(err.message || "Analysis failed. Please ensure the backend and ML services are running.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="space-y-8 animate-in fade-in duration-500">
      <div className="flex flex-col lg:flex-row gap-8">
        <div className="lg:w-1/2 space-y-6">
          <div className="glass p-8 rounded-3xl border border-slate-800 shadow-xl">
            <h2 className="text-2xl font-bold mb-6 flex items-center gap-3">
              <Upload className="text-emerald-500 w-7 h-7" /> {t('crop.title')}
            </h2>
            
            <div className="space-y-6">
              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-widest mb-2">{t('common.location')}</label>
                  <input 
                    type="text" 
                    value={location}
                    onChange={(e) => setLocation(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:ring-2 focus:ring-emerald-500 outline-none transition-all"
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold text-slate-500 uppercase tracking-widest mb-2">{t('common.soil_type')}</label>
                  <select 
                    value={soilType}
                    onChange={(e) => setSoilType(e.target.value)}
                    className="w-full bg-slate-900 border border-slate-700 rounded-xl px-4 py-3 text-white focus:ring-2 focus:ring-emerald-500 outline-none transition-all"
                  >
                    <option>Loamy</option>
                    <option>Sandy</option>
                    <option>Clay</option>
                    <option>Black Soil</option>
                    <option>Laterite</option>
                  </select>
                </div>
              </div>

              <div 
                className={`relative group h-64 border-2 border-dashed rounded-2xl flex flex-col items-center justify-center transition-all overflow-hidden ${imagePreview ? 'border-emerald-500/50 bg-emerald-500/5' : 'border-slate-700 hover:border-slate-500 bg-slate-900/50'}`}
              >
                {imagePreview ? (
                  <>
                    <img src={imagePreview} className="w-full h-full object-cover rounded-2xl" alt="Preview" />
                    <div className="absolute inset-0 bg-slate-950/40 opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center">
                       <p className="text-white text-xs font-bold uppercase tracking-widest">Change Image</p>
                    </div>
                  </>
                ) : (
                  <>
                    <Upload className="w-10 h-10 text-slate-500 group-hover:text-slate-300 transition-colors mb-3" />
                    <p className="text-slate-400 text-sm font-medium">{t('crop.upload_hint')}</p>
                  </>
                )}
                <input 
                  type="file" 
                  accept="image/*"
                  onChange={handleImageUpload}
                  className="absolute inset-0 opacity-0 cursor-pointer"
                />
              </div>

              <button 
                onClick={handleDetect}
                disabled={loading || !imageBase64}
                className="w-full bg-emerald-600 hover:bg-emerald-500 disabled:bg-slate-800 disabled:cursor-not-allowed text-white font-bold py-4 rounded-xl transition-all shadow-lg shadow-emerald-900/20 flex items-center justify-center gap-2"
              >
                {loading ? (
                  <>
                    <Loader2 className="w-5 h-5 animate-spin" />
                    Processing...
                  </>
                ) : (
                  <>
                    <CheckCircle2 className="w-5 h-5" />
                    {t('crop.detect_btn')}
                  </>
                )}
              </button>
            </div>
          </div>
        </div>

        <div className="lg:w-1/2">
          <AnalysisResultCard result={result} loading={loading} error={error} />
        </div>
      </div>
    </div>
  );
};

export default CropDisease;
