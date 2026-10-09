# KissanSevaAI 🌾

<div align="center">
  
  **Smart Farming Platform powered by AI**
  
  [![Made with React](https://img.shields.io/badge/Made%20with-React-61DAFB?style=flat&logo=react)](https://reactjs.org/)
  [![Powered by Gemini](https://img.shields.io/badge/Powered%20by-Gemini%20AI-4285F4?style=flat&logo=google)](https://ai.google.dev/)
  [![TypeScript](https://img.shields.io/badge/TypeScript-5.8-3178C6?style=flat&logo=typescript)](https://www.typescriptlang.org/)
  [![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)
</div>

---

## 📋 Table of Contents

- [About](#about)
- [Features](#features)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Project Structure](#project-structure)
- [Multilingual Support](#multilingual-support)
- [API Reference](#api-reference)
- [Contributing](#contributing)
- [License](#license)

---

## 🌟 About

**KissanSevaAI** is a comprehensive smart farming platform designed to empower farmers with AI-driven insights and tools. From crop disease detection to market intelligence, loan advisory, and carbon credit estimation, KissanSevaAI brings modern technology to agriculture in an accessible, multilingual interface.

### Why KissanSevaAI?

- 🤖 **AI-Powered Analysis** - Advanced crop disease detection and livestock health monitoring
- 🌍 **Multilingual** - Available in English, Hindi, Marathi, Punjabi, and Sanskrit
- 📊 **Real-time Insights** - Live market prices, IoT sensor monitoring, and trends
- 💰 **Financial Tools** - Loan recommendations, EMI calculators, and eligibility checks
- 🌱 **Sustainability** - Carbon credit estimation and organic farming guidance
- 🔍 **Market Intelligence** - Trader directories and comprehensive market analysis

---

## ✨ Features

### 🔬 **Crop Disease Detection**
- Upload crop images for instant AI analysis
- Identify diseases with confidence scores
- Get organic and chemical treatment recommendations
- Prevention strategies and severity assessment

### 🐄 **Livestock Care**
- Health condition analysis from images
- Symptom identification
- Treatment recommendations
- Veterinary advice

### 📈 **Market Insights**
- Real-time market price tracking
- Trend analysis (up/down/stable indicators)
- State-wise price comparisons
- SWOT analysis for farm strategies

### 🌡️ **IoT Monitoring**
- Real-time sensor data visualization
- Temperature, humidity, soil moisture, pH tracking
- Historical trends and patterns
- AI-powered insights and recommendations

### 🏛️ **Government Schemes**
- Comprehensive scheme directory
- Eligibility checking
- AI-powered recommendations
- Direct links to official portals

### 🤝 **Traders & Buyers Directory**
- Verified trader listings
- Location-based search
- Ratings and reviews
- Direct contact information

### 💳 **Loans & Credit Hub**
- Multiple loan product comparisons
- EMI calculator
- AI-powered eligibility assessment
- Document requirement lists
- Government and private loan options

### 🌿 **Carbon Credits**
- Carbon footprint estimation
- Sustainable practice recommendations
- Credit calculation based on farm practices

### 💬 **AI Chatbot Assistant**
- Context-aware farming advice
- Multilingual support
- Multiple modes (General, Crop, Livestock, Market, etc.)
- Voice input capability

---

## 🛠️ Tech Stack

### Frontend
- **React 19.2** - UI framework
- **TypeScript 5.8** - Type safety
- **React Router 7** - Navigation
- **Tailwind CSS** - Styling
- **Lucide React** - Icons
- **i18next** - Internationalization

### AI & Backend
- **Google Gemini AI (Flash 3)** - AI processing
- **Node.js** - Server runtime
- **Express.js** - API framework
- **MongoDB** - Database
- **Mongoose** - ODM

### Authentication & Security
- **JWT** - Token-based authentication
- **bcryptjs** - Password hashing

### Development Tools
- **Vite 6** - Build tool
- **ESBuild** - Fast bundler

---

## 🚀 Getting Started

### Prerequisites

- Node.js >= 18.0.0
- npm >= 8.0.0
- Gemini API Key ([Get one here](https://ai.google.dev/))

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/kissansevaai.git
   cd kissansevaai
   ```

2. **Install dependencies**
   ```bash
   npm install
   ```

3. **Configure environment variables**
   
   Create a `.env.local` file in the root directory:
   ```env
   GEMINI_API_KEY=your_gemini_api_key_here
   ```

4. **Run the development server**
   ```bash
   npm run dev
   ```

5. **Open your browser**
   
   Navigate to `http://localhost:3000`

### Build for Production

```bash
npm run build
npm run preview
```

---

## 📁 Project Structure

```
kissansevaai/
├── components/           # Reusable React components
│   └── Layout.tsx       # Main layout wrapper
├── pages/               # Page components
│   ├── Dashboard.tsx    # Main dashboard
│   ├── CropDisease.tsx  # Crop disease detection
│   ├── Livestock.tsx    # Livestock health
│   ├── MarketInsights.tsx
│   ├── IoTMonitoring.tsx
│   ├── GovernmentSchemes.tsx
│   ├── TradersDirectory.tsx
│   ├── CarbonCredits.tsx
│   ├── LiveMarketPrices.tsx
│   ├── LoansPage.tsx
│   ├── Chatbot.tsx
│   ├── Login.tsx
│   └── Signup.tsx
├── locales/             # Translation files
│   ├── en.ts           # English
│   ├── hi.ts           # Hindi
│   ├── mr.ts           # Marathi
│   ├── pa.ts           # Punjabi
│   └── sa.ts           # Sanskrit
├── constants.tsx        # App constants & data
├── geminiService.ts     # Gemini AI integration
├── types.ts             # TypeScript types
├── i18n.ts              # i18n configuration
├── index.tsx            # App entry point
└── index.html           # HTML template
```

---

## 🌐 Multilingual Support

KissanSevaAI supports **5 languages**:

| Language | Code | Script |
|----------|------|--------|
| English | `en` | Latin |
| Hindi | `hi` | Devanagari |
| Marathi | `mr` | Devanagari |
| Punjabi | `pa` | Gurmukhi |
| Sanskrit | `sa` | Devanagari |

### Switching Languages

Use the language selector in the header to switch between languages. All content, including AI responses, will be delivered in the selected language.

---

## 🔌 API Reference

### Gemini AI Service

#### Crop Disease Analysis
```typescript
analyzeCropDisease(
  imageBase64: string,
  location: string,
  soilType: string,
  language: Language
): Promise<CropDiseaseResult>
```

**Returns:**
```typescript
{
  cropName: string;
  diseaseName: string;
  confidence: number;
  summary: string;
  severity: "Low" | "Medium" | "High";
  treatment: {
    organic: string[];
    chemical: string[];
  };
  prevention: string[];
  source: "gemini";
}
```

#### Livestock Analysis
```typescript
analyzeLivestockDisease(
  imageBase64: string,
  animalType: string,
  language: Language
): Promise<LivestockResult>
```

**Returns:**
```typescript
{
  conditionName: string;
  confidence: string;
  summary: string;
  symptoms: string[];
  treatment: string[];
  prevention: string[];
  vetAdvice: string;
}
```

#### Market Recommendations
```typescript
getMarketRecommendations(
  farmData: object,
  language: Language
): Promise<string>
```

#### Loan Advisory
```typescript
getLoanAdvice(
  profileData: object,
  language: Language
): Promise<LoanAdvisorResult>
```

**Returns:**
```typescript
{
  eligibilityLevel: "High" | "Medium" | "Low";
  recommendedLoan: string;
  amountRange: string;
  estimatedInterestRange: string;
  documents: string[];
  nextSteps: string[];
  riskNotes: string[];
}
```

#### Carbon Credit Estimation
```typescript
estimateCarbonCredits(
  farmData: object,
  language: Language
): Promise<string>
```

#### Voice Field Extraction
```typescript
extractFieldsFromSpeech(
  text: string,
  fields: string[],
  language: Language
): Promise<object>
```

---

## 🎨 Key Components

### Dashboard
The main landing page showcasing all available features with quick access cards.

### Crop Disease Detection
- Image upload with preview
- AI-powered disease identification
- Treatment recommendations (organic & chemical)
- Prevention strategies
- Confidence scoring

### Livestock Care
- Animal health monitoring
- Symptom analysis
- Treatment plans
- Veterinary advice integration

### IoT Monitoring
- Real-time sensor data visualization
- Historical data trends
- Alerts and notifications
- AI-powered insights

### Market Insights
- Live price tracking
- Trend analysis
- SWOT analysis generation
- Market recommendations

### Loans & Credit Hub
- Loan product comparison
- EMI calculator
- AI-powered eligibility checker
- Document checklist

### Government Schemes
- Searchable scheme database
- Category filtering
- Eligibility checking
- Direct links to official portals

---

## 🤝 Contributing

We welcome contributions! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Code Standards

- Use TypeScript for type safety
- Follow the existing code style
- Write meaningful commit messages
- Add comments for complex logic
- Test your changes thoroughly

### Development Guidelines

- All AI responses must support multilingual output
- Follow the existing component structure
- Use Tailwind utility classes for styling
- Ensure mobile responsiveness
- Handle loading and error states properly

---

## 🐛 Known Issues

- Voice input may not work on all browsers (Chrome recommended)
- Image upload limited to 5MB per file
- IoT sensor data is simulated for demo purposes

---

## 📝 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 🙏 Acknowledgments

- **Google Gemini AI** for powerful AI capabilities
- **React Team** for the amazing framework
- **Tailwind CSS** for utility-first styling
- **Lucide** for beautiful icons
- **i18next** for internationalization support
- All farmers who inspired this project

---

## 📞 Support

For support, email support@kissansevaai.com or create an issue in the repository.

---

## 🗺️ Roadmap

- [ ] Mobile app (React Native)
- [ ] Offline mode support
- [ ] Weather integration
- [ ] Pest prediction model
- [ ] Community forum
- [ ] Video tutorials
- [ ] AR crop scanning
- [ ] Blockchain for carbon credits
- [ ] SMS alerts for market prices
- [ ] Integration with government databases

---

## 📊 Project Statistics

- **Total Components:** 15+
- **Supported Languages:** 5
- **AI Models:** Gemini 3 Flash
- **Lines of Code:** 5,000+
- **API Endpoints:** 10+

---

## 🔐 Security

- JWT-based authentication
- Password hashing with bcryptjs
- Environment variable protection
- Input validation and sanitization
- CORS configuration

---

## 🌍 Deployment

### Deploy to Vercel

[![Deploy with Vercel](https://vercel.com/button)](https://vercel.com/new/clone?repository-url=https://github.com/yourusername/kissansevaai)

### Deploy to Netlify

[![Deploy to Netlify](https://www.netlify.com/img/deploy/button.svg)](https://app.netlify.com/start/deploy?repository=https://github.com/yourusername/kissansevaai)

---

<div align="center">
  
  **Made with ❤️ for Farmers**
  
  [Website](https://kissansevaai.com) • [Documentation](https://docs.kissansevaai.com) • [Report Bug](https://github.com/yourusername/kissansevaai/issues) • [Request Feature](https://github.com/yourusername/kissansevaai/issues)
  
  ---
  
  ⭐ Star this repository if you find it helpful!
  
</div>
