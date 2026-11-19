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

## 4. Pokes (Clin d'oeil) *(Pending Deprecation)*
**Endpoint**: `GET /ct/online/8`

> [!WARNING]
> **Pending Deprecation**: Pokes (`Clins d'oeil`) are scheduled to be officially replaced by the [Flirt conversation feature](conversations.md#3-flirt-categories--templates) (`/rest/flirt/categories` and `/rest/users/{userId}/conversations/flirts`).

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

## 6. Pokes Sent (Clins d'oeil envoyés) *(Pending Deprecation)*
**Endpoint**: `GET /ct/online/10`

> [!WARNING]
> **Pending Deprecation**: Outgoing pokes are scheduled to be officially replaced by the [Flirt conversation feature](conversations.md#3-flirt-categories--templates).

Returns a list of "Clins d'oeil" (pokes) sent by the authenticated user.

| Property | Value |
| :--- | :--- |
| **Format** | HTML |
| **Parser** | `ApiParser.parsePokeHtml` |
| **Description** | Outgoing pokes counterpart to `/ct/online/8` (Pokes received). |

---

## 7. Member Search (Simple & Detailed)
**Form Discovery Endpoints**:
- Simple Search form: `GET /ct/search/0`
- Detailed Search form: `GET /ct/search_det/1`
- Location cascade (optional AJAX): `POST /ct/UIControls/CountrySelector`

**Execution Endpoint**:
- Page 1: `POST /ct/searchResults/profile` (`application/x-www-form-urlencoded; charset=ISO-8859-1`)
- Page 2+: `GET /ct/searchResults/profile?InitiateSearch=0&page={n}&orderBy={1|2|3}&tab_id={0|1}` (uses server-side session state from the Page 1 `POST`)

**Builder / Parser**: `SearchApiUtils.buildSimpleSearchRequest` / `SearchApiUtils.buildDetailedSearchRequest` -> `ApiParser.parseSearchResults`

### Mode Flags
| Parameter | Simple Search (`/ct/search/0`) | Detailed Search (`/ct/search_det/1`) |
| :--- | :--- | :--- |
| `InitiateSearch` | `1` on Page 1, `0` on Page 2+ | `1` on Page 1, `0` on Page 2+ |
| `search_detl_flg` | `0` | `1` |
| `tab_id` | `0` | `1` |
| `Referer` | `/ct/search/0` | `/ct/search_det/1` |
| `btnPager` / `pager_on` | `1` / `1` | `1` / `1` |

### Form Parameter Reference
> [!IMPORTANT]
> Simple Search (`search_detl_flg=0`) and Detailed Search (`search_detl_flg=1`) are backed by separate server handlers. Several filters only exist on Simple Search (`Pseudonyme`, `VIPEnLigne`, `VIPPhotos`, `my_region`, `VIPCertified`, `geolocationOption`, `VIPOnlyGOLD`, `VIPOnlyVIP`, `VIPVideo`), while multi-value checkboxes, multi-location (`Location`), physical sliders (`VIPPoidsD`/`F`, `VIPGrandeurD`/`F`), availability (`VIPDisponible`), smoking (`VIPFumeur`), zodiac (`VIPSigne`), `VIPWebcam`, and `VIPSpeedMeeting` belong to Detailed Search.

| Parameter | Simple (`search_detl_flg=0`) | Detailed (`search_detl_flg=1`) | Notes |
| :--- | :--- | :--- | :--- |
| `seek` | Single value (`1,2,4,8,16,32,64`) | Multiple repeated keys | Sought gender/sex. |
| `wanted` | Single value (`1,2,4,8,16,32,64`) | Multiple repeated keys | Gender/sex the member desires to meet. |
| `orient` | Single value (`1,2,3,4,7`; plus `5`,`6` when `seek=4`) | Multiple repeated keys (`1,2,3,4,7`) | Sexual orientation. |
| `status` | Single value (`1`..`7`) | Multiple repeated keys | Relational configuration / civil status. |
| `relCher` | Single value (`1,3,5`..`20`) | Multiple repeated keys | Goal on JALF. |
| `VIPFantasme` | Comma-separated fantasy IDs | Comma-separated fantasy IDs | Populated from `#fant_list` / `.fantasy-card--selected[data-fantasyid]`. |
| `VIPAgeD` / `VIPAgeF` | `18`..`99` | `18`..`99` | Age range. |
| `orderBy` | `1` (Last visit), `2` (Registration), `3` (Same fantasies) | `1`, `2`, `3` | Sort order. |
| `Pseudonyme` | String (username substring) | Not in web form *(enforced client-side in app)* | **Must be `Pseudonyme`**, not `pseudo`. |
| `VIPEnLigne` | `1` | Not in web form *(enforced client-side in app)* | Online only. **Must be `VIPEnLigne`**, not `online`. |
| `VIPPhotos` | `1` | Not in web form *(enforced client-side in app)* | Profiles with photos. **Must be `VIPPhotos`**, not `a_photo`. |
| `my_region` | `1` | Not in web form | Restrict to authenticated user's own region. **Must be `my_region`**, not `maRegion`. |
| `cboPays`, `cboProvince`, `cboRegion` | Required for single-location filter (`0` = all) | Sent alongside `Location` | Simple Search reads `cboPays`/`cboProvince`/`cboRegion` + `hidCboProvince`/`hidCboRegion` (not `Location`). |
| `hidCboProvince`, `hidCboRegion` | Mirrors `cboProvince` / `cboRegion` | Mirrors `cboProvince` / `cboRegion` | Populated by `init_search()` before form submission. |
| `Location`, `multRegions` | Not in web form | Required for multi-location | `Location` joins `country[,province[,region]]` entries with `-` (e.g. `100,9,16-100,8`). |
| `VIPPoidsD` / `VIPPoidsF` | N/A | `1`..`35` (default `1`..`35`) | Weight range IDs (from `/rest/weights`). |
| `VIPGrandeurD` / `VIPGrandeurF` | N/A | `1`..`37` (default `1`..`37`) | Height range IDs (from `/rest/heights`). |
| `VIPDisponible` | N/A | Multiple repeated keys (`1`..`11`) | Availability schedule. |
| `VIPFumeur` | N/A | Multiple repeated keys (`1`..`4`) | Smoking preference. |
| `VIPSigne` | N/A | Multiple repeated keys (`1`..`12`) | Zodiac sign. |
| `VIPOrigine` | N/A | Single ID (from `/rest/ethnic-groups`) | Ethnicity (also forwarded on Page 2+ query string). |
| `VIPWebcam` | N/A | `YES` | Members with a webcam. |
| `VIPSpeedMeeting` | N/A | `1` | "Désire baiser aujourd'hui" / speed meeting. |
| `VIPCertified`, `geolocationOption`, `VIPOnlyGOLD`, `VIPOnlyVIP`, `VIPVideo` | `1` (available in `/ct/search/0`) | N/A | Additional Simple Search checkboxes on the web form. |


