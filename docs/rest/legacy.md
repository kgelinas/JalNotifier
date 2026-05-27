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

