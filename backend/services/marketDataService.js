import { generateText } from './geminiService.js';

// Comprehensive dataset covering Crops, Vegetables, and Fruits across Indian Mandis
const INITIAL_MARKET_DATA = [
  // ── CROPS (Cereals, Pulses, Oilseeds, Commercial) ──
  {
    _id: 'mp_c1', id: 'mp_c1',
    crop: 'Wheat', category: 'crop',
    market: 'Nagpur APMC', state: 'Maharashtra',
    price: 2320, minPrice: 2180, maxPrice: 2460, modalPrice: 2320,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_c2', id: 'mp_c2',
    crop: 'Rice (Basmati)', category: 'crop',
    market: 'Karnal Mandi', state: 'Haryana',
    price: 3650, minPrice: 3400, maxPrice: 3900, modalPrice: 3650,
    unit: 'quintal', trend: 'stable',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_c3', id: 'mp_c3',
    crop: 'Paddy (Common)', category: 'crop',
    market: 'Burdwan APMC', state: 'West Bengal',
    price: 2200, minPrice: 2050, maxPrice: 2350, modalPrice: 2200,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_c4', id: 'mp_c4',
    crop: 'Cotton', category: 'crop',
    market: 'Rajkot APMC', state: 'Gujarat',
    price: 7250, minPrice: 6900, maxPrice: 7550, modalPrice: 7250,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_c5', id: 'mp_c5',
    crop: 'Soybean', category: 'crop',
    market: 'Indore Mandi', state: 'Madhya Pradesh',
    price: 4820, minPrice: 4600, maxPrice: 5020, modalPrice: 4820,
    unit: 'quintal', trend: 'down',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_c6', id: 'mp_c6',
    crop: 'Mustard (Sarson)', category: 'crop',
    market: 'Bharatpur Mandi', state: 'Rajasthan',
    price: 5450, minPrice: 5200, maxPrice: 5650, modalPrice: 5450,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_c7', id: 'mp_c7',
    crop: 'Maize (Corn)', category: 'crop',
    market: 'Davanagere APMC', state: 'Karnataka',
    price: 2150, minPrice: 1980, maxPrice: 2280, modalPrice: 2150,
    unit: 'quintal', trend: 'stable',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_c8', id: 'mp_c8',
    crop: 'Gram (Chana)', category: 'crop',
    market: 'Akola Mandi', state: 'Maharashtra',
    price: 5850, minPrice: 5600, maxPrice: 6100, modalPrice: 5850,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_c9', id: 'mp_c9',
    crop: 'Sugarcane', category: 'crop',
    market: 'Kolhapur APMC', state: 'Maharashtra',
    price: 335, minPrice: 315, maxPrice: 355, modalPrice: 335,
    unit: 'quintal', trend: 'stable',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_c10', id: 'mp_c10',
    crop: 'Groundnut (Peanut)', category: 'crop',
    market: 'Gondal APMC', state: 'Gujarat',
    price: 6400, minPrice: 6100, maxPrice: 6700, modalPrice: 6400,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_c11', id: 'mp_c11',
    crop: 'Turmeric', category: 'crop',
    market: 'Nizamabad APMC', state: 'Telangana',
    price: 13800, minPrice: 12900, maxPrice: 14700, modalPrice: 13800,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_c12', id: 'mp_c12',
    crop: 'Bajra (Pearl Millet)', category: 'crop',
    market: 'Jaipur Mandi', state: 'Rajasthan',
    price: 2280, minPrice: 2100, maxPrice: 2420, modalPrice: 2280,
    unit: 'quintal', trend: 'stable',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },

  // ── VEGETABLES (सब्जियां) ──
  {
    _id: 'mp_v1', id: 'mp_v1',
    crop: 'Onion (प्याज)', category: 'vegetable',
    market: 'Lasalgaon APMC', state: 'Maharashtra',
    price: 1850, minPrice: 1500, maxPrice: 2150, modalPrice: 1850,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_v2', id: 'mp_v2',
    crop: 'Potato (आलू)', category: 'vegetable',
    market: 'Agra Mandi', state: 'Uttar Pradesh',
    price: 1450, minPrice: 1250, maxPrice: 1650, modalPrice: 1450,
    unit: 'quintal', trend: 'stable',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_v3', id: 'mp_v3',
    crop: 'Tomato (टमाटर)', category: 'vegetable',
    market: 'Kolar APMC', state: 'Karnataka',
    price: 1350, minPrice: 950, maxPrice: 1700, modalPrice: 1350,
    unit: 'quintal', trend: 'down',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_v4', id: 'mp_v4',
    crop: 'Green Chilli (हरी मिर्च)', category: 'vegetable',
    market: 'Guntur Mandi', state: 'Andhra Pradesh',
    price: 3800, minPrice: 3400, maxPrice: 4200, modalPrice: 3800,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_v5', id: 'mp_v5',
    crop: 'Cauliflower (फूलगोभी)', category: 'vegetable',
    market: 'Azadpur Mandi', state: 'Delhi',
    price: 1600, minPrice: 1300, maxPrice: 1900, modalPrice: 1600,
    unit: 'quintal', trend: 'down',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_v6', id: 'mp_v6',
    crop: 'Cabbage (पत्तागोभी)', category: 'vegetable',
    market: 'Pune APMC', state: 'Maharashtra',
    price: 1200, minPrice: 950, maxPrice: 1450, modalPrice: 1200,
    unit: 'quintal', trend: 'stable',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_v7', id: 'mp_v7',
    crop: 'Lady Finger (भिंडी / Okra)', category: 'vegetable',
    market: 'Vashi APMC', state: 'Maharashtra',
    price: 2900, minPrice: 2500, maxPrice: 3300, modalPrice: 2900,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_v8', id: 'mp_v8',
    crop: 'Brinjal (बैंगन / Eggplant)', category: 'vegetable',
    market: 'Surat APMC', state: 'Gujarat',
    price: 1750, minPrice: 1400, maxPrice: 2050, modalPrice: 1750,
    unit: 'quintal', trend: 'stable',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_v9', id: 'mp_v9',
    crop: 'Carrot (गाजर)', category: 'vegetable',
    market: 'Chandigarh APMC', state: 'Punjab',
    price: 1550, minPrice: 1300, maxPrice: 1800, modalPrice: 1550,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_v10', id: 'mp_v10',
    crop: 'Garlic (लहसुन)', category: 'vegetable',
    market: 'Mandsaur Mandi', state: 'Madhya Pradesh',
    price: 12500, minPrice: 11000, maxPrice: 14200, modalPrice: 12500,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_v11', id: 'mp_v11',
    crop: 'Ginger (अदरक)', category: 'vegetable',
    market: 'Kochi APMC', state: 'Kerala',
    price: 8200, minPrice: 7500, maxPrice: 9100, modalPrice: 8200,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },

  // ── FRUITS (फल) ──
  {
    _id: 'mp_f1', id: 'mp_f1',
    crop: 'Apple (सेब)', category: 'fruit',
    market: 'Shimla Mandi', state: 'Himachal Pradesh',
    price: 7800, minPrice: 6500, maxPrice: 9200, modalPrice: 7800,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_f2', id: 'mp_f2',
    crop: 'Banana (केला)', category: 'fruit',
    market: 'Jalgaon APMC', state: 'Maharashtra',
    price: 1850, minPrice: 1600, maxPrice: 2100, modalPrice: 1850,
    unit: 'quintal', trend: 'stable',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_f3', id: 'mp_f3',
    crop: 'Mango (Alphonso / आम)', category: 'fruit',
    market: 'Ratnagiri APMC', state: 'Maharashtra',
    price: 11500, minPrice: 9800, maxPrice: 13500, modalPrice: 11500,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_f4', id: 'mp_f4',
    crop: 'Pomegranate (अनार)', category: 'fruit',
    market: 'Solapur APMC', state: 'Maharashtra',
    price: 8900, minPrice: 7500, maxPrice: 10400, modalPrice: 8900,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_f5', id: 'mp_f5',
    crop: 'Orange (संतरा / Mandarin)', category: 'fruit',
    market: 'Nagpur Cotton Market', state: 'Maharashtra',
    price: 4300, minPrice: 3800, maxPrice: 4900, modalPrice: 4300,
    unit: 'quintal', trend: 'down',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_f6', id: 'mp_f6',
    crop: 'Grapes (अंगूर)', category: 'fruit',
    market: 'Nashik APMC', state: 'Maharashtra',
    price: 5600, minPrice: 4900, maxPrice: 6300, modalPrice: 5600,
    unit: 'quintal', trend: 'stable',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_f7', id: 'mp_f7',
    crop: 'Papaya (पपीता)', category: 'fruit',
    market: 'Ananthapur APMC', state: 'Andhra Pradesh',
    price: 2100, minPrice: 1800, maxPrice: 2450, modalPrice: 2100,
    unit: 'quintal', trend: 'stable',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_f8', id: 'mp_f8',
    crop: 'Guava (अमरूद)', category: 'fruit',
    market: 'Prayagraj Mandi', state: 'Uttar Pradesh',
    price: 2600, minPrice: 2200, maxPrice: 3000, modalPrice: 2600,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_f9', id: 'mp_f9',
    crop: 'Watermelon (तरबूज)', category: 'fruit',
    market: 'Kurnool APMC', state: 'Andhra Pradesh',
    price: 1100, minPrice: 850, maxPrice: 1350, modalPrice: 1100,
    unit: 'quintal', trend: 'down',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  },
  {
    _id: 'mp_f10', id: 'mp_f10',
    crop: 'Lemon (नींबू)', category: 'fruit',
    market: 'Nellore Mandi', state: 'Andhra Pradesh',
    price: 4900, minPrice: 4200, maxPrice: 5600, modalPrice: 4900,
    unit: 'quintal', trend: 'up',
    updatedAt: new Date().toISOString(), lastUpdated: new Date().toISOString()
  }
];

