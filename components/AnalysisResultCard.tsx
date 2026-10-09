import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import {
  CheckCircle2,
  ShieldAlert,
  Droplets,
  Loader2,
  Thermometer,
  ShieldCheck,
  AlertCircle,
  Sprout,
} from "lucide-react";
import TTSButton from "./TTSButton.tsx";

export interface CropDiagnosisResult {
  cropName: string;
  diseaseName: string;
  confidence: number;
  summary: string;
  severity: string;
  treatment: {
    organic: string[];
    chemical: string[];
  };
  prevention: string[];
  source?: string;
}

interface AnalysisResultCardProps {
  result: CropDiagnosisResult | null;
  loading: boolean;
  error: string | null;
}

const AnalysisResultCard: React.FC<AnalysisResultCardProps> = ({
  result,
  loading,
  error,
}) => {
  const { t, i18n } = useTranslation();

  const friendlyError = useMemo(() => {
    if (!error) return null;

    const err = error.toLowerCase();

    if (err.includes("invalid json")) {
      return "AI returned an unexpected format. Please try again.";
    }
    if (err.includes("quota")) {
      return "AI quota exceeded. Please try again after some time.";
    }
    if (err.includes("api key")) {
      return "AI key issue. Please check server environment settings.";
    }
    if (err.includes("model") && err.includes("not")) {
      return "AI model not available. Please update model name in backend.";
    }
    if (err.includes("temporarily unavailable")) {
      return "AI service is temporarily unavailable. Please try again.";
    }

    return error;
  }, [error]);

  // ✅ TTS narrative
  const speechText = useMemo(() => {
    if (!result) return "";

    const isHealthy =
      result.diseaseName.toLowerCase().includes("healthy") ||
      result.diseaseName === t("crop.healthy");

    const cropLabel = result.cropName;
    const statusLabel = result.diseaseName;
    const matchLabel = `${t("common.match")}: ${Math.round(
      result.confidence * 100
    )} percent`;

    let text = `${t("crop.health_report")}. ${t(
      "dashboard.cards.prices"
    )}: ${cropLabel}. ${t("common.results")}: ${statusLabel}. ${matchLabel}. ${
      result.summary
    }. `;

    if (!isHealthy) {
      if (result.treatment?.organic?.length > 0) {
        text += `${t("crop.organic")}: ${result.treatment.organic.join(
          ". "
        )}. `;
      }
      if (result.treatment?.chemical?.length > 0) {
        text += `${t("crop.chemical")}: ${result.treatment.chemical.join(
          ". "
        )}. `;
      }
      if (result.prevention?.length > 0) {
        text += `${t("crop.prevention")}: ${result.prevention.join(". ")}. `;
      }
    } else {
      if (result.prevention?.length > 0) {
        text += `${t("crop.maintenance")}: ${result.prevention.join(". ")}. `;
      }
    }

    return text;
  }, [result, t]);

  if (loading) {
    return (
      <div className="h-full flex flex-col items-center justify-center text-center p-12 glass rounded-3xl border border-emerald-500/20 bg-emerald-500/5 animate-pulse">
        <Loader2 className="w-12 h-12 text-emerald-500 animate-spin mb-4" />
        <h3 className="text-xl font-bold text-emerald-400 mb-2">
          {t("crop.analyzing")}
        </h3>
        <p className="text-slate-400 text-sm max-w-xs">{t("crop.analyzing_hint")}</p>
      </div>
    );
  }

  if (friendlyError) {
    return (
      <div className="h-full flex flex-col items-center justify-center text-center p-12 glass rounded-3xl border border-red-500/20 bg-red-500/5">
        <AlertCircle className="w-12 h-12 text-red-500 mb-4" />
        <h3 className="text-xl font-bold text-red-400 mb-2">
          {t("crop.failed")}
        </h3>

        <p className="text-slate-400 text-sm max-w-xs">{friendlyError}</p>

        <p className="text-slate-500 text-xs mt-4 max-w-xs">
          Tip: Try uploading a clear leaf photo in good light, without blur.
        </p>
      </div>
    );
  }

  if (!result) {
    return (
      <div className="h-full flex flex-col items-center justify-center text-center p-12 glass rounded-3xl border border-dashed border-slate-800 opacity-60">
        <div className="w-20 h-20 bg-slate-900 rounded-full flex items-center justify-center mb-6 shadow-inner">
          <Thermometer className="w-10 h-10 text-slate-600" />
        </div>
        <h3 className="text-xl font-bold text-slate-400 mb-2">
          {t("crop.diagnosis_ready")}
        </h3>
        <p className="text-slate-500 text-sm max-w-xs">{t("crop.diagnosis_hint")}</p>
      </div>
    );
  }

  const isHealthy =
    result.diseaseName.toLowerCase().includes("healthy") ||
    result.diseaseName === t("crop.healthy");

  const getSeverityColor = (severity: string) => {
    if (isHealthy)
      return "text-emerald-500 bg-emerald-500/10 border-emerald-500/20";

    switch (severity?.toLowerCase()) {
      case "high":
        return "text-red-500 bg-red-500/10 border-red-500/20";
      case "medium":
        return "text-amber-500 bg-amber-500/10 border-amber-500/20";
      case "low":
        return "text-emerald-500 bg-emerald-500/10 border-emerald-500/20";
      default:
        return "text-slate-400 bg-slate-400/10 border-slate-400/20";
    }
  };

  return (
    <div className="glass p-8 rounded-3xl border border-slate-800 space-y-6 animate-in slide-in-from-right-4 duration-500 shadow-xl">
      <div className="flex items-center justify-between flex-wrap gap-4">
        <h2 className="text-2xl font-bold text-white">
          {isHealthy ? t("crop.health_report") : t("crop.analysis_result")}
        </h2>

        <div className="flex items-center gap-3">
          <TTSButton text={speechText} lang={i18n.language} />
          <div className="flex items-center gap-2">
            <span
              className={`px-3 py-1 rounded-full text-[10px] font-bold border uppercase tracking-widest ${getSeverityColor(
                result.severity
              )}`}
            >
              {isHealthy ? t("crop.safe") : `${result.severity} ${t("common.severity")}`}
            </span>

            <span className="px-3 py-1 bg-slate-800 text-slate-300 rounded-full text-[10px] font-bold uppercase tracking-wider border border-slate-700">
              {t("common.source")}: {result.source === "gemini" ? "EXPERT AI" : "ViT Model"}
            </span>
          </div>
        </div>
      </div>

      <div
        className={`p-6 rounded-2xl border transition-colors ${
          isHealthy
            ? "bg-emerald-500/10 border-emerald-500/20"
            : "bg-slate-900/50 border-slate-800"
        }`}
      >
        <div className="flex items-center justify-between mb-4">
          <div className="space-y-1">
            <div className="flex items-center gap-2 text-emerald-500 text-xs font-bold uppercase tracking-widest">
              <Sprout className="w-4 h-4" />
              {t("dashboard.cards.prices")}
            </div>

            <h3 className={`text-2xl font-bold flex items-center gap-2 ${isHealthy ? "text-emerald-400" : "text-white"}`}>
              {result.cropName}
            </h3>

            <div className={`text-lg font-bold flex items-center gap-2 ${isHealthy ? "text-emerald-500/80" : "text-emerald-400"}`}>
              {isHealthy && <ShieldCheck className="w-5 h-5" />}
              {result.diseaseName}
            </div>
          </div>

          <div className="text-right">
            <span className="text-slate-400 text-xs font-bold block">{t("common.match")}</span>
            <span className="text-white font-bold text-xl">{Math.round(result.confidence * 100)}%</span>
          </div>
        </div>

        <div className="w-full bg-slate-800 h-1.5 rounded-full overflow-hidden">
          <div
            className="h-full transition-all duration-1000 bg-emerald-500"
            style={{ width: `${result.confidence * 100}%` }}
          />
        </div>

        <p className="mt-4 text-slate-300 text-sm leading-relaxed italic border-l-2 border-emerald-500/30 pl-4">
          {result.summary}
        </p>
      </div>

      <div className="space-y-4">
        {!isHealthy && (
          <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
            <div className="p-5 bg-emerald-500/5 border border-emerald-500/10 rounded-2xl">
              <h5 className="text-emerald-400 font-bold text-xs mb-3 flex items-center gap-2 uppercase tracking-widest">
                <CheckCircle2 className="w-4 h-4" /> {t("crop.organic")}
              </h5>
              <ul className="space-y-2">
                {result.treatment?.organic?.map((tr: string, i: number) => (
                  <li key={i} className="text-slate-400 text-[11px] flex gap-2">
                    <span className="text-emerald-500">•</span> {tr}
                  </li>
                ))}
              </ul>
            </div>

            <div className="p-5 bg-red-500/5 border border-red-500/10 rounded-2xl">
              <h5 className="text-red-400 font-bold text-xs mb-3 flex items-center gap-2 uppercase tracking-widest">
                <ShieldAlert className="w-4 h-4" /> {t("crop.chemical")}
              </h5>
              <ul className="space-y-2">
                {result.treatment?.chemical?.map((tr: string, i: number) => (
                  <li key={i} className="text-slate-400 text-[11px] flex gap-2">
                    <span className="text-red-500">•</span> {tr}
                  </li>
                ))}
              </ul>
            </div>
          </div>
        )}

        <div
          className={`p-5 rounded-2xl border ${
            isHealthy
              ? "bg-emerald-500/5 border-emerald-500/10"
              : "bg-slate-800/30 border-slate-800"
          }`}
        >
          <div className="flex items-center justify-between mb-3">
            <h5 className="text-slate-300 font-bold text-xs flex items-center gap-2 uppercase tracking-widest">
              <Droplets className="w-4 h-4 text-blue-400" />{" "}
              {isHealthy ? t("crop.maintenance") : t("crop.prevention")}
            </h5>
          </div>

          <ul className="grid grid-cols-1 md:grid-cols-2 gap-2">
            {result.prevention?.map((pr: string, i: number) => (
              <li key={i} className="text-slate-400 text-[10px] flex items-start gap-2">
                <div className="w-1 h-1 bg-slate-500 rounded-full mt-1.5 shrink-0" />
                {pr}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
};

export default AnalysisResultCard;
