# JALF REST API: Users

This document describes the endpoints related to user profiles, real-time status counters, and account preferences.

## 1. User Profile
**Endpoint**: `GET /rest/users/{userId}`

Returns the comprehensive profile of a specific user, including their stats, location, and links to other resources.

```json
{
    "certified": false, // If true, the account has been verified by JALF staff
    "relationship_link": null, // REST link to relationship metadata (if applicable)
    "archived_conversations_link": "/rest/users/USERID/conversations/archived", // REST endpoint to fetch archived chat threads with this user
    "webcam_blocked_users_link": "/rest/users/USERID/webcam/blocked-users", // REST endpoint for managing webcam blocklist for this user
    "photo": { // Primary profile photo object
        "image_1024x768_link": "pictureurl", // Link to high-resolution photo (1024x768)
        "generic": 1, // 1 if using a generic placeholder avatar, 0 if custom
        "image_144x189_link": "pictureurl", // Link to standard profile photo (144x189)
        "is_sensitive": false, // If true, photo should be blurred according to NSFW settings
        "certified": false, // If true, this specific photo has been verified
        "image_70x95_link": "pictureurl" // Link to thumbnail photo (70x95)
    },
    "accepted_languages": [ // List of languages the user speaks/understands
        {
            "fr": "Français" // Language ISO code key mapped to localized name
        }
    ],
    "alcohol_use": "string", // Localized label for alcohol consumption (e.g. "Occasional")
    "happening_default_address_link": "/rest/happenings/defaultAddress/USERID", // Link to default physical address for events/happenings
    "privileges_links": [], // Array of REST links to privilege definitions
    "test": "0", // Internal test flag (1 = test account)
    "user_avatar": "/jalf/images/icons/avatar_2.png", // Path to custom user avatar (if different from primary photo)
    "drug_use_link": "/rest/drug-uses/DRUGUSEID", // REST link to drug-use metadata
    "fantasies_links": [ // Array of REST links to specific fantasies
        "/rest/fantasies/FANTASYID"
    ],
    "zodiac_sign": "string", // Localized name of the user's zodiac sign
    "goals_links": [ // Array of REST links to user's dating goals
        "/rest/goals/GOALID"
    ],
    "user_events_link": "string", // SSE stream URI or landing page for real-time notifications
    "social_status": "string", // Localized label for social status (e.g. "Single")
    "goals": [ // Array of localized goal labels
        "Rencontres réelles"
    ],
    "schedule_available": "string", // Localized text describing active hours/days
    "user_favorites_link": "/rest/users/USERID/favorites", // REST endpoint to fetch/manage this user's favorites list
    "drug_use": "string", // Localized label for drug usage (e.g. "Never")
    "user_videos_albums_link": "/rest/users/USERID/videos/albums", // REST endpoint for user's video content
    "user_mail_link": "/rest/users/USERID/mail", // REST link to legacy internal mail system
    "country": { // Geographic country data
        "name": "string", // Localized country name
        "country_link": "/rest/regions/REGIONID" // REST link to country metadata
    },
    "sex_link": "/rest/sexes/SEXID", // REST link to sex/gender metadata
    "user_blocked_users_link": "/rest/users/USERID/blocked", // REST endpoint for managing blocked users
    "onfire": "0", // Sex Today (1 = "want to have sex today")
    "user_pnsubscriptions_link": "/rest/users/USERID/pnsubscriptions", // Link for Push Notification subscription management
    "send_poke_htlink": "/ct/memberProfile/USERID/5", // Website URL to send a "Clin d'oeil" (poke)
    "zodiac_sign_link": "/rest/zodiac-signs/ZODIACID", // REST link to zodiac sign metadata
    "sexes_interested_links": [ // List of REST links to genders the user is interested in
        "/rest/sexes/SEXID"
    ],
    "options": { // User interaction privacy/UI preferences
        "geolocation": 0, // 1 if sharing geolocation, 0 if disabled
        "share_distance": 1, // 1 if allowing others to see distance
        "notifications_minimized": 0, // UI hint for notification bar state
        "conversations_minimized": 0 // UI hint for chat window state
    },
    "sexes_interested": [ // Array of localized labels for interested genders
        "Homme"
    ],
    "weight": "string", // User's weight string (units depend on user preferences)
    "new_conversations_link": "/rest/users/USERID/conversations/new", // REST endpoint for unread/new conversations
    "alcohol_use_link": "/rest/alcohol-uses/ALCOHOLID", // REST link to alcohol consumption metadata
    "vip": "0", // 1 if user has VIP status, 0 otherwise
    "schedule_available_link": "/rest/schedules-available/SCHEDULEID", // REST link to availability metadata
    "report_htlink": "/ct/profile/report_profile/USERID", // Website URL to file a report against this user
    "user_link": "/rest/users/USERID", // Canonical REST link to this user's profile
    "relationship": null, // Detailed relationship status object (if applicable)
    "notifications_link": "/rest/users/USERID/notifications", // REST endpoint for user's event notifications
    "weight_link": "/rest/weights/WEIGHTID", // REST link to weight category metadata
    "notices_link": "/rest/users/USERID/notices", // REST endpoint for system notices/alerts
    "province": { // Geographic sub-region data
        "name": "string", // Localized province/state name
        "province_link": "/rest/regions/REGIONID/SUBREGIONID" // REST link to sub-region metadata
    },
    "ethnic_group": "string", // Localized name of ethnic group
    "user_status_link": "/rest/users/USERID/status", // REST endpoint for real-time status counters
    "gold": "0", // 1 if user has Gold status, 0 otherwise
    "occupation_link": "/rest/occupations/OCCUPATIONID", // REST link to occupation metadata
    "last_connection": "need_vip", // Formatted string of last activity (or "need_vip" if Restricted)
    "occupation": "string", // Localized label for occupation
    "active_conversations_link": "/rest/users/USERID/conversations/active", // REST endpoint for the active chat thread list
    "sexual_orientation": "string", // Localized label for sexual orientation
    "self_htlink": "/ct/memberProfile/USERID/1", // Website URL for viewing own profile on web
    "name": "string", // User's display name/alias
    "dynamic_main_menu_link": "/rest/users/USERID/dynamic-main-menu", // Menu customization options (VIP-only feature)
    "staff": "0", // 1 if the user is a JALF staff member
    "user_options_link": "/rest/users/USERID/user-options", // REST endpoint for global account options
    "smoking_link": "/rest/smoking/SMOKINGID", // REST link to smoking habit metadata
    "sex_val": 2, // Integer ID of the user's sex category
    "profile_descriptions": { // Localized bio/descriptions (can contain multiple languages)
        "fr": { // Language key (ISO 639-1)
            "description": "string" // Main body of the profile description in this language
        }
    },
    "height_link": "/rest/heights/HEIGHTID", // REST link to height category metadata
    "region": { // Geographic city/local area data
        "region_link": "/rest/regions/REGIONID/SUBREGIONID/LOCALID", // REST link to local region/city metadata
        "name": "string" // Localized city/region name
    },
    "callouts_link": "/rest/users/USERID/callouts", // REST endpoint for managing public profile callouts
    "sex": "string", // Localized label for user's sex
    "online": "1", // 1 if currently online, 0 otherwise
    "send_message_htlink": "/ct/conversations#startconvo=/rest/users/USERID", // Direct website link to start a conversation
    "social_status_link": "/rest/social-statuses/1", // REST link to social status metadata
    "height": "string", // User's height string (e.g. "1,68 m (5'6'')")
    "user_photos_albums_link": "/rest/users/USERID/photos/albums", // REST endpoint for user's categorized photo albums
    "age": "string", // User's age string (computed server-side)
    "region_link": "/rest/regions/REGIONID/SUBREGIONID/LOCALID", // Duplicate REST link to local region
    "registered_since": "need_vip", // ISO 8601 timestamp or "need_vip" string
    "appearance_link": null, // Link to detailed physical appearance traits
    "smoking": "string", // Localized label for smoking habit
    "appearance": null, // Detailed physical appearance object
    "gold_message_link": "/rest/users/USERID/conversations/gold-message", // REST endpoint for Gold voice/media
    "sexual_orientation_link": "/rest/sexual-orientations/ORIENTATIONID", // REST link to sexual orientation metadata
    "location": { // Flattened location strings for UI display
        "country": "string",
        "region": "string",
        "state": "string"
    },
    "ethnic_group_link": "/rest/ethnic-groups/ETHNICID", // REST link to ethnic group metadata
    "fantasies": [ // Array of localized fantasy labels
        "string"
    ]
}
```

