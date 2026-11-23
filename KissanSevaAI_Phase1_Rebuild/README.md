# KissanSevaAI Phase 1: crop disease detection rebuild

This package rebuilds the crop-image similarity engine from scratch and provides a **server-side integration path** for an existing Expo/React Native app. You must connect it to your own repository; the package cannot automatically edit an app that has not been supplied.

## Steps

1. Open the `KissanSevaAI_Phase1_Rebuild_Colab.ipynb` notebook in **Google Colab** (Runtime → T4 GPU).
2. Upload the Kaggle ZIP (`Plant-Diseases-100k-Labelled-Images`) to Google Drive as `MyDrive/archive.zip`, or edit `ZIP_PATH`.
3. Run all notebook cells in `MODE='pilot'`; inspect validation macro-F1, held-out test metrics, and the ONNX equivalence check.
4. Set `MODE='full'` in Cell 2 and rerun from the configuration cell onward. Outputs go to separate Drive folders to prevent pilot/full index confusion. The notebook uses a chunk cache and `num_workers=0` to avoid the previous DataLoader worker crash.
5. From `MyDrive/KissanSevaAI_Phase1/full/`, download:
   - `dinov2_vits14_cls_mean.onnx`
   - `crop_index.faiss`
   - `train_labels.npy`
   - `classes.json`
   - `model_meta.json`
6. Place those files in `backend/model_assets/` locally (create the directory).
7. Open terminal inside `backend/` and run:

   ```bash
   python -m venv .venv
   source .venv/bin/activate  # On Windows: .venv\\Scripts\\activate
   pip install -r requirements.txt
   uvicorn app:app --host 0.0.0.0 --port 8000
   ```

8. Open `http://127.0.0.1:8000/health`. It must show `ok: true`, class count and train-reference count.
9. Test a real image with:

   ```bash
   curl -X POST -F 'file=@/path/to/leaf.jpg' http://127.0.0.1:8000/api/crop/predict
   ```

10. In your existing React Native project:

    ```bash
    npx expo install expo-image-picker
    ```

    Copy `expo/CropPredictionScreen.tsx` into your app's screens, add it to your existing navigation, and set `EXPO_PUBLIC_CROP_API_URL=http://YOUR_COMPUTER_LAN_IP:8000` in `.env` for a physical phone on the same Wi-Fi. Restart Expo after changing `.env`. Use **HTTPS** for deployed apps. A physical Android phone cannot reach a development server using `localhost` (it points to the phone).

## Architecture

`Camera/gallery → React Native → POST /api/crop/predict → FastAPI → identical PIL preprocessing → DINOv2-S ONNX (768D features) → FAISS cosine index → similarity-weighted/majority vote → result`.

## Important correctness checks

- Do **not** pair the old `train_vectors.bin` with a newly exported ONNX encoder unless you can prove identical model, normalization, preprocessing and feature construction. Use the newly generated FAISS index with its matching ONNX encoder.
- The 768-D embedding here is **concatenated** DINOv2-S CLS (384D) and mean patch (384D), then L2 normalized.
- `IndexIVFFlat` is approximate; test accuracy depends on `nprobe`. Pilot uses an exact flat index.
- Split is 80/10/10 by image after removing byte-identical duplicates. Similar photos of the same physical leaf may still leak across partitions; independent field testing is necessary.
- Cosine similarity and `neighbor_agreement` are **not** calibrated confidence probabilities.
- An unknown plant, an animal photo or a poor-quality image might still receive a known label. Do not claim reliable no-leaf detection until you collect out-of-distribution images and test a rejection method.
- This API provides visual screening only. Do not issue medication/pesticide directions directly from a predicted class.
- To display actual reference images inside the app, add image IDs and licensed thumbnail URLs; the default response displays labels and scores only.
- Secure and rate-limit the public API; configure appropriate authentication before deployment.

## If accuracy is insufficient

1. Review per-class precision/recall, not just overall accuracy.
2. Collect independent field images, inspect mislabeled examples, near-duplicates and class imbalance.
3. Increase gallery coverage, test CLS-only vs concatenated features and resolution settings (regenerate vectors after every such change).
4. Compare larger DINOv2/DINOv3 encoders or fine-tuned supervised heads with the **same** held-out evaluation protocol.
5. Keep large image encoders and FAISS server-side first; on-device deployment can be engineered later.

## Sources

- Meta DINOv2 official repo: https://github.com/facebookresearch/dinov2
- FAISS metric guidance: https://github.com/facebookresearch/faiss/wiki/MetricType-and-distances
- ONNX Runtime Python: https://onnxruntime.ai/docs/get-started/with-python
- Expo ImagePicker: https://docs.expo.dev/versions/latest/sdk/imagepicker/
