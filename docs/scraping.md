# JALF Scraping Strategy & Implementation

This document details the HTML scraping logic used in the JALF Notifier application. Scraping is used as a discovery mechanism for content that is not yet fully available via REST or to supplement REST data with fields strictly available in HTML (e.g., precise distance).

## 1. Overview: Hybrid Strategy

The application follows a **Scrape-first, Enrich-later** pattern:
1.  **Discovery (HTML)**: Fetch a legacy HTML page and parse basic user IDs and metadata.
2.  **Enrichment (REST)**: For each discovered user ID, trigger an asynchronous background fetch to the `/rest/users/{userId}` endpoint.
3.  **Refinement**: Merge the authoritative REST data (name, avatar, online status) with the unique HTML-only data (distance, city fallback).

### Pagination Quirks
Pagination on legacy HTML list endpoints (such as `/ct/online/11` and `/ct/online/2`) operates under a unique, non-standard mechanism:
1. **Initial Load (GET)**: The first page is fetched via a standard `GET` request. It returns the first 20 items inline and includes a hidden input `<input type="hidden" id="offset" value="[timestamp]">`.
2. **Subsequent Pages (POST)**: To load more items, a `POST` request must be sent with `x-requested-with: XMLHttpRequest` and a form-urlencoded body (e.g., `tab=11&offset=[timestamp]&limit=[index]`).
3. **The `limit` Parameter**: The `limit` parameter does *not* dictate the number of items to return; it actually acts as the starting index (`startIndex`). To fetch page 2, you pass `limit=20`. For page 3, `limit=40`, scaling as `depth * 20`.
4. **The `offset` Parameter**: The offset is a static timestamp identifying the search session. It remains identical across all pagination requests and is not updated in the POST responses.
5. **Response Format**: While the initial GET request returns standard HTML, the pagination POST requests return a JSON object (e.g., `{"userList": "<html>..."}`) containing the raw HTML fragment for the next batch of users. This JSON wrapper must be parsed before the HTML can be processed.

---

## 2. Documented Parsers

### Bookmarks / Saves (`/ct/online/11`)
**Method**: `ApiParser.parseBookmarkHtml`

| Field | Selector / Logic | Notes |
| :--- | :--- | :--- |
| **UserId** | `a[onclick*=gotoprofile]` (regex: `gotoprofile\((\d+)`) | Primary identifier for REST lookup. |
| **Name** | `a[onclick*=gotoprofile].text()` | Scraped as initial placeholder. |
| **Distance** | `span.distance` | **HTML Exclusive**. Not available in REST. |
| **City** | `p.descprof` (last comma-separated part) | Used as a fallback if REST `region` is missing. |
| **Age** | `p.descprof` (regex: `(\d+)\s+ans`) | Extracted for immediate UI display. |
| **isOnline** | Implicit — all users on this page are online | No per-card icon check needed. |

### Favorites (`/ct/online/2`)
**Method**: `ApiParser.parseFavoritesHtml`

This page lists **all favorites regardless of online status** (unlike `/ct/online/11` which only shows currently online saves). It is the **only source for distance** for all favorites.

| Field | Selector / Logic | Notes |
| :--- | :--- | :--- |
| **UserId** | `a[onclick*=gotoprofile]` (regex: `gotoprofile\((\d+)`) | Primary identifier for REST lookup. |
| **Name** | `a[onclick*=gotoprofile].text()` | Scraped as initial placeholder. |
| **Distance** | `span.distance` | **HTML Exclusive**. Not available in REST. |
| **City** | `p.descprof` (last comma-separated part) | Fallback locale data. |
| **Age** | `p.descprof` (regex: `(\d+)\s+ans`) | Extracted for immediate UI display. |
| **isOnline** | `img[src*=ic_online]` presence in card | Per-card; only present when member is currently online. |
| **isBookmarked** | `img[src*=bookmark_profile]` presence | Determines if member is also saved. |

