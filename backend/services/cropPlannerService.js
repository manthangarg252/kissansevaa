import CropPlan from '../models/CropPlan.js';
import PlannerTask from '../models/PlannerTask.js';
import { getWeatherForecast } from './weatherService.js';
import { callGeminiText } from './geminiService.js';

const STAGE_TEMPLATES = {
  pre_sowing: [
    { offset: 0, title: 'Soil Testing & Field Levelling', category: 'soil', priority: 'high', description: 'Collect topsoil samples and level seedbed for uniform water distribution.' },
    { offset: 1, title: 'Apply Well-Rotted FYM / Compost', category: 'fertilizer', priority: 'medium', description: 'Incorporate organic manure evenly prior to final harrowing.' },
    { offset: 3, title: 'Seed Selection & Bio-Treatment', category: 'soil', priority: 'high', description: 'Treat certified seed with Trichoderma viride or recommended rhizobium.' },
    { offset: 5, title: 'Inspect Irrigation Channels & Bunds', category: 'irrigation', priority: 'medium', description: 'Clean field inlets and check drip/sprinkler emitters.' },
  ],
  sowing: [
    { offset: 0, title: 'Precision Sowing at Recommended Spacing', category: 'soil', priority: 'high', description: 'Maintain uniform seed depth and row spacing.' },
    { offset: 1, title: 'Basal Fertilizer Placement', category: 'fertilizer', priority: 'high', description: 'Apply basal phosphorus/potash dose per soil health card.' },
    { offset: 3, title: 'Pre-Emergence Soil Moisture Check', category: 'irrigation', priority: 'medium', description: 'Ensure gentle surface moisture without crusting.' },
    { offset: 6, title: 'Early Germination Stand Count', category: 'inspection', priority: 'medium', description: 'Walk field transects to spot gaps or early damping-off.' },
  ],
  vegetative: [
    { offset: 0, title: 'Monitor Foliage for Early Pests & Spots', category: 'inspection', priority: 'high', description: 'Check underside of young leaves for aphids, whiteflies, or fungal lesions.' },
    { offset: 1, title: 'Scheduled Root-Zone Irrigation', category: 'irrigation', priority: 'high', description: 'Irrigate lightly during cool morning or late afternoon hours.' },
    { offset: 2, title: 'First Weeding & Inter-Cultivation', category: 'weeding', priority: 'medium', description: 'Remove competitive weeds to preserve nutrients and aeration.' },
    { offset: 4, title: 'Nitrogen Split Top-Dressing Assessment', category: 'fertilizer', priority: 'medium', description: 'Check leaf greenness before applying split nitrogen dose.' },
    { offset: 5, title: 'Foliar Micronutrient / Neem Bio-Spray', category: 'spray', priority: 'medium', description: 'Spray only during low-wind dry weather if threshold pests appear.' },
  ],
  flowering: [
    { offset: 0, title: 'Critical Flowering Moisture Check', category: 'irrigation', priority: 'high', description: 'Avoid moisture stress during bud initiation and flowering.' },
    { offset: 2, title: 'Pollinator-Safe Pest Inspection', category: 'inspection', priority: 'high', description: 'Avoid broad-spectrum sprays during peak bee foraging hours.' },
    { offset: 4, title: 'Boron / Potassium Foliar Support', category: 'spray', priority: 'medium', description: 'Apply foliar support in calm weather to improve flower retention.' },
    { offset: 6, title: 'Fungus & Wilt Perimeter Check', category: 'inspection', priority: 'medium', description: 'Rogue isolated diseased plants and maintain clean field bunds.' },
  ],
  fruiting: [
    { offset: 0, title: 'Fruit / Pod Fill Irrigation Check', category: 'irrigation', priority: 'high', description: 'Maintain steady root moisture to prevent pod drop or fruit cracking.' },
    { offset: 2, title: 'Borer / Fruit Fly Trap Inspection', category: 'inspection', priority: 'high', description: 'Check pheromone traps and remove damaged fruits promptly.' },
    { offset: 4, title: 'Protective Bio-Fungicide Window', category: 'spray', priority: 'medium', description: 'Schedule preventive spray only in dry non-rainy weather.' },
    { offset: 6, title: 'Lodging & Staking Support Check', category: 'soil', priority: 'low', description: 'Check tall plants/vines for wind support after storms.' },
  ],
  maturity: [
    { offset: 0, title: 'Taper Irrigation Before Harvest', category: 'irrigation', priority: 'high', description: 'Gradually reduce irrigation to improve grain dry-down / sugar concentration.' },
    { offset: 2, title: 'Maturity & Moisture Testing', category: 'inspection', priority: 'high', description: 'Sample grain hardness or fruit color readiness across plots.' },
    { offset: 4, title: 'Clean Storage Bags & Drying Floor', category: 'harvest', priority: 'medium', description: 'Sanitize tarpaulins and gunny bags before harvest intake.' },
  ],
  harvest: [
    { offset: 0, title: 'Harvest During Dry Daylight Hours', category: 'harvest', priority: 'high', description: 'Avoid harvesting wet dew-laden crop to reduce post-harvest mold.' },
    { offset: 2, title: 'Sun Drying & Moisture Safe-Storage', category: 'harvest', priority: 'high', description: 'Dry grain below safe moisture (~12% cereals) before bagging.' },
    { offset: 4, title: 'Compare APMC Mandi Rates Before Sale', category: 'harvest', priority: 'medium', description: 'Check KissanSevaAI Live Prices across nearby mandis for best realization.' },
    { offset: 6, title: 'Post-Harvest Stubble & Soil Mulch Plan', category: 'soil', priority: 'medium', description: 'Compost crop residue instead of burning to boost organic carbon.' },
  ],
};