class MarketDataService {
  constructor() {
    this.marketPrices = INITIAL_MARKET_DATA.map((item) => ({
      ...item,
      district: item.district || (item.market ? item.market.split(' ')[0] : ''),
      sourceBadge: 'reference_baseline',
    }));
    this.lastUpdated = new Date();
    this.lastLiveSuccessAt = null;
    this.lastFetchError = null;
    this.sourceStatus = 'seeded_reference';
    this.cooldownUntil = 0;
    this.updateIntervalMinutes = 15;
    this.refreshTimer = null;
    this.activeApiKey =
      process.env.DATA_GOV_IN_API_KEY ||
      process.env.AGMARKNET_API_KEY ||
      process.env.MARKET_API_KEY ||
      null;

    if (process.env.NODE_ENV !== 'test') {
      this.startAutoUpdater();
    }
  }

  getApiKey() {
    return (
      process.env.DATA_GOV_IN_API_KEY ||
      process.env.AGMARKNET_API_KEY ||
      process.env.MARKET_API_KEY ||
      this.activeApiKey
    );
  }

  classifyCommodity(commodity) {
    const text = (commodity || '').toLowerCase();
    const vegetables = [
      'onion', 'potato', 'tomato', 'chilli', 'cauliflower', 'cabbage', 'okra',
      'bhindi', 'brinjal', 'carrot', 'garlic', 'ginger', 'spinach', 'peas',
      'gourd', 'capsicum', 'coriander', 'radish', 'pumpkin',
    ];
    const fruits = [
      'apple', 'banana', 'mango', 'pomegranate', 'orange', 'grapes', 'papaya',
      'guava', 'watermelon', 'lemon', 'pineapple', 'lime', 'strawberry', 'sapota',
    ];

    for (const v of vegetables) {
      if (text.includes(v)) return 'vegetable';
    }
    for (const f of fruits) {
      if (text.includes(f)) return 'fruit';
    }
    return 'crop';
  }

