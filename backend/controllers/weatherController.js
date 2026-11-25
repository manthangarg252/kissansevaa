import { getWeatherForecast, computeAgriWeatherAdvisories } from '../services/weatherService.js';

export const getForecast = async (req, res) => {
  try {
    const {
      lat,
      lon,
      location,
      district,
      state,
      language = 'en',
      refresh,
      includeAi,
    } = req.query;

    const forecast = await getWeatherForecast({
      lat,
      lon,
      location: location || req.user?.district || req.user?.state,
      district: district || req.user?.district,
      state: state || req.user?.state,
      language,
      forceRefresh: refresh === 'true' || refresh === '1',
      includeAi: includeAi === 'true' || includeAi === '1',
      userId: req.user?._id || null,
    });

    res.json(forecast);
  } catch (error) {
    const status = error.statusCode || 503;
    res.status(status).json({
      error: error.message || 'Unable to retrieve weather forecast',
      source: 'service_unavailable',
    });
  }
};

export const postFarmWeatherAlerts = async (req, res) => {
  try {
    const {
      lat,
      lon,
      location,
      district,
      state,
      crop,
      soilType,
      language = 'en',
      includeAi = true,
    } = req.body || {};

    const forecast = await getWeatherForecast({
      lat: lat ?? req.user?.latitude,
      lon: lon ?? req.user?.longitude,
      location: location || req.user?.district || req.user?.state,
      district: district || req.user?.district,
      state: state || req.user?.state,
      language,
      forceRefresh: false,
      includeAi: Boolean(includeAi),
      userId: req.user?._id || null,
    });

    const extraCropTips = [];
    if (crop || soilType) {
      const soilLower = String(soilType || '').toLowerCase();
      if (soilLower.includes('sandy') && !forecast.advisories.some((a) => a.id === 'irrigation_defer')) {
        extraCropTips.push({
          id: 'soil_sandy_moisture',
          category: 'irrigation',
          severity: 'info',
          title: `Sandy Soil Moisture Note (${crop || 'Crop'})`,
          message: 'Sandy soil drains rapidly; split irrigation into lighter, more frequent intervals.',
        });
      } else if (soilLower.includes('clay') && forecast.daily?.some((d) => Number(d.precipitationMm || 0) >= 10)) {
        extraCropTips.push({
          id: 'soil_clay_drainage',
          category: 'rain',
          severity: 'caution',
          title: `Clay Soil Waterlogging Check (${crop || 'Crop'})`,
          message: 'Heavy clay retains water after rain; inspect root-zone drainage to prevent root rot.',
        });
      }
    }

    res.json({
      ...forecast,
      advisories: [...(forecast.advisories || []), ...extraCropTips],
    });
  } catch (error) {
    const status = error.statusCode || 503;
    res.status(status).json({
      error: error.message || 'Unable to evaluate weather alerts',
      source: 'service_unavailable',
    });
  }
};
