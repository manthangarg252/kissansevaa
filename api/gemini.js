export default async function handler(req, res) {
  if (req.method !== "POST") {
    return res.status(405).json({ error: "Method not allowed" });
  }

  try {
    const {
      prompt,
      imageBase64,
      mimeType,
      systemInstruction,
      responseMimeType,
      generationConfig,
    } = req.body;

    if (!prompt && !imageBase64) {
      return res.status(400).json({ error: "Prompt or image is required" });
    }

    const apiKey = process.env.GEMINI_API_KEY;

    if (!apiKey) {
      return res.status(500).json({ error: "Missing GEMINI_API_KEY in server env" });
    }

    const MODEL = "gemini-1.5-flash";

    const url =
      `https://generativelanguage.googleapis.com/v1beta/models/${MODEL}:generateContent?key=${apiKey}`;

    const parts = [];

    // ✅ Image support
    if (imageBase64) {
      parts.push({
        inlineData: {
          mimeType: mimeType || "image/jpeg",
          data: imageBase64,
        },
      });
    }

    // ✅ Text support
    if (prompt) {
      parts.push({ text: prompt });
    }

    const bodyObj = {
      contents: [{ parts }],
    };

    // ✅ FIX: systemInstruction must be object format
    if (systemInstruction) {
      bodyObj.systemInstruction = {
        parts: [{ text: systemInstruction }],
      };
    }

    // ✅ Optional generation config (responseMimeType is not always supported reliably)
    if (generationConfig || responseMimeType) {
      bodyObj.generationConfig = {
        ...(generationConfig || {}),
        ...(responseMimeType ? { responseMimeType } : {}),
      };
    }

    const response = await fetch(url, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(bodyObj),
    });

    const data = await response.json();

    // ✅ If Gemini returns error
    if (!response.ok) {
      return res.status(response.status).json(data);
    }

    return res.status(200).json(data);
  } catch (err) {
    return res.status(500).json({ error: "Server error", details: err.message });
  }
}
