import User from '../models/User.js';
import jwt from 'jsonwebtoken';

const generateToken = (id) => {
  return jwt.sign({ id }, process.env.JWT_SECRET, { expiresIn: '30d' });
};

function sanitizeUserResponse(user) {
  return {
    _id: user._id,
    id: user._id,
    name: user.name,
    email: user.email,
    phone: user.phone || '',
    languagePreference: user.languagePreference || 'en',
    farmName: user.farmName || '',
    state: user.state || '',
    district: user.district || '',
    city: user.city || '',
    village: user.village || '',
    country: user.country || '',
    postalCode: user.postalCode || '',
    latitude: user.latitude ?? null,
    longitude: user.longitude ?? null,
    locationSource: user.locationSource || '',
    locationUpdatedAt: user.locationUpdatedAt ? new Date(user.locationUpdatedAt).toISOString() : null,
    farmSizeAcres: user.farmSizeAcres ?? null,
    soilType: user.soilType || '',
    irrigationType: user.irrigationType || '',
    primaryCrops: Array.isArray(user.primaryCrops) ? user.primaryCrops : [],
  };
}

export const registerUser = async (req, res) => {
  try {
    const { name, email, password, languagePreference, state, district, farmSizeAcres, soilType, irrigationType } = req.body;

    if (!name || !email || !password) {
      return res.status(400).json({ error: 'Please provide all required fields' });
    }

    const userExists = await User.findOne({ email });
    if (userExists) {
      return res.status(400).json({ error: 'User already exists' });
    }

    const user = await User.create({
      name,
      email,
      password,
      languagePreference: languagePreference || 'en',
      state: state || '',
      district: district || '',
      farmSizeAcres: farmSizeAcres ?? null,
      soilType: soilType || '',
      irrigationType: irrigationType || '',
    });

    if (user) {
      res.status(201).json({
        ...sanitizeUserResponse(user),
        token: generateToken(user._id),
      });
    } else {
      res.status(400).json({ error: 'Invalid user data' });
    }
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

export const loginUser = async (req, res) => {
  try {
    const { email, password } = req.body;
    const user = await User.findOne({ email });

    if (user && (await user.matchPassword(password))) {
      res.json({
        ...sanitizeUserResponse(user),
        token: generateToken(user._id),
      });
    } else {
      res.status(401).json({ error: 'Invalid email or password' });
    }
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};

export const getMe = async (req, res) => {
  res.json(req.user);
};

export const updateProfile = async (req, res) => {
  try {
    const allowedFields = [
      'name',
      'phone',
      'languagePreference',
      'farmName',
      'state',
      'district',
      'city',
      'village',
      'country',
      'postalCode',
      'latitude',
      'longitude',
      'locationSource',
      'locationUpdatedAt',
      'farmSizeAcres',
      'soilType',
      'irrigationType',
      'primaryCrops',
    ];
    const updates = {};
    for (const field of allowedFields) {
      if (req.body[field] !== undefined) {
        updates[field] = req.body[field];
      }
    }

    const updated = await User.findByIdAndUpdate(
      req.user._id,
      { $set: updates },
      { new: true, runValidators: true },
    ).select('-password');

    if (!updated) {
      return res.status(404).json({ error: 'User not found' });
    }
    res.json(sanitizeUserResponse(updated));
  } catch (error) {
    res.status(500).json({ error: error.message });
  }
};