---

## 2. Real-time Status
**Endpoint**: `GET /rest/users/{userId}/status`

Returns real-time counters and status indicators for the authenticated user (unread messages, pokes, lookers, etc.).

```json
{
    "online": 0, // Current online status: 1 = online, 0 = offline
    "new_notices_count": 0, // Number of unread system notices/broadcasts
    "login_device_icon": { // Metadata about the primary device used for the current session
        "jalf_38x64_icon_uriref": "picturerelativeurl" // URI to the device icon (e.g., mobile phone or desktop icon)
    },
    "unread_convo_messages_count": 0, // Total number of unread messages across all conversation categories
    "new_pokes_count": 0, // Number of new "Clins d'oeil" (pokes) received since last check
    "new_messages_count": 0, // Number of new message notifications (distinct from unread count)
    "new_favorites_count": 0, // Number of users who added you to their favorites since last check
    "new_lookers_count": 0, // Number of users who viewed your profile since last check
    "new_notifs_count": 0 // Total count of new general notifications
}
```

---

## 3. Favorites
**Endpoint**: `GET /rest/users/{userId}/favorites`

Returns the list of other users marked as favorites by the specified user.

```json
{
    "name": "Favorites", // Localized title of the list
    "total_items_count": 0, // Total number of favorite entries
    "items": [ // Array of favorite entries
        {
            "has_note": true, // If true, a personal annotation exists for this favorite entry
            "user_link": "/rest/users/FAVORITEUSERID", // REST link to the favorite user's profile
            "similar_fantasies": [ // List of fantasies shared between the account owner and the favorite user
                "/rest/fantasies/FANTASYID" // REST link to a shared fantasy metadata entry
            ],
            "description": "string", // Username of the favorite user
            "online": true, // Real-time online status: true = online, false = offline
            "user_favorite_link": "/rest/users/USERID/favorites/FAVORITEUSERID", // REST endpoint to manage this specific favorite entry
            "rank": 0 // User-defined sorting rank for this favorite
        }
    ]
}
```

