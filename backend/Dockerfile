FROM node:22-alpine

RUN apk add --no-cache curl tini ca-certificates

WORKDIR /app

# Install production Node dependencies cleanly without dev tools (mongodb-memory-server, nodemon)
COPY package.json package-lock.json ./
RUN npm install --omit=dev --no-audit --no-fund && npm cache clean --force

# Copy backend source without local secrets, uploads, logs, or databases (.dockerignore)
COPY --chown=node:node . .

RUN mkdir -p /app/uploads /app/logs && chown -R node:node /app

ENV NODE_ENV=production
ENV PORT=5000
ENV HOST=0.0.0.0

USER node

EXPOSE 5000

HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=3 \
  CMD curl -fsS http://127.0.0.1:5000/health >/dev/null || exit 1

ENTRYPOINT ["/sbin/tini", "--"]
CMD ["node", "server.js"]
