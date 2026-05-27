# Jalf API Endpoints Reference

Base URL: `https://m-app.jalf.com`
Photo CDN: `https://photos2.jalf.com`
App host: `https://app.jalf.com`

---

## Detailed Documentation
For field-level schemas and exhaustive documentation, see the following:
- [Metadata](docs/rest/metadata.md) - Collections (Sexes, Orientations, etc.)
- [Users](docs/rest/users.md) - Profiles, status, and options
- [Conversations](docs/rest/conversations.md) - Chat threads and messages
- [Server-Sent Events (SSE)](docs/rest/sse.md) - Real-time notifications and updates
- [Media](docs/rest/media.md) - Photos and videos
- [Legacy HTML Endpoints](docs/rest/legacy.md): Traditional endpoints returning HTML.
- [HTML Scraping Strategy](docs/scraping.md): Detailed CSS selectors and enrichment logic.

---

## Authentication
## keb = quebec french
## eng = english
## itl = italian
## esp = spanish
## alm = german

### POST `/ct/connect?Lang={keb|eng|itl|esp|alm}`
Login with credentials.
- **Body**: form-encoded `username=...&password=...`
- **Response**: HTML page containing SUID cookie and `/rest/users/{userId}` link
- **Used in**: `LoginActivity`

### GET `/ct/accueil`
Session validation / re-auth check.
- **Used in**: `LoginActivity`

### POST `/ct/connect` (re-login)
Background session refresh.
- **Used in**: `JalfNotificationWorker`

---

## Current User

### GET `/rest/users/{userId}`
Fetch own or any user's full profile.
- **Detailed Schema**: [users.md](docs/rest/users.md#1-user-profile)
- **Used in**: `MainActivity` (own profile → toolbar avatar + profile sheet), per-favorite/event profile enrichment

### GET `/rest/users/{userId}/favorites`
Fetch list of favourite users.
- **Detailed Schema**: [users.md](docs/rest/users.md#3-favorites)
- **Used in**: `MainActivity`, `JalfNotificationWorker`, `SettingsActivity`

---

## Conversations (Chats)

### GET `/rest/users/{userId}/conversations/{folder}?offset={n}&count=20`
Fetch conversation threads.
- `{folder}` can be `new`, `active`, or `archived`.
- **Detailed Schema**: [conversations.md](docs/rest/conversations.md#1-list-conversations)
- **Used in**: `MainActivity` (chat list), `JalfNotificationWorker`

---

## Events (Notifications)

### GET `/rest/users/{userId}/notifications?offset=0&count=50`
User activity feed (views, pokes, etc.)
- **Detailed Schema**: [users.md](docs/rest/users.md#4-notifications-feed)
- **Used in**: `MainActivity`, `JalfNotificationWorker`

---

## Reference Data (Metadata)

### GET `/rest/sexes`
All sex categories with icons.
- **Detailed Schema**: [metadata.md](docs/rest/metadata.md#11-sexes--genders)
- **ID Reference**:
  | ID | Description | Icon |
  |----|-------------|------|
  | SEXID | Homme | `avatarH.png` / `ic_sex_1_20.png` |
  | SEXID | Femme | `avatarF.png` / `ic_sex_2_20.png` |
  | SEXID | Couple | `avatarC.png` / `ic_sex_4_20.png` |
  | SEXID | Travesti | `avatarTrav.png` / `ic_sex_8_20.png` |
  | SEXID | Trans | `avatarTrans.png` / `ic_sex_16_20.png` |
  | SEXID | Couple de femmes | `avatarCF.png` / `ic_sex_32_20.png` |
  | SEXID | Couple d'hommes | `avatarCH.png` / `ic_sex_64_20.png` |
- **Used in**: `MainActivity`, `JalfNotificationWorker`, `SettingsActivity`

---

## Dynamic URLs (parsed from response bodies)

| Field | Example Pattern | Description |
|-------|---------|-------------|
| `user_link` | `/rest/users/USERID` | Resolve full user profile |
| `conversation_link` | `/rest/users/USERID/conversations/CONVERSATIONID` | Open a specific conversation |
| `notification_link` | `/rest/notifications/NOTIFICATIONID` | Mark notification as read/deleted |
| `photo_link` | `/rest/photos/PHOTOID` | Full photo resource |
| `sex_link` | `/rest/sexes/SEXID` | Sex category reference |
| `user_photos_albums_link` | `/rest/users/USERID/photos/albums` | User's photo albums |
| `user_videos_albums_link` | `/rest/users/USERID/videos/albums` | User's video albums |

---

## Required Request Headers

```http
Cookie:      {FULL_COOKIE}
x-csrftoken: {SUID}
User-Agent:  Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Mobile Safari/537.36
```
