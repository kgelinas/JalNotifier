# JALF REST API: Media

This document describes the endpoints for managing and accessing photos and videos, including albums and safety ratings.

## 1. Photo Albums
**Endpoint**: `GET /rest/users/{userId}/photos/albums`

Returns the list of photo albums owned by a user.

```json
{
    "name": "Photos Albums", // Localized header for the albums list
    "total_items_count": 5, // Total number of albums found
    "items": [ // Primary array for UI list population
        {
            "user_photos_album_link": "/rest/users/USERID/photos/albums/ALBUMNAME", // REST endpoint to fetch photos within this album
            "rank": 0, // Server-side defined display order (0-indexed)
            "description": "string" // Localized name/label of the album (e.g., "Public", "Private")
        }
    ]
}
```

---

## 2. List Photos in Album
**Endpoint**: `GET /rest/users/{userId}/photos/albums/{albumName}`

Returns the list of photos contained within a specific album.

```json
{
    "photos_selection": {
        "offset": 0,
        "photos": [
            {
                "image_uri": "pictureurl", // picture url
                "anonymous": false, // true = anonymous ; false = not anonymous
                "show_public_album": true, // true = show public album ; false = not show public album
                "user_photos_album_link": "/rest/users/USERID/photos/albums/ALBUMNAME", // user photos album link
                "image_link": "pictureurl", // picture url
                "original_file_sha256": null, // original file sha256
                "presentation_order": 1, // presentation order
                "datetime_added": "0000-00-00T00:00:00.000Z", // datetime added
                "state": "string",
                "comment": null, // comment
                "photo_link": "/rest/photos/PHOTOID", // photo link
                "thumbnail_link": "pictureurl",
                "photo_category_link": null,
                "thumbnail_uri": "pictureurl",
                "photo_rating_link": "/rest/photos/ratings/PHOTORATINGID",
                "certification_status": "certified",
                "user_link": "/rest/users/USERID",
                "approved": true
            }
        ]
    },
    "name": "string",
    "total_photos": 0,
    "album_accueil": 1, // 1 = default album ; 0 = not default album
    "total_items_count": 0
}
```

---

## 3. Photo Details & Metadata
**Endpoint**: `GET /rest/photos/{photoId}`

Returns detailed metadata for a specific photo.

```json
{
    "photo_link": "/rest/photos/PHOTOID", // Canonical REST link to this photo
    "user_link": "/rest/users/USERID", // REST link to the owner's profile
    "user_photos_album_link": "/rest/users/USERID/photos/albums/private", // REST link to the album containing this photo
    "photo_rating_link": "/rest/photos/ratings/1", // REST link to the safety/NSFW rating applied
    "image_uri": "https://...", // Primary high-resolution image URI
    "thumbnail_uri": "https://...", // Low-resolution preview URI
    "datetime_added": "2024-08-05T19:27:42.000Z", // Upload timestamp
    "state": "approved", // Approval state: "approved", "pending", "refused"
    "certification_status": "not_certified", // "certified", "not_certified", "pending"
    "presentation_order": 3, // Sort rank within the album
    "anonymous": false, // If true, owner's identity is hidden
    "original_file_sha256": "hash" // Integrity hash of the original upload
}
```

---

## 4. Photo Safety Ratings
**Endpoint**: `GET /rest/photos/ratings`

Returns the list of available NSFW/Safety ratings for photos.

```json
{
    "name": "Photos ratings", // Localized header for the ratings list
    "total_items_count": 10, // Total number of rating categories
    "items": [ // Array of rating definitions
        {
            "photo_rating_link": "/rest/photos/ratings/1", // Canonical REST link to this rating level
            "description": "HABILLÉ", // Localized name (e.g. "HABILLÉ", "SEXUEL")
            "rank": 0
        }
    ]
}
```

---

## 5. Video Albums
**Endpoint**: `GET /rest/users/{userId}/videos/albums`

Returns the list of video albums owned by a user.

```json
{
    "name": "Videos Albums",
    "total_items_count": 0,
    "items": [
        {
            "user_photos_album_link": "/rest/users/USERID/videos/albums/ALBUMNAME", // Actually links to video album
            "description": "string"
        }
    ]
}
```

---

## 6. List Videos in Album
**Endpoint**: `GET /rest/users/{userId}/videos/albums/{albumName}`

Returns the list of videos contained within a specific album.

```json
{
    "videos_selection": {
        "offset": 0,
        "videos": [
            {
                "duration": 69, // Duration in seconds
                "user_link": "/rest/users/USERID",
                "title": "Doggy",
                "state": "approved",
                "thumbnail_uri": "pictureurl",
                "animated_thumbnails": [ // Sequence of frames for preview
                    "pictureurl",
                    "pictureurl",
                    "pictureurl",
                    "pictureurl",
                    "pictureurl"
                ],
                "video_link": "/rest/videos/VIDEOID",
                "original_file_sha256": "hash"
            }
        ]
    },
    "identifier": "string"
}
```

---

## 7. Video Details
**Endpoint**: `GET /rest/videos/{videoId}`

Returns metadata and thumbnail sequences for a specific video.

```json
{
    "video_link": "/rest/videos/VIDEOID", // Canonical REST link to this video
    "user_link": "/rest/users/USERID", // REST link to the owner's profile
    "user_videos_album_link": "/rest/users/USERID/videos/albums/private", // REST link to the album
    "title": "Doggy",
    "duration": 69,
    "thumbnail_uri": "https://...",
    "animated_thumbnails": [ // Sequence of frames for hover/animated preview
        "https://...",
        "https://..."
    ],
    "datetime_added": "2024-01-01T00:00:00.000Z",
    "original_file_sha256": "hash"
}
```