---

## 4. Notifications (Feed)
**Endpoint**: `GET /rest/users/{userId}/notifications`

Returns the activity feed (pokes, profile views, favorite alerts) for the authenticated user.

```json
{
    "notifications": [ // Array of notification events
        {
            "notification_link": "/rest/notifications/NOTIFICATIONID", // REST endpoint to manage/delete this notification
            "type": 22, // Numeric code for the notification type (e.g. 22=Favorite/View, 13=Video like, 7=Photo like, 2=Upload)
            "why": "favorite_of", // Machine-readable reason (e.g. "favorite_of", "your_favorite", "author")
            "when_added": "2026-04-21T03:17:31.639Z", // ISO 8601 timestamp with milliseconds
            "user_name": "string", // Username of the person who triggered the notification
            "user_link": "/rest/users/OTHERUSERID", // REST link to the triggering user's profile
            "user_htlink": "/ct/memberProfile/OTHERUSERID/1", // Website URL to the triggering user's profile
            "reference_link": "/rest/users/USERID", // REST link to the target object (your profile, or a specific photo/video)
            "reference_htlink": "/ct/memberProfile/USERID/1", // Website URL for the target object
            "reference_data": { // Polymorphic metadata about the referenced object
                "type": "user", // Type of reference: "user", "photo", or "video"
                "name": "USERNAME", // Name of the referenced object (e.g. username for type:user)
                "thumbnail_uri": "https://...", // Image URI if reference is a photo/video
                "description": "string", // Caption or description for photos/videos
                "is_sensitive": true // Flag for NSFW content if reference is a photo/video
            }
        }
    ]
}
```

---

## 5. Account Options & Preferences
**Endpoint**: `GET /rest/users/{userId}/user-options`

Returns the fine-grained notification and privacy settings. Most fields are booleans toggling Email, Website Notification, or Real-time (SSE) alerts.