> **Implementation note**: `scrapeFavoritesHtml()` runs first in the contacts loading chain, before `fetchBookmarks()`. This ensures all contacts get distance data, not just bookmarked ones.

### Profile Viewers / Visits Sent (`/ct/online/3`, `/ct/online/6`)
**Method**: `ApiParser.parseProfileViewersHtml`

Used for both incoming profile viewers (who visited you, `/ct/online/3`) and outgoing profile visits (who you visited, `/ct/online/6`).

| Field | Selector / Logic | Notes |
| :--- | :--- | :--- |
| **UserId** | Regex on `onclick` OR `href` OR meta-redirect | Multiple fallback strategies used. |
| **Timestamp** | `p.date_show` | Parsed via `ApiParser.parseLookerDate`. |
| **Avatar** | `img.picture_border` | Extracted for fast list loading. |

### Pokes Received / Sent (`/ct/online/8`, `/ct/online/10`)
**Method**: `ApiParser.parsePokeHtml`

Used for both incoming pokes (received greetings, `/ct/online/8`) and outgoing pokes (sent greetings, `/ct/online/10`).

| Field | Selector / Logic | Notes |
| :--- | :--- | :--- |
| **UserId** | `div.one_user div[id]` (regex: `user_(\d+)`) | Uses the wrapper ID. |
| **Type** | `img[src*=pokes]` | Extracts poke icon ID to determine greeting type. |

### Search Results (`/ct/recherche`)
**Method**: `ApiParser.parseSearchResults`

Parses large user cards into `SearchItem` objects including city, age, and online status.

---

## 3. Mandatory Enrichment Fields

When enriching an HTML-scraped user with REST data, the following fields **MUST** be prioritized from the REST response:

-   `name` / `pseudo`: Authoritative name (HTML may contain truncated labels).
-   `is_online`: Real-time status (HTML status icons can be cached/stale).
-   `sex_link`: Resolved via `MetadataManager` for the correct sex icon.
-   `photo`: JSON `image_144x189_link` is preferred over HTML thumbnails.

---

## 4. Implementation Guidelines (MainActivity)

-   **Bypass Cache**: On pull-to-refresh, use the `forceRefresh` flag to bypass `ProfileCacheManager` TTL.
-   **Preserve HTML Data**: Always store scraped `distance` in the `FavoriteItem.distance` field before REST enrichment to ensure it isn't overwritten.
-   **Atomic Updates**: Use `runOnUiThread` to ensure UI updates after REST callbacks are thread-safe.
# JALF Profile Edit Form (`/ct/profile`) & REST Mapping

This document provides a comprehensive specification of the HTML form fields on the profile editing page (`/ct/profile`) and their corresponding mappings to the REST API (`GET /rest/users/{userId}`).

## 1. Overview & Form Endpoint
- **URL**: `POST /ct/profile`
- **Encoding**: `application/x-www-form-urlencoded; charset=ISO-8859-1`
- **Purpose**: Updating user profile details in Ghost Mode / background sync.

---

## 2. HTML Form Field Mapping Table

