FROM node:22-bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends flac ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev --registry=https://registry.npmmirror.com --no-audit --no-fund
COPY src ./src
COPY web ./web
RUN mkdir -p /data/config
EXPOSE 7800
ENV NODE_ENV=production
CMD ["node", "--experimental-sqlite", "src/index.js"]