```json
{
    // --- Favorite Activities ---
    "favorite_connected_realtime": true, // SSE alert when a favorite user logs in
    "favorite_updated_profile_realtime": true,
    "favorite_updated_profile_notification": true,
    "favorite_updated_profile_mail": true,
    "favorite_added_status_realtime": true,
    "favorite_added_status_notification": true,
    "favorite_added_status_mail": true,
    "favorite_certified_realtime": true,
    "favorite_certified_notification": true,
    "favorite_certified_mail": true,
    "favorite_speed_meeting_realtime": true,
    // --- Mutual Favorites & Private Content ---
    "mutual_favorite_add_private_picture_notification": true, // Alert when a mutual favorite uploads private photos
    "mutual_favorite_add_private_video_notification": true, // Alert when a mutual favorite uploads private videos
    "in_favorite_add_private_picture_notification": true, // Alert when anyone in your favorites adds private content
    // --- Social Interactions & Matchmaking ---
    "favorite_added_notification": true, // Someone added YOU as a favorite
    "favorite_added_mail": true,
    "look_at_you_realtime": true, // Someone viewed your profile
    "look_at_you_offline_mail": true,
    "matches_matching_notification": true, // Alert for automated matchmaking hits
    "matches_matching_mail": true,
    "new_poke_realtime": true, // "Clin d'oeil" received
    "new_poke_notification": true,
    "new_poke_mail": true,
    "new_message_realtime": true, // New chat message
    "new_message_mail": true,
    "message_read_mail": true, // (VIP) Message read receipt
    "conversation_realtime": true,
    // --- Photo Gallery & Stories ---
    "picture_like_author_realtime": true,
    "picture_like_author_notification": true,
    "picture_comment_author_realtime": true,
    "picture_realise_author_realtime": true, // Someone wants to "realize" a fantasy with you
    "story_like_author_notification": true, // Story like alert
    "story_comment_author_notification": true, // Story comment alert
    "story_realise_author_notification": true, // Story fantasy realization request
    // --- Forum & Miscellaneous ---
    "forum_post_added_notification": true,
    "upgrade_receipt_notification": true, // Receipt for VIP/Gold purchase
    "geolocation": 1,
    "share_distance": 1,
    "journal_frequency": "daily",
    "journal_mail": true,
    "auto_connection_enabled": true,
    "new_message_sound": true,
    "webcam_publish_quality": "default_quality",
    "notify_activities_to_everyone": true,
    "notify_activities_to_my_favorites": true
}
```

---

## 4. User Blocking

Endpoints for managing blocked users. This is crucial for User-Generated Content (UGC) safety compliance on Google Play.

### Block a User
**Endpoint**: `POST /rest/users/{userId}/blocked`

**Request Headers**:
- `Accept: */*`
- `Content-Type: application/vnd.jalf.blockuser+json`
- `x-csrftoken`: SUID token

**Request Body**:
```json
{
    "user_link": "/rest/users/TARGET_USERID"
}
```

---

## 5. Gold Message
**Endpoint**: `GET /rest/users/{userId}/conversations/gold-message`
**Endpoint**: `POST /rest/users/{userId}/conversations/gold-message`

Retrieves or updates the user's pre-recorded "Gold Message" (a quick response template for Premium/Gold members). A user is only permitted one Gold message on the server, which serves as the primary quick response pattern.

### Request Header (POST)
`Content-Type: application/vnd.jalf.convo.goldmessage+json`

### JSON Payload (POST)
```json
{
    "message": "Hello !\nTon profil a piqué ma curiosité...\n\nPrête à voir si nos zones de confort sont compatibles ?"
}
```

### List Blocked Users
**Endpoint**: `GET /rest/users/{userId}/blocked`

Returns the list of accounts currently blocked by the user.

**Response Body**:
```json
{
  "items": [
    {
      "description": "Username",
      "user_blocked_user_link": "/rest/users/{USER_ID}/blocked/{TARGET_USER_ID}",
      "user_status_link": "/rest/users/{TARGET_USER_ID}/status",
      "user_link": "/rest/users/{TARGET_USER_ID}",
      "rank": 1
    }
  ],
  "total_items_count": 47,
  "name": "Blocked Users"
}
```
