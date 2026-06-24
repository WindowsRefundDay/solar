# Reach Directory (Cloudflare Worker)

Registers Soulseek usernames from Reach clients on Y1/Y2 so users can discover each other.

## Setup

1. Install dependencies: `npm install`
2. Create a KV namespace: `npx wrangler kv:namespace create REACH_USERS`
3. Copy the namespace id into `wrangler.toml`
4. (Optional) Set `REGISTRY_TOKEN` in `wrangler.toml` `[vars]` and the same value in the Android app `REACH_DIRECTORY_TOKEN` Gradle property
5. Deploy: `npm run deploy`

## API

### `POST /v1/register`

```json
{ "username": "Y1-plume-wave-42", "device": "Y1" }
```

Updates `lastSeen`. Entries not seen for **24 hours** are removed automatically.

### `GET /v1/users?exclude=MyUsername`

Returns active Reach users (newest first). Soulseek online status is resolved on the client via `watchUser`.

### `GET /v1/users/:username`

Returns whether a username is an active Reach user.

## Android

Set `REACH_DIRECTORY_URL` in `gradle.properties` (or leave the default in `app/build.gradle` after you deploy):

```
REACH_DIRECTORY_URL=https://reach-directory.<your-subdomain>.workers.dev
```

The app registers only after a **successful Soulseek login** (`onConnected`).