  async fetchFromAgmarknetApi(apiKey) {
    if (!apiKey) {
      this.sourceStatus = this.lastLiveSuccessAt ? 'cached_agmarknet' : 'seeded_reference_no_api_key';
      return { ok: false, records: null, error: 'DATA_GOV_IN_API_KEY not configured' };
    }
    if (Date.now() < this.cooldownUntil) {
      return { ok: false, records: null, error: this.lastFetchError || 'Rate-limit cooldown active' };
    }

    try {
      const url = `https://api.data.gov.in/resource/9ef84268-d588-465a-a308-a864a43d0070?api-key=${encodeURIComponent(apiKey)}&format=json&limit=100`;
      const res = await fetch(url, { signal: AbortSignal.timeout(8000) });

      if (res.status === 429) {
        this.cooldownUntil = Date.now() + 5 * 60_000;
        const msg = 'Data.gov.in API HTTP 429 (rate-limited). Serving last verified data without fabricating updates.';
        this.lastFetchError = msg;
        this.sourceStatus = this.lastLiveSuccessAt ? 'cached_agmarknet_rate_limited' : 'seeded_reference_rate_limited';
        return { ok: false, records: null, error: msg };
      }

      if (!res.ok) {
        const msg = `Data.gov.in API HTTP ${res.status}`;
        this.lastFetchError = msg;
        this.sourceStatus = this.lastLiveSuccessAt ? 'cached_agmarknet_stale' : 'seeded_reference_api_error';
        return { ok: false, records: null, error: msg };
      }

      const json = await res.json();
      if (json && Array.isArray(json.records) && json.records.length > 0) {
        const fetchedIso = new Date().toISOString();
        const records = json.records.map((r, idx) => {
          const commodity = r.commodity || r.Commodity || 'Crop';
          const modalPrice = parseFloat(r.modal_price || r.Modal_Price) || 2000;
          const minPrice = parseFloat(r.min_price || r.Min_Price) || Math.round(modalPrice * 0.92);
          const maxPrice = parseFloat(r.max_price || r.Max_Price) || Math.round(modalPrice * 1.08);
          const state = r.state || r.State || 'India';
          const district = r.district || r.District || '';
          const market = r.market || r.Market || 'APMC Mandi';
          const category = this.classifyCommodity(commodity);

          return {
            _id: `agm_${commodity.toLowerCase().replace(/\W+/g, '_')}_${market.toLowerCase().replace(/\W+/g, '_')}_${idx}`,
            id: `agm_${commodity.toLowerCase().replace(/\W+/g, '_')}_${market.toLowerCase().replace(/\W+/g, '_')}_${idx}`,
            crop: commodity,
            category,
            market,
            district,
            state,
            price: modalPrice,
            modalPrice,
            minPrice,
            maxPrice,
            unit: 'quintal',
            trend: modalPrice >= maxPrice * 0.96 ? 'up' : modalPrice <= minPrice * 1.04 ? 'down' : 'stable',
            sourceBadge: 'agmarknet_live',
            arrivalDate: r.arrival_date || r.Arrival_Date || null,
            updatedAt: fetchedIso,
            lastUpdated: fetchedIso,
          };
        });

        this.lastLiveSuccessAt = new Date();
        this.lastFetchError = null;
        this.sourceStatus = 'live_agmarknet';
        console.log(`[MarketDataService] Fetched ${records.length} live records from Data.gov.in`);
        return { ok: true, records, error: null };
      }

      this.lastFetchError = 'Data.gov.in returned 0 records';
      this.sourceStatus = this.lastLiveSuccessAt ? 'cached_agmarknet_stale' : 'seeded_reference_empty_upstream';
      return { ok: false, records: null, error: this.lastFetchError };
    } catch (err) {
      this.lastFetchError = err.message;
      this.sourceStatus = this.lastLiveSuccessAt ? 'cached_agmarknet_stale' : 'seeded_reference_network_error';
      console.warn(`[MarketDataService] Data.gov.in fetch notice: ${err.message}. Preserving last verified prices.`);
      return { ok: false, records: null, error: err.message };
    }
  }

