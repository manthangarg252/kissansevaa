import fetch from 'node-fetch';
import WeatherCache from '../models/WeatherCache.js';
import { callGeminiText, classifyGeminiError } from './geminiService.js';

const CACHE_TTL_MINUTES = Number(process.env.WEATHER_CACHE_TTL_MINUTES || 30);

// Deterministic fallback coordinates for major Indian agricultural hubs when geocoding isn't needed or offline
const INDIA_LOCATION_COORDS = {
  delhi: { name: 'New Delhi, Delhi', state: 'Delhi', district: 'New Delhi', lat: 28.6139, lon: 77.209 },
  'new delhi': { name: 'New Delhi, Delhi', state: 'Delhi', district: 'New Delhi', lat: 28.6139, lon: 77.209 },
  rajkot: { name: 'Rajkot, Gujarat', state: 'Gujarat', district: 'Rajkot', lat: 22.3039, lon: 70.8022 },
  nagpur: { name: 'Nagpur, Maharashtra', state: 'Maharashtra', district: 'Nagpur', lat: 21.1458, lon: 79.0882 },
  pune: { name: 'Pune, Maharashtra', state: 'Maharashtra', district: 'Pune', lat: 18.5204, lon: 73.8567 },
  ludhiana: { name: 'Ludhiana, Punjab', state: 'Punjab', district: 'Ludhiana', lat: 30.901, lon: 75.8573 },
  karnal: { name: 'Karnal, Haryana', state: 'Haryana', district: 'Karnal', lat: 29.6857, lon: 76.9905 },
  indore: { name: 'Indore, Madhya Pradesh', state: 'Madhya Pradesh', district: 'Indore', lat: 22.7196, lon: 75.8577 },
  jaipur: { name: 'Jaipur, Rajasthan', state: 'Rajasthan', district: 'Jaipur', lat: 26.9124, lon: 75.7873 },
  lucknow: { name: 'Lucknow, Uttar Pradesh', state: 'Uttar Pradesh', district: 'Lucknow', lat: 26.8467, lon: 80.9462 },
  varanasi: { name: 'Varanasi, Uttar Pradesh', state: 'Uttar Pradesh', district: 'Varanasi', lat: 25.3176, lon: 82.9739 },
  patna: { name: 'Patna, Bihar', state: 'Bihar', district: 'Patna', lat: 25.5941, lon: 85.1376 },
  bengaluru: { name: 'Bengaluru, Karnataka', state: 'Karnataka', district: 'Bengaluru', lat: 12.9716, lon: 77.5946 },
  hyderabad: { name: 'Hyderabad, Telangana', state: 'Telangana', district: 'Hyderabad', lat: 17.385, lon: 78.4867 },
  bhopal: { name: 'Bhopal, Madhya Pradesh', state: 'Madhya Pradesh', district: 'Bhopal', lat: 23.2599, lon: 77.4126 },
  kolhapur: { name: 'Kolhapur, Maharashtra', state: 'Maharashtra', district: 'Kolhapur', lat: 16.705, lon: 74.2433 },
};

const WMO_LABELS = {
  0: { en: 'Clear Sky', hi: 'साफ आसमान' },
  1: { en: 'Mainly Clear', hi: 'मुख्यतः साफ' },
  2: { en: 'Partly Cloudy', hi: 'आंशिक बादल' },
  3: { en: 'Overcast', hi: 'घने बादल' },
  45: { en: 'Foggy', hi: 'कोहरा' },
  48: { en: 'Depositing Rime Fog', hi: 'घना कोहरा' },
  51: { en: 'Light Drizzle', hi: 'हल्की बूंदाबांदी' },
  53: { en: 'Moderate Drizzle', hi: 'मध्यम बूंदाबांदी' },
  61: { en: 'Light Rain', hi: 'हल्की बारिश' },
  63: { en: 'Moderate Rain', hi: 'मध्यम बारिश' },
  65: { en: 'Heavy Rain', hi: 'तेज बारिश' },
  80: { en: 'Rain Showers', hi: 'बारिश की बौछारें' },
  95: { en: 'Thunderstorm', hi: 'आंधी-तूफान' },
};

export function weatherCodeToLabel(code, lang = 'en') {
  const entry = WMO_LABELS[Number(code)] || { en: 'Fair Weather', hi: 'सामान्य मौसम' };
  return lang === 'hi' || lang === 'bho' ? entry.hi : entry.en;
}

