/** Example Expo / React Native screen. Merge into your EXISTING app, do not replace navigation. */
import React, { useState } from 'react';
import { View, Text, Button, ActivityIndicator, ScrollView, Alert, Image } from 'react-native';
import * as ImagePicker from 'expo-image-picker';

// On a physical phone use your computer's LAN IP or a HTTPS server, not 'localhost'.
const API_BASE = process.env.EXPO_PUBLIC_CROP_API_URL || 'http://192.168.1.10:8000';

type Match = { class_name: string; cosine_similarity: number };
type Prediction = {
  label: string;
  crop: string;
  condition: string;
  top_cosine_similarity: number;
  neighbor_agreement: number;
  needs_review: boolean;
  matches: Match[];
  warning: string;
};

export default function CropPredictionScreen() {
  const [photo, setPhoto] = useState<ImagePicker.ImagePickerAsset | null>(null);
  const [result, setResult] = useState<Prediction | null>(null);
  const [busy, setBusy] = useState(false);

  async function takeOrSelect(camera: boolean) {
    const permission = camera
      ? await ImagePicker.requestCameraPermissionsAsync()
      : await ImagePicker.requestMediaLibraryPermissionsAsync();
    if (!permission.granted) {
      Alert.alert('Permission needed', 'Allow access to the camera or photos.');
      return;
    }
    const response = camera
      ? await ImagePicker.launchCameraAsync({ mediaTypes: ['images'], quality: 0.85 })
      : await ImagePicker.launchImageLibraryAsync({ mediaTypes: ['images'], quality: 0.85 });
    if (!response.canceled && response.assets.length) {
      setPhoto(response.assets[0]);
      setResult(null);
    }
  }

  async function analyze() {
    if (!photo) return;
    setBusy(true);
    setResult(null);
    try {
      const data = new FormData();
      data.append('file', {
        uri: photo.uri,
        name: photo.fileName || 'leaf.jpg',
        type: photo.mimeType || 'image/jpeg',
      } as any);
      const response = await fetch(`${API_BASE}/api/crop/predict`, {
        method: 'POST', body: data,
        // Do NOT set Content-Type manually; React Native adds the multipart boundary.
      });
      if (!response.ok) {
        const message = await response.text();
        throw new Error(`HTTP ${response.status}: ${message}`);
      }
      setResult((await response.json()) as Prediction);
    } catch (error) {
      Alert.alert('Prediction unavailable', String(error));
    } finally {
      setBusy(false);
    }
  }

  return (
    <ScrollView contentContainerStyle={{ padding: 20, gap: 14 }}>
      <Text style={{ fontSize: 23, fontWeight: '700' }}>Crop Disease Detection</Text>
      <Button title="Take leaf photo" onPress={() => takeOrSelect(true)} />
      <Button title="Choose from gallery" onPress={() => takeOrSelect(false)} />
      {photo && <Image source={{ uri: photo.uri }} style={{ width: '100%', height: 260 }} resizeMode="contain" />}
      <Button title="Analyze image" onPress={analyze} disabled={!photo || busy} />
      {busy && <ActivityIndicator size="large" />}
      {result && (
        <View style={{ gap: 8 }}>
          <Text style={{ fontSize: 18, fontWeight: '600' }}>
            {result.needs_review ? 'Uncertain match — review needed' : 'Possible match'}: {result.label}
          </Text>
          <Text>Nearest-image cosine similarity: {result.top_cosine_similarity.toFixed(3)}</Text>
          <Text>Neighbor agreement: {(result.neighbor_agreement * 100).toFixed(1)}% (not medical confidence)</Text>
          <Text style={{ fontWeight: '600' }}>Nearest reference classes:</Text>
          {result.matches.slice(0, 5).map((m, i) => (
            <Text key={`${i}-${m.class_name}`}>{i + 1}. {m.class_name} — {m.cosine_similarity.toFixed(3)}</Text>
          ))}
          <Text>{result.warning}</Text>
        </View>
      )}
    </ScrollView>
  );
}