function addDaysIso(baseDate, offsetDays) {
  const d = new Date(baseDate.getTime() + offsetDays * 86_400_000);
  return d.toISOString().slice(0, 10);
}

/**
 * Deterministic weather-aware 7-day crop task generator.
 */
export function buildDeterministicSevenDayPlan({
  cropName,
  stage = 'vegetative',
  soilType = 'Loamy',
  irrigationType = 'Drip / Canal',
  farmSizeAcres = 1,
  dailyWeather = [],
  startDate = new Date(),
}) {
  const templates = STAGE_TEMPLATES[stage] || STAGE_TEMPLATES.vegetative;
  const tasks = [];

  for (const tpl of templates) {
    let targetOffset = tpl.offset;
    let weatherAdjusted = false;
    let weatherReason = '';
    const dayWeather = dailyWeather[targetOffset];

    // Reschedule spray or irrigation away from rainy/windy forecast days
    if (tpl.category === 'spray' && dayWeather) {
      const rainy = Number(dayWeather.precipitationProbPct || 0) >= 50 || Number(dayWeather.precipitationMm || 0) >= 2;
      const windy = Number(dayWeather.windMaxKmh || 0) >= 22;
      if (rainy || windy) {
        const dryAltIndex = dailyWeather.findIndex(
          (d, idx) => idx > targetOffset && Number(d.precipitationProbPct || 0) < 40 && Number(d.windMaxKmh || 0) < 20,
        );
        if (dryAltIndex >= 0 && dryAltIndex <= 6) {
          weatherAdjusted = true;
          weatherReason = `Rescheduled from Day ${targetOffset + 1} due to ${rainy ? 'rain risk' : 'high wind'} forecast.`;
          targetOffset = dryAltIndex;
        } else {
          weatherAdjusted = true;
          weatherReason = 'Hold spray until dry low-wind window confirms.';
        }
      }
    } else if (tpl.category === 'irrigation' && dayWeather) {
      const heavyRain = Number(dayWeather.precipitationProbPct || 0) >= 65 || Number(dayWeather.precipitationMm || 0) >= 8;
      if (heavyRain) {
        weatherAdjusted = true;
        weatherReason = `Rainfall expected (~${Number(dayWeather.precipitationMm || 0).toFixed(1)}mm); skip manual irrigation if soil holds moisture.`;
      }
    }

    const scheduledDate = addDaysIso(startDate, targetOffset);
    let description = `${tpl.description} (Crop: ${cropName}, Soil: ${soilType}, Area: ${farmSizeAcres} ac, Irrigation: ${irrigationType}).`;
    if (weatherReason) {
      description = `${description} [Weather Rule: ${weatherReason}]`;
    }

    tasks.push({
      dayOffset: targetOffset,
      scheduledDate,
      title: `${cropName}: ${tpl.title}`,
      description,
      category: tpl.category,
      priority: tpl.priority,
      status: 'pending',
      weatherAdjusted,
      weatherReason,
      reminderEnabled: true,
    });
  }

  // Always add a Day 6 weekly field progress review
  tasks.push({
    dayOffset: 6,
    scheduledDate: addDaysIso(startDate, 6),
    title: `${cropName}: Weekly Crop Health & Stage Review`,
    description: `Update growth stage and review completed tasks for ${cropName} (${stage}). Scan suspicious leaves in Crop Disease AI.`,
    category: 'inspection',
    priority: 'medium',
    status: 'pending',
    weatherAdjusted: false,
    weatherReason: '',
    reminderEnabled: true,
  });

  return tasks.sort((a, b) => a.dayOffset - b.dayOffset);
}