export async function resolveCoordinates({ lat, lon, location, district, state }) {
  const numLat = Number(lat);
  const numLon = Number(lon);
  if (Number.isFinite(numLat) && Number.isFinite(numLon) && Math.abs(numLat) <= 90 && Math.abs(numLon) <= 180 && !(numLat === 0 && numLon === 0)) {
    return {
      latitude: Number(numLat.toFixed(4)),
      longitude: Number(numLon.toFixed(4)),
      locationName: location || [district, state].filter(Boolean).join(', ') || `${numLat.toFixed(2)}, ${numLon.toFixed(2)}`,
      state: state || '',
      district: district || '',
    };
  }

  const queryText = (location || district || state || 'New Delhi').trim();
  const normalized = queryText.toLowerCase();
  for (const [key, preset] of Object.entries(INDIA_LOCATION_COORDS)) {
    if (normalized.includes(key)) {
      return {
        latitude: preset.lat,
        longitude: preset.lon,
        locationName: preset.name,
        state: state || preset.state,
        district: district || preset.district,
      };
    }
  }

  try {
    const geoUrl = `https://geocoding-api.open-meteo.com/v1/search?name=${encodeURIComponent(queryText)}&count=1&language=en&format=json`;
    const resp = await fetch(geoUrl, { signal: AbortSignal.timeout(6000) });
    if (resp.ok) {
      const geoJson = await resp.json();
      const first = geoJson?.results?.[0];
      if (first) {
        return {
          latitude: Number(first.latitude.toFixed(4)),
          longitude: Number(first.longitude.toFixed(4)),
          locationName: [first.name, first.admin1, first.country].filter(Boolean).join(', '),
          state: state || first.admin1 || '',
          district: district || first.name || '',
        };
      }
    }
  } catch {
    // Fall back safely to Nagpur central India coordinates if offline during geocoding
  }

  const fallback = INDIA_LOCATION_COORDS.delhi;
  return {
    latitude: fallback.lat,
    longitude: fallback.lon,
    locationName: queryText || fallback.name,
    state: state || fallback.state,
    district: district || fallback.district,
  };
}

/**
 * Deterministic agronomic weather rules engine — works reliably without any LLM.
 */
export function computeAgriWeatherAdvisories({ current, daily = [] }) {
  const advisories = [];
  const temp = Number(current?.temperatureC ?? 25);
  const wind = Number(current?.windSpeedKmh ?? 5);
  const humidity = Number(current?.humidityPct ?? 55);
  const currentRain = Number(current?.precipitationMm ?? 0);

  const today = daily[0] || {};
  const tomorrow = daily[1] || {};
  const next48RainProb = Math.max(
    Number(today.precipitationProbPct || 0),
    Number(tomorrow.precipitationProbPct || 0),
  );
  const next48RainMm = Number(today.precipitationMm || 0) + Number(tomorrow.precipitationMm || 0);
  const maxTemp3d = Math.max(...daily.slice(0, 3).map((d) => Number(d.tempMaxC ?? temp)), temp);
  const minTemp3d = Math.min(...daily.slice(0, 3).map((d) => Number(d.tempMinC ?? temp)), temp);
  const maxWind48 = Math.max(wind, Number(today.windMaxKmh || 0), Number(tomorrow.windMaxKmh || 0));

  // 1. Spraying window check
  let sprayWindowSuitable = true;
  if (currentRain > 0.5 || next48RainMm >= 2.5 || next48RainProb >= 55) {
    sprayWindowSuitable = false;
    advisories.push({
      id: 'spray_rain_risk',
      category: 'spray',
      severity: 'warning',
      sprayWindowSuitable: false,
      title: 'Hold Foliar Spraying (Wash-off Risk)',
      message: `Rain likely in the next 48 hours (${next48RainProb}% chance, ~${next48RainMm.toFixed(1)}mm). Delay pesticide/fungicide spraying until dry spell.`,
    });
  } else if (wind >= 18 || maxWind48 >= 22) {
    sprayWindowSuitable = false;
    advisories.push({
      id: 'spray_wind_drift',
      category: 'spray',
      severity: 'caution',
      sprayWindowSuitable: false,
      title: 'High Wind — Avoid Spray Drift',
      message: `Wind speed up to ${Math.round(Math.max(wind, maxWind48))} km/h risks uneven droplet drift. Spray only during calm early morning hours.`,
    });
  } else if (temp >= 37) {
    advisories.push({
      id: 'spray_heat_timing',
      category: 'spray',
      severity: 'caution',
      sprayWindowSuitable: true,
      title: 'Spray Only Before 9 AM or After 5 PM',
      message: `High daytime temperature (${temp.toFixed(1)}°C) causes rapid evaporation and leaf scorch.`,
    });
  } else {
    advisories.push({
      id: 'spray_optimal',
      category: 'spray',
      severity: 'info',
      sprayWindowSuitable: true,
      title: 'Favorable Spray Window',
      message: `Low rainfall risk (${next48RainProb}%) and calm wind (${Math.round(wind)} km/h). Suitable for foliar nutrient or bio-control application.`,
    });
  }

  // 2. Irrigation rule
  const rain3d = daily.slice(0, 3).reduce((acc, d) => acc + Number(d.precipitationMm || 0), 0);
  if (rain3d >= 10 || next48RainProb >= 70) {
    advisories.push({
      id: 'irrigation_defer',
      category: 'irrigation',
      severity: 'info',
      title: 'Pause Scheduled Irrigation',
      message: `Expected rainfall (~${rain3d.toFixed(1)}mm over 3 days) helps recharge root-zone moisture. Ensure field drainage channels are clear.`,
    });
  } else if (maxTemp3d >= 34 && humidity < 45) {
    advisories.push({
      id: 'irrigation_evap',
      category: 'irrigation',
      severity: 'caution',
      title: 'Increase Moisture Monitoring',
      message: `Dry heat (${maxTemp3d.toFixed(1)}°C max, ${Math.round(humidity)}% RH) increases evapotranspiration. Irrigate during cool hours to minimize loss.`,
    });
  }

  // 3. Heatwave / Cold / Heavy Rain rules
  if (maxTemp3d >= 40) {
    advisories.push({
      id: 'heat_stress',
      category: 'heat',
      severity: 'warning',
      title: 'Heat Stress Alert',
      message: `Peak temperatures forecast near ${maxTemp3d.toFixed(1)}°C. Apply organic mulch and provide shade/cool water for nurseries and livestock.`,
    });
  }
  if (minTemp3d <= 5) {
    advisories.push({
      id: 'cold_frost',
      category: 'cold',
      severity: 'warning',
      title: 'Frost / Cold Stress Risk',
      message: `Night temperatures may drop to ${minTemp3d.toFixed(1)}°C. Light evening irrigation helps buffer soil against frost injury.`,
    });
  }
  if (daily.slice(0, 3).some((d) => Number(d.precipitationMm || 0) >= 25)) {
    advisories.push({
      id: 'heavy_rain_drainage',
      category: 'rain',
      severity: 'warning',
      title: 'Heavy Rain & Waterlogging Alert',
      message: 'Heavy rain spell forecast (>=25mm/day). Open bund outlets, protect harvested produce, and defer urea top-dressing.',
    });
  }

  return { advisories, sprayWindowSuitable };
}