  async updatePrices() {
    const apiKey = this.getApiKey();
    const { ok, records, error } = await this.fetchFromAgmarknetApi(apiKey);
    if (ok && records && records.length > 0) {
      const mergedMap = new Map();
      for (const item of records) {
        mergedMap.set(`${item.crop.toLowerCase()}_${item.market.toLowerCase()}`, item);
      }
      for (const item of this.marketPrices) {
        const key = `${item.crop.toLowerCase()}_${item.market.toLowerCase()}`;
        if (!mergedMap.has(key)) {
          mergedMap.set(key, item);
        }
      }
      this.marketPrices = Array.from(mergedMap.values());
      this.lastUpdated = new Date();
      return { updatedFromLiveApi: true, liveCount: records.length, sourceStatus: this.sourceStatus };
    }

    // Never simulate fake random market swings; return honest cache/reference metadata
    return { updatedFromLiveApi: false, liveCount: 0, sourceStatus: this.sourceStatus, error };
  }

  startAutoUpdater() {
    if (this.refreshTimer) clearInterval(this.refreshTimer);
    this.refreshTimer = setInterval(() => {
      this.updatePrices().catch((e) => console.error('[MarketDataService] Auto update error:', e));
    }, this.updateIntervalMinutes * 60 * 1000);
    if (typeof this.refreshTimer.unref === 'function') {
      this.refreshTimer.unref();
    }
  }

