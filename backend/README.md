
# KissanSevaAI Backend

Production-ready Express.js backend for KissanSevaAI.

## Tech Stack
- Node.js & Express
- Local JWT Authentication
- MongoDB (Mongoose)
- Google Gemini API
- Multer (Local File Storage)

## Setup Instructions

1.  **Clone & Install**
    ```bash
    cd backend
    npm install
    ```

2.  **Environment Variables**
    Create a `.env` file based on `.env.example`. You will need:
    - `API_KEY`: Google Gemini API Key.
    - `MONGO_URI`: Your MongoDB connection string.
    - `JWT_SECRET`: A secure string for signing tokens.

3.  **Run the Server**
    ```bash
    npm run dev
    ```

4.  **Run IoT Simulator**
    ```bash
    node scripts/iotSimulator.js
    ```

## API Endpoints

- `POST /api/auth/register`: Register a new account.
- `POST /api/auth/login`: Login and receive JWT.
- `POST /api/crop/analyze`: Multi-part form with `image`, `location`, `soilType`. (Requires JWT)
- `POST /api/chat`: JSON with `message`, `language`, `mode`. (Requires JWT)
- `GET /api/iot/live`: Get latest sensor data.
- `GET /api/market`: Get mandi prices.
- `POST /api/loans/advisor`: JSON with farmer profile. (Requires JWT)