export async function createPlanWithTasks({
  userId,
  cropName,
  variety = '',
  stage = 'vegetative',
  soilType = 'Loamy',
  irrigationType = 'Drip / Canal',
  farmSizeAcres = 1,
  locationName = 'India',
  latitude = null,
  longitude = null,
  sowingDate = '',
  customTask = '',
  language = 'en',
  clientRequestId = null,
  includeAi = true,
}) {
  if (clientRequestId) {
    const existing = await CropPlan.findOne({ userId, clientRequestId }).lean();
    if (existing) {
      const existingTasks = await PlannerTask.find({ planId: existing._id }).sort({ dayOffset: 1 }).lean();
      return { plan: existing, tasks: existingTasks, idempotentReplay: true };
    }
  }

  let dailyWeather = [];
  let resolvedLocation = locationName;
  let weatherSource = 'none';
  try {
    const forecast = await getWeatherForecast({
      lat: latitude,
      lon: longitude,
      location: locationName,
      language,
      includeAi: false,
    });
    dailyWeather = forecast.daily || [];
    resolvedLocation = forecast.locationName || locationName;
    weatherSource = forecast.source || 'live';
  } catch {
    // Planner works deterministically even when external weather is down
  }

  const parsedStart = sowingDate && /^\d{4}-\d{2}-\d{2}$/.test(String(sowingDate).trim())
    ? new Date(`${String(sowingDate).trim()}T00:00:00Z`)
    : null;
  const startDate = parsedStart && !Number.isNaN(parsedStart.getTime()) ? parsedStart : new Date();
  const generatedTasks = buildDeterministicSevenDayPlan({
    cropName,
    stage,
    soilType,
    irrigationType,
    farmSizeAcres,
    dailyWeather,
    startDate,
  });

  if (customTask && String(customTask).trim()) {
    generatedTasks.unshift({
      dayOffset: 0,
      scheduledDate: new Date().toISOString().slice(0, 10),
      title: String(customTask).trim().slice(0, 140),
      description: `Farmer custom task added for ${cropName} (${farmSizeAcres} ac, ${soilType} soil, ${irrigationType}).`,
      category: 'custom',
      priority: 'high',
      status: 'pending',
      weatherAdjusted: false,
      weatherReason: '',
    });
  }

  let generationSource = 'rules_only';
  let aiAdvisoryNote = null;
  let aiServiceStatus = includeAi ? 'unavailable' : 'not_requested';

  if (includeAi) {
    try {
      const aiRes = await callGeminiText({
        prompt: `Provide 2 concise weekly agronomic tips for ${cropName} (${variety || 'standard variety'}) at "${stage}" stage in "${soilType}" soil, "${irrigationType}" irrigation, near ${resolvedLocation}. Reply in language "${language}".`,
        systemInstruction: 'You are an Indian Krishi Vigyan Kendra agronomist. Keep advice practical, safe, and concise.',
        userId,
        feature: 'crop_planner',
      });
      generationSource = 'rules+gemini';
      aiAdvisoryNote = aiRes.text;
      aiServiceStatus = 'ok';
    } catch (err) {
      aiServiceStatus = err?.code || 'upstream_unavailable';
    }
  }

  const plan = await CropPlan.create({
    userId,
    clientRequestId: clientRequestId || undefined,
    cropName,
    variety,
    stage,
    soilType,
    irrigationType,
    farmSizeAcres: Number(farmSizeAcres) || 1,
    locationName: resolvedLocation,
    latitude,
    longitude,
    sowingDate: sowingDate ? String(sowingDate).trim() : '',
    customTask: customTask ? String(customTask).trim() : '',
    startDate,
    status: 'active',
    generationSource,
    aiAdvisoryNote,
  });

  const savedTasks = await PlannerTask.insertMany(
    generatedTasks.map((t) => ({
      ...t,
      userId,
      planId: plan._id,
    })),
  );

  return {
    plan: plan.toObject(),
    tasks: savedTasks.map((t) => t.toObject()),
    weatherSource,
    aiAvailable: generationSource === 'rules+gemini',
    aiServiceStatus,
  };
}