  getPrices({ crop, state, district, market, category, sort } = {}) {
    const filtered = this.marketPrices.filter((p) => {
      const matchCrop = crop ? p.crop.toLowerCase().includes(crop.toLowerCase()) : true;
      const matchState = state ? p.state.toLowerCase().includes(state.toLowerCase()) : true;
      const matchDistrict = district
        ? (p.district || p.market || '').toLowerCase().includes(district.toLowerCase())
        : true;
      const matchMarket = market ? p.market.toLowerCase().includes(market.toLowerCase()) : true;
      const matchCategory =
        category && category.toLowerCase() !== 'all'
          ? (p.category || '').toLowerCase() === category.toLowerCase()
          : true;
      return matchCrop && matchState && matchDistrict && matchMarket && matchCategory;
    });

    if (sort === 'price_desc') {
      return [...filtered].sort((a, b) => (b.modalPrice || b.price) - (a.modalPrice || a.price));
    }
    if (sort === 'price_asc') {
      return [...filtered].sort((a, b) => (a.modalPrice || a.price) - (b.modalPrice || b.price));
    }
    return filtered;
  }

  compareMarkets({ crop, category } = {}) {
    const items = this.getPrices({ crop, category });
    const byCrop = new Map();
    for (const item of items) {
      const key = item.crop.split('(')[0].trim().toLowerCase();
      if (!byCrop.has(key)) byCrop.set(key, []);
      byCrop.get(key).push(item);
    }

    const comparisons = [];
    for (const [, entries] of byCrop.entries()) {
      const sorted = [...entries].sort((a, b) => (b.modalPrice || b.price) - (a.modalPrice || a.price));
      const best = sorted[0];
      const lowest = sorted[sorted.length - 1];
      comparisons.push({
        crop: best.crop,
        category: best.category,
        mandisCount: sorted.length,
        highestMandi: { market: best.market, state: best.state, price: best.modalPrice || best.price },
        lowestMandi: { market: lowest.market, state: lowest.state, price: lowest.modalPrice || lowest.price },
        spreadPerQuintal: (best.modalPrice || best.price) - (lowest.modalPrice || lowest.price),
        listings: sorted.slice(0, 5),
      });
    }
    return comparisons.sort((a, b) => b.spreadPerQuintal - a.spreadPerQuintal);
  }

  getStats() {
    const cropsCount = this.marketPrices.filter((p) => p.category === 'crop').length;
    const vegetablesCount = this.marketPrices.filter((p) => p.category === 'vegetable').length;
    const fruitsCount = this.marketPrices.filter((p) => p.category === 'fruit').length;

    return {
      total: this.marketPrices.length,
      cropsCount,
      vegetablesCount,
      fruitsCount,
      lastUpdated: this.lastUpdated.toISOString(),
      lastLiveSuccessAt: this.lastLiveSuccessAt ? this.lastLiveSuccessAt.toISOString() : null,
      sourceStatus: this.sourceStatus,
      lastFetchError: this.lastFetchError,
      apiKeyConfigured: Boolean(this.getApiKey()),
      updateIntervalMinutes: this.updateIntervalMinutes,
    };
  }
}

export const marketService = new MarketDataService();
