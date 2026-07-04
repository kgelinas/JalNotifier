# JALF REST API: Legacy HTML Endpoints

This document describes the legacy HTML-based endpoints used by the JALF Notifier application. These endpoints are primarily used when corresponding JSON-based REST endpoints are either unavailable or provide incomplete metadata.

> [!NOTE]
> The application is progressively migrating to JSON REST endpoints. Use of these legacy endpoints should be avoided for new feature development where possible.

## 1. Legacy Favorites List
**Endpoint**: `GET /ct/favorite/favorite_list`

Returns a list of bookmarked/favorite users in HTML format.

| Property | Value |
| :--- | :--- |
| **Format** | HTML |
| **Parser** | `ApiParser.parseLegacyFavoritesHtml` |
| **Scraping Tag** | `div.favorite_item`, `a.member_name` |

---

## 2. Online Bookmarks Status
**Endpoint**: `GET /ct/online/11`

Fetch the online status of all bookmarked contacts.

| Property | Value |
| :--- | :--- |
| **Format** | HTML |
| **Parser** | `MainActivity.fetchFavorites` |
| **Description** | Used to quickly detect which favorites are currently active. |

---

## 3. Web Photo Albums
**Endpoint**: `POST /ct/photoAlbum`

Fetch the user's public or private photo albums via the legacy HTML interface, which includes hidden metadata like photo IDs, like statuses, and realize statuses not present in the newer REST endpoints.

| Property | Value |
| :--- | :--- |
| **Format** | HTML |
| **Parameters** | `action=getAlbum`, `album=public` (or private), `of={userId}` |
| **Parser** | `ApiParser.parseWebPhotos` |
| **Scraping Tag** | `div.swiper-slide` attributes: `data-picid`, `data-iliked`, `data-irealized` |
| **Description** | Merged with REST API `/members/{id}/photos/albums` to attach interactive IDs to profile photos. |

---

## 4. Toggle Photo Like / Realize
**Endpoint**: `POST /ct/mediaLovers/pictures/{picId}`

Toggle the "Like" or "Realize Fantasy" state for a specific picture using the legacy HTML endpoints.

| Property | Value |
| :--- | :--- |
| **Format** | Form Data (No JSON Response) |
| **Parameters** | `action={add|del|add-fntsm|del-fntsm}`, `Pid={picId}` |
| **Description** | Toggles interactive states for photos discovered via `/ct/photoAlbum`. |
---

## 3. Profile Viewers (Lookers)
**Endpoint**: `GET /ct/online/3`

Returns the list of users who have recently viewed the authenticated user's profile.

| Property | Value |
| :--- | :--- |
| **Format** | HTML |
| **Parser** | `ApiParser.parseProfileViewersHtml` |
| **Scraping Tag** | `div.member_list`, `span.online_now` |

---

## 4. Pokes (Clin d'oeil)
**Endpoint**: `GET /ct/online/8`

Returns a list of "Clins d'oeil" (pokes) received by the user.

| Property | Value |
| :--- | :--- |
| **Format** | HTML |
| **Parser** | `ApiParser.parsePokesHtml` |
| **Scraping Tag** | `div.member_list`, `span.date` |

---

## 5. Visits Sent (Membres visités)
**Endpoint**: `GET /ct/online/6`

Returns a list of profiles that the authenticated user has recently viewed/visited.

| Property | Value |
| :--- | :--- |
| **Format** | HTML |
| **Parser** | `ApiParser.parseProfileViewersHtml` |
| **Description** | Outgoing visits counterpart to `/ct/online/3` (Profile Viewers). |

---

## 6. Pokes Sent (Clins d'oeil envoyés)
**Endpoint**: `GET /ct/online/10`

Returns a list of "Clins d'oeil" (pokes) sent by the authenticated user.

| Property | Value |
| :--- | :--- |
| **Format** | HTML |
| **Parser** | `ApiParser.parsePokeHtml` |
| **Description** | Outgoing pokes counterpart to `/ct/online/8` (Pokes received). |

