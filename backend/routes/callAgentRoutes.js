import express from 'express';
import {
  getCallAgentContract,
  verifyCallAgentAuth,
  validateToolPayload,
  dispatchCallAgentTool,
} from '../services/callAgentService.js';

const router = express.Router();

/**
 * Integration contract discovery endpoint for Sarvam & external voice/telephony call agents.
 * Exposes schemas, auth headers, and config flags without leaking secrets.
 */
router.get('/contract', (_req, res) => {
  res.json(getCallAgentContract());
});

async function handleToolWebhook(req, res, explicitTool = null) {
  const auth = verifyCallAgentAuth(req);
  if (!auth.ok) {
    return res.status(auth.statusCode).json({
      error: auth.error,
      code: auth.code,
      serviceStatus: auth.code,
    });
  }

  const parsed = validateToolPayload(req.body, explicitTool);
  if (parsed.error) {
    return res.status(400).json({ error: parsed.error, code: 'validation_error' });
  }

  try {
    const result = await dispatchCallAgentTool(parsed);
    return res.json({
      authMethod: auth.authMethod,
      ...result,
    });
  } catch (error) {
    console.error('[CallAgent] Tool execution error:', error);
    return res.status(500).json({ error: 'Call-agent tool execution failed', code: 'internal_error' });
  }
}

router.post('/webhook', (req, res) => handleToolWebhook(req, res, null));
router.post('/tools/:toolName', (req, res) => handleToolWebhook(req, res, req.params.toolName));

export default router;