async function fetchOpenMeteoForecast(latitude, longitude, lang = 'en') {
  const url =
    `https://api.open-meteo.com/v1/forecast?latitude=${encodeURIComponent(latitude)}`
    + `&longitude=${encodeURIComponent(longitude)}`
    + '&current=temperature_2m,relative_humidity_2m,apparent_temperature,precipitation,weather_code,wind_speed_10m'
    + '&hourly=temperature_2m,precipitation_probability,precipitation,wind_speed_10m'
    + '&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max,wind_speed_10m_max'
    + '&forecast_days=7&timezone=auto';

  const resp = await fetch(url, { signal: AbortSignal.timeout(9000) });
  if (!resp.ok) {
    throw new Error(`Open-Meteo HTTP ${resp.status}`);
  }
  const raw = await resp.json();
  if (!raw?.current || !raw?.daily?.time) {
    throw new Error('Open-Meteo returned incomplete payload');
  }

  const current = {
    temperatureC: Number(raw.current.temperature_2m ?? 0),
    feelsLikeC: Number(raw.current.apparent_temperature ?? raw.current.temperature_2m ?? 0),
    humidityPct: Number(raw.current.relative_humidity_2m ?? 0),
    windSpeedKmh: Number(raw.current.wind_speed_10m ?? 0),
    precipitationMm: Number(raw.current.precipitation ?? 0),
    weatherCode: Number(raw.current.weather_code ?? 0),
    conditionLabel: weatherCodeToLabel(raw.current.weather_code, lang),
  };

  const daily = (raw.daily.time || []).map((date, idx) => ({
    date,
    tempMaxC: Number(raw.daily.temperature_2m_max?.[idx] ?? 0),
    tempMinC: Number(raw.daily.temperature_2m_min?.[idx] ?? 0),
    precipitationMm: Number(raw.daily.precipitation_sum?.[idx] ?? 0),
    precipitationProbPct: Number(raw.daily.precipitation_probability_max?.[idx] ?? 0),
    windMaxKmh: Number(raw.daily.wind_speed_10m_max?.[idx] ?? 0),
    weatherCode: Number(raw.daily.weather_code?.[idx] ?? 0),
    conditionLabel: weatherCodeToLabel(raw.daily.weather_code?.[idx], lang),
  }));

  const hourlyNext24 = (raw.hourly?.time || []).slice(0, 24).map((time, idx) => ({
    time,
    temperatureC: Number(raw.hourly.temperature_2m?.[idx] ?? 0),
    precipitationProbPct: Number(raw.hourly.precipitation_probability?.[idx] ?? 0),
    precipitationMm: Number(raw.hourly.precipitation?.[idx] ?? 0),
    windSpeedKmh: Number(raw.hourly.wind_speed_10m?.[idx] ?? 0),
  }));

  return { current, daily, hourlyNext24 };
}

