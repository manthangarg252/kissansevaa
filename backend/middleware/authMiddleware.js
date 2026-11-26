import jwt from 'jsonwebtoken';
import User from '../models/User.js';

/**
 * Middleware to protect routes using JWT
 */
export const protect = async (req, res, next) => {
  let token;

  if (req.headers.authorization && req.headers.authorization.startsWith('Bearer')) {
    try {
      token = req.headers.authorization.split(' ')[1];
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      req.user = await User.findById(decoded.id).select('-password');

      if (!req.user) {
        return res.status(401).json({ error: 'User not found, authorization denied' });
      }

      return next();
    } catch (error) {
      console.error('Auth Middleware Error:', error);
      return res.status(401).json({ error: 'Not authorized, token failed' });
    }
  }

  return res.status(401).json({ error: 'Not authorized, no token provided' });
};

/**
 * Optional auth middleware for public-or-personalized endpoints (e.g. /api/weather/forecast, /api/schemes).
 * Attaches req.user when a valid Bearer token is present; never blocks unauthenticated callers.
 */
export const optionalProtect = async (req, _res, next) => {
  if (req.headers.authorization && req.headers.authorization.startsWith('Bearer')) {
    try {
      const token = req.headers.authorization.split(' ')[1];
      const decoded = jwt.verify(token, process.env.JWT_SECRET);
      req.user = await User.findById(decoded.id).select('-password');
    } catch {
      req.user = null;
    }
  }
  return next();
};
