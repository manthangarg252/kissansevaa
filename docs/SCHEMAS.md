# Backend Response Schemas

Reference document for Android data class authoring. Every JSON shape here corresponds to a `@Serializable` data class in `core/model/Models.kt`.

## Auth

### POST /api/auth/login | /api/auth/register
```json
{
  "_id": "string",
  "name": "string",
  "email": "string",
  "token": "jwt_string"
}
```

### GET /api/auth/me
```json
{
  "_id": "string",
  "name": "string",
  "email": "string",
  "phone": "string|null",
  "languagePreference": "en"
}
```

## Crop Disease

### POST /api/crop/analyze (multipart: image, location, soilType, language)
```json
{
  "_id": "string",
  "source": "gemini|ml-service",
  "cropName": "string|null",
  "diseaseName": "string",
  "confidence": 0.95,
  "severity": "high|medium|low",
  "summary": "string|null",
  "treatment": {
    "organic": ["string"],
    "chemical": ["string"]
  },
  "prevention": ["string"],
  "language": "en",
  "imagePath": "string|null",
  "createdAt": "ISO8601"
}
```

### GET /api/crop/history
Array of the same crop analysis objects.

## Livestock

### POST /api/livestock/analyze (multipart: image, animalType, language)
```json
{
  "conditionName": "string|null",
  "confidence": "string|null",
  "symptoms": ["string"],
  "immediateAction": "string|null",
  "longTermCare": "string|null",
  "vetConsultationAdvice": "string|null",
  "imageUrl": "string|null"
}
```

## Chat

### POST /api/chat
**Request:**
```json
{ "message": "string", "language": "en", "mode": "string|null" }
```
**Response:**
```json
{ "response": "string" }
```

## Market

### GET /api/market?crop=&state=
```json
[
  {
    "_id": "string",
    "crop": "string",
    "price": 2500.0,
    "modalPrice": 2500.0,
    "minPrice": 2200.0,
    "maxPrice": 2700.0,
    "unit": "quintal",
    "market": "string",
    "state": "string",
    "trend": "up|down|stable",
    "updatedAt": "ISO8601",
    "lastUpdated": "ISO8601"
  }
]
```

### POST /api/market/recommendations
**Request:**
```json
{
  "location": "string",
  "soilType": "string",
  "farmSize": "string",
  "currentCrop": "string",
  "irrigation": "string",
  "language": "en"
}
```
**Response:**
```json
{ "response": "string" }
```

## Schemes

### POST /api/schemes/recommendations
**Request:**
```json
{
  "state": "string",
  "farmSize": "string",
  "crop": "string",
  "soilType": "string",
  "landType": "string",
  "language": "en"
}
```
**Response:**
```json
{ "response": "string" }
```

## Loans

### POST /api/loans/advisor
**Request:**
```json
{
  "profile": {
    "name": "string",
    "age": "string",
    "farmSize": "string",
    "annualIncome": "string",
    "existingLoans": "string",
    "creditScore": "string",
    "cropType": "string",
    "state": "string"
  },
  "language": "en"
}
```
**Response:**
```json
{
  "recommendedLoan": "string|null",
  "emiEstimate": "string|null",
  "riskAdvisory": "string|null",
  "response": "string|null"
}
```

## Traders

### GET /api/traders?location=&search=
```json
[
  {
    "_id": "string",
    "name": "string",
    "location": "string",
    "dealsIn": ["string"],
    "phone": "string",
    "rating": 4.5,
    "reviewCount": 12
  }
]
```

### POST /api/traders/advisory (NEW)
**Request:**
```json
{ "query": "string", "language": "en" }
```
**Response:**
```json
{ "response": "string" }
```

## IoT

### GET /api/iot/live
```json
{
  "temperature": 28.5,
  "humidity": 65.0,
  "soilMoisture": 42.0,
  "phLevel": 6.8,
  "timestamp": "ISO8601"
}
```

### POST /api/iot/insights (NEW)
**Request:**
```json
{
  "sensorData": {
    "temperature": 28.5,
    "humidity": 65.0,
    "soilMoisture": 42.0,
    "phLevel": 6.8
  },
  "language": "en"
}
```
**Response:**
```json
{ "response": "string" }
```

## Carbon

### POST /api/carbon/estimate
**Request:**
```json
{
  "farmSize": "string",
  "cropType": "string",
  "method": "string",
  "irrigation": "string",
  "language": "en"
}
```
**Response:**
```json
{ "response": "string" }
```

## Field Extraction (NEW)

### POST /api/extract-fields
**Request:**
```json
{
  "text": "spoken text string",
  "fields": ["location", "soilType", "cropName"],
  "language": "en"
}
```
**Response:**
```json
{
  "extractedFields": {
    "location": "value|null",
    "soilType": "value|null",
    "cropName": "value|null"
  }
}
```

## Error Responses
All endpoints return on error:
```json
{ "error": "description string" }
```
HTTP status codes: 400 (bad request), 401 (unauthorized), 500 (server error).
