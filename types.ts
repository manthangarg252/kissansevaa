
export type Language = 'en' | 'hi' | 'sa' | 'pa' | 'mr';

export interface User {
  id: string;
  email: string;
  name: string;
}

export interface Scheme {
  id: string;
  name: string;
  category: 'Irrigation' | 'Organic' | 'Insurance' | 'Financial' | 'Income Support' | 'Technology' | 'Horticulture' | 'Infrastructure' | 'Marketing' | 'Soil Health' | 'Seeds' | 'Fisheries' | 'Animal Husbandry';
  description: string;
  eligibility: string[];
  link: string;
}

export interface LoanProduct {
  id: string;
  name: string;
  type: 'Bank' | 'Govt' | 'Microfinance' | 'FinTech';
  description: string;
  benefits: string[];
  eligibility: string[];
  link: string;
}

export interface LoanAdvisorResult {
  eligibilityLevel: 'High' | 'Medium' | 'Low';
  recommendedLoan: string;
  amountRange: string;
  estimatedInterestRange: string;
  documents: string[];
  nextSteps: string[];
  riskNotes: string[];
}

export interface Trader {
  id: string;
  name: string;
  location: string;
  dealsIn: string[];
  phone: string;
  rating: number;
  reviewCount: number;
  imageUrl?: string;
}

export interface MarketPrice {
  crop: string;
  market: string;
  state: string;
  minPrice: number;
  maxPrice: number;
  modalPrice: number;
  lastUpdated: string;
  trend: 'up' | 'down' | 'stable';
}

export interface SensorData {
  temperature: number;
  humidity: number;
  moisture: number;
  ph: number;
  timestamp: string;
}