// Optional test override
let forecastFetcherOverride = null;
export function setWeatherFetcherForTests(fn) {
  forecastFetcherOverride = typeof fn === 'function' ? fn : null;
}

export async function getWeatherForecast({
  lat,
  lon,
  location,
  district,
  state,
  language = 'en',
  forceRefresh = false,
  includeAi = false,
  userId = null,
}) {
  const coords = await resolveCoordinates({ lat, lon, location, district, state });
  const locationKey = `${coords.latitude.toFixed(2)},${coords.longitude.toFixed(2)}`;
  const now = new Date();
  const dbReady = WeatherCache.db?.readyState === 1;

  let cachedDoc = null;
  if (dbReady) {
    cachedDoc = await WeatherCache.findOne({ locationKey }).lean();
  }

  if (cachedDoc && !forceRefresh && new Date(cachedDoc.expiresAt) > now) {
    const { advisories, sprayWindowSuitable } = computeAgriWeatherAdvisories({
      current: cachedDoc.current,
      daily: cachedDoc.daily,
    });
    return {
      ...cachedDoc,
      advisories,
      sprayWindowSuitable,
      source: 'cache',
      isStale: false,
      staleReason: null,
    };
  }

  try {
    const live = forecastFetcherOverride
      ? await forecastFetcherOverride(coords.latitude, coords.longitude, language)
      : await fetchOpenMeteoForecast(coords.latitude, coords.longitude, language);

    const { advisories, sprayWindowSuitable } = computeAgriWeatherAdvisories(live);
    let aiSummary = null;
    let aiAvailable = false;
    let aiServiceStatus = includeAi ? 'pending' : 'not_requested';

    if (includeAi) {
      try {
        const aiRes = await callGeminiText({
          prompt: `Summarize farm actions for ${coords.locationName}: temp=${live.current.temperatureC}C, humidity=${live.current.humidityPct}%, wind=${live.current.windSpeedKmh}km/h, advisories=${advisories.map((a) => a.title).join('; ')}. Keep it 3 concise bullets in language "${language}".`,
          systemInstruction: 'You are an agronomist giving concise practical weather actions. Never invent numbers outside the provided forecast.',
          userId,
          feature: 'weather_advisory',
        });
        aiSummary = aiRes.text;
        aiAvailable = true;
        aiServiceStatus = 'ok';
      } catch (aiErr) {
        const classified = classifyGeminiError(aiErr);
        aiAvailable = false;
        aiServiceStatus = classified.code;
      }
    }

    const expiresAt = new Date(now.getTime() + CACHE_TTL_MINUTES * 60_000);
    const payload = {
      locationKey,
      locationName: coords.locationName,
      state: coords.state,
      district: coords.district,
      latitude: coords.latitude,
      longitude: coords.longitude,
      provider: 'open-meteo',
      current: live.current,
      daily: live.daily,
      hourlyNext24: live.hourlyNext24,
      advisories,
      sprayWindowSuitable,
      aiSummary,
      aiAvailable,
      aiServiceStatus,
      fetchedAt: now.toISOString(),
      expiresAt: expiresAt.toISOString(),
      source: 'live',
      isStale: false,
      staleReason: null,
    };

    if (dbReady) {
      await WeatherCache.findOneAndUpdate(
        { locationKey },
        { $set: { ...payload, fetchedAt: now, expiresAt } },
        { upsert: true, new: true },
      );
    }

    return payload;
  } catch (liveErr) {
    if (cachedDoc) {
      const { advisories, sprayWindowSuitable } = computeAgriWeatherAdvisories({
        current: cachedDoc.current,
        daily: cachedDoc.daily,
      });
      return {
        ...cachedDoc,
        advisories,
        sprayWindowSuitable,
        source: 'cached_stale',
        isStale: true,
        staleReason: `Live weather provider unavailable (${liveErr.message}); displaying cached forecast from ${new Date(cachedDoc.fetchedAt).toISOString()}.`,
      };
    }
    const err = new Error(`Weather provider unavailable and no cached forecast exists: ${liveErr.message}`);
    err.statusCode = 503;
    throw err;
  }
}
