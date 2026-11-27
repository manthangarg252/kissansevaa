FROM python:3.11-slim-bookworm

RUN apt-get update && apt-get install -y --no-install-recommends \
    libgomp1 \
    curl \
    tini \
    ca-certificates \
  && rm -rf /var/lib/apt/lists/*

RUN addgroup --system --gid 10001 mluser \
  && adduser --system --uid 10001 --gid 10001 --home /home/mluser mluser

WORKDIR /app

COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

COPY --chown=mluser:mluser app.py v3_svm_adapter.py ./

RUN mkdir -p \
    /app/logs \
    /app/model_assets \
    /app/model_candidates/v3_svm_repaired/model_assets \
  && chown -R mluser:mluser /app

ENV PYTHONUNBUFFERED=1
ENV PYTHONDONTWRITEBYTECODE=1
ENV ACTIVE_CROP_MODEL=v1
ENV ENABLE_V3_SHADOW=true
ENV V3_LOAD_FULL_FAISS=true
ENV CROP_MODEL_DIR=/app/model_assets
ENV V3_CANDIDATE_DIR=/app/model_candidates/v3_svm_repaired/model_assets
ENV SHADOW_LOG_PATH=/app/logs/shadow_comparison.jsonl

USER mluser

EXPOSE 8000

HEALTHCHECK --interval=20s --timeout=5s --start-period=45s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8000/health >/dev/null || exit 1

ENTRYPOINT ["/usr/bin/tini", "--"]
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "1", "--proxy-headers"]