| HTML Form Field Name | HTML Element Type | Values / Options | REST API Field (`GET /rest/users/{userId}`) | REST Link Format | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`Sexe`** | `<select>` | `1`=Homme, `2`=Femme, `4`=Couple, `8`=Travesti, `16`=Trans, `32`=Couple de femmes, `64`=Couple d'hommes | `sex_link` | `/rest/sexes/{id}` | User's own gender/sex identity. |
| **`Ornt`** | `<select>` | `1`=Hétérosexuel(le), `2`=Bi-curieux(se), `3`=Bisexuel(le), `4`=Homosexuel(le), `7`=Pansexuel(le) | `sexual_orientation_link` | `/rest/sexual-orientations/{id}` | **User's own sexual orientation**. |
| **`Marit`** | `<select>` | `1`=Célibataire, `2`=Séparé(e), `3`=En relation, `4`=Marié(e), `5`=Veuf/Veuve, `6`=Amant/Maîtresse, `7`=Autre | `social_status_link` | `/rest/social-statuses/{id}` | Civil/Social status. |
| **`Occp`** | `<select>` | `1`=Employé, `2`=Entrepreneur, `3`=Étudiant, `4`=Professionnel, `5`=Sans emploi, `6`=Retraité, `7`=A la maison, `8`=Autre, `9`=Non spécifié | `occupation_link` | `/rest/occupations/{id}` | Occupation. |
| **`Ethn`** | `<select>` | `1`=Blanc, `2`=Noir, `3`=Asiatique, `4`=Hispanique, `5`=Moyen-Orient, `6`=Autre, `7`=Amérindien, `8`=Indien, `9`=Métis | `ethnic_group_link` | `/rest/ethnic-groups/{id}` | Ethnicity. |
| **`Dispo`** | `<select>` | `1`..`11` (Schedule options) | `schedule_available_link` | `/rest/schedules-available/{id}` | Availability schedule. |
| **`Fumr`** | `<select>` | `1`=Oui, `2`=Non, `3`=Occasionnellement, `4`=Électronique | `smoking_link` | `/rest/smoking/{id}` | Smoking preferences. |
| **`Sign`** | `<select>` | `1`..`12` (Zodiac signs) | `zodiac_sign_link` | `/rest/zodiac-signs/{id}` | Zodiac sign. |
| **`Alcl`** | `<select>` | `1`..`9` (Alcohol options) | `alcohol_use_link` | `/rest/alcohol-uses/{id}` | Alcohol consumption. |
| **`Drog`** | `<select>` | `1`..`7` (Drug options) | `drug_use_link` | `/rest/drug-uses/{id}` | Drug use preferences. |
| **`Hght`** | `<select>` | `1`..`37` (Height IDs) | `height_link` | `/rest/heights/{id}` | Height preference/value. |
| **`Wght`** | `<select>` | `1`..`36` (Weight IDs) | `weight_link` | `/rest/weights/{id}` | Weight preference/value. |
| **`Goals`** | `<input type="checkbox">` | `1`, `3`, `5`..`20` (Goals) | `goals_links` | `/rest/goals/{id}` | **Multiple**. User's dating goals. |
| **`Want`** | `<input type="checkbox">` | `1`=Hommes, `2`=Femmes, `4`=Couples, `8`=Travestis, `16`=Trans, `32`=Couples de femmes, `64`=Couples d'hommes | `sexes_interested_links` | `/rest/sexes/{id}` | **Multiple**. Genders/sexes the user is interested in ("Intéressé par"). |
| **`fant_list`** | `<input type="hidden">` / checkboxes | Comma-separated fantasy IDs | `fantasies_links` | `/rest/fantasies/{id}` | **Multiple (comma-separated)**. |
| **`OrntRev`** | `<input type="checkbox">` | `1`=Hétérosexuel(le), `2`=Bi-curieux(se), `3`=Bisexuel(le), `4`=Homosexuel(le), `7`=Pansexuel(le) | **NONE (HTML ONLY)** | N/A | **HTML-only field** ("... dont l'orientation est"). Represents target user orientations sought. NOT present in REST responses. Must be extracted from HTML form as-is. |

---

## 3. Crucial Distinctions

1. **`Ornt` vs `OrntRev`**:
   - `Ornt` (REST: `sexual_orientation_link`): **Your own** sexual orientation.
   - `OrntRev` (REST: **None**): The sexual orientation of the people you are looking for ("... dont l'orientation est").

2. **`Want` vs `OrntRev`**:
   - `Want` (REST: `sexes_interested_links`): "Intéressé par" (Genders/sexes sought, e.g., Femmes = 2, Couples = 4).
   - `OrntRev` (REST: **None**): "dont l'orientation est" (Orientations of people sought).
