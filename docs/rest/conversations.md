# JALF REST API: Conversations

This document describes the endpoints for listing conversation threads and managing detailed message histories.

## 1. List Conversations
**Endpoint**: `GET /rest/users/{userId}/conversations/{folder}`
- `{folder}` can be `active`, `new`, `archived`, or `flirts`.

Returns the list of conversation threads for the specified category.

```json
{
    "total_conversations_count": 150, // Total number of threads in this specific category
    "total_unread_messages_count": 5, // Total number of unread messages across all threads in this category
    "conversations": [ // Array of thread summary objects
        {
            "conversation_link": "/rest/users/USERID/conversations/CONVERSATIONID", // REST link to the detailed thread
            "last_posted": "2024-04-20T12:00:00.000Z", // ISO timestamp of the most recent activity in this thread
            "unread_messages_count": 2, // Number of unread messages in this specific thread
            "last_message": { // Summary of the most recent message in the thread
                "type": "text", // Type of the last message ("text" or "photo")
                "content": {
                    "text": "See you later!" // Preview text of the last message
                },
                "from_user_link": "/rest/users/OTHERUSERID", // REST link to the sender of the last message
                "message_link": "/rest/users/USERID/conversations/messages/ID", // REST link to the last message itself
                "posted": "2024-04-20T12:00:00.000Z", // ISO timestamp of the last message
                "ephemeral": null // Ephemeral metadata if the last message is self-destructing
            },
            "other_members": [ // Summary participants list (usually 1 person in private chats)
                {
                    "user_link": "/rest/users/OTHERUSERID", // REST link to participant's profile
                    "name": "string", // Username of participant
                    "online": true, // Real-time online status
                    "profile_photo_uri": "pictureurl", // Participant's avatar URI
                    "profile_photo_certified": true, // Participant avatar verification flag
                    "profile_photo_sensitive": false // NSFW flag for participant's avatar
                }
            ]
        }
    ]
}
```

---

## 2. Get Conversation Details
**Endpoint**: `GET /rest/users/{userId}/conversations/{conversationId}`

Returns the full message history and metadata for a specific conversation thread.

```json
{
    "unread_messages_count": 0, // Number of messages in this specific thread that YOU have not yet read
    "cannot_post_error_message": null, // If not null, contains the localized reason why you cannot reply (e.g. "User blocked")
    "conversation_link": "/rest/users/USERID/conversations/CONVERSATIONID", // Canonical REST link to this conversation
    "ephemeral_mode": true, // If true, messages in this thread are automatically deleted after being read or after a timer
    "last_posted": "2024-04-20T12:00:00.000Z", // ISO timestamp of the most recent activity in the thread (message or system event)
    "can_post": "yes", // "yes" if the user can send a reply, or other values (e.g., "no", "deleted_user") indicating they cannot
    "messages_count": true, // (Unknown flag, usually true)
    "folder_link": "/rest/users/USERID/conversations/active", // REST link to the category this thread belongs to (active/new/archived/flirts)
    "read_until": "2024-04-20T11:50:00.000Z", // ISO timestamp indicating the last message YOU have read in this thread
    "other_members": [ // Array of other participants in the conversation
        {
            "type": "user", // Participant type (usually "user")
            "certification": null, // Certification badge details for the participant
            "user_link": "/rest/users/OTHERUSERID", // REST link to participant's profile
            "name": "string", // Username of the participant
            "profile_photo_uri": "pictureurl", // URI to participant's thumbnail
            "profile_photo_sensitive": true, // If true, participant's avatar should be blurred
            "online": true, // Real-time online status of the participant
            "read_until": "2024-04-20T11:55:00.0000Z", // ISO timestamp indicating the last message THE OTHER USER has read
            "sex_link": "/rest/sexes/SEXID", // REST link to participant's sex metadata
            "profile_photo_certified": true // If true, participant's photo is verified
        }
    ],
    "messages": [ // Array of messages in this thread (ordered by 'posted' timestamp)
        {
            "posted": "2024-04-20T11:45:00.000Z", // ISO timestamp of when the message was sent
            "message_link": "/rest/users/USERID/conversations/messages/MESSAGEID", // REST link to manage/delete this specific message
            "from_user_link": "/rest/users/OTHERUSERID", // REST link to the sender's profile
            "type": "text", // Message type: "text", "photo", or "flirt"
            "ephemeral": { // Details for self-destructing messages (null if standard message)
                "remaining_count": 5, // Number of views remaining before deletion
                "expiration": 3600, // Seconds remaining before deletion (if time-based)
                "initial_remaining_count": 10, // Original number of views allowed
                "show_duration": 10, // Seconds the message is shown per view
                "expires_at": null // ISO timestamp of absolute expiration (if applicable)
            },
            "content": { // Polymorphic content based on 'type'
                "text": "Hello!" // The message body (if type is "text")
            }
        },
        {
            "type": "photo", // Example of a photo message
            "from_user_link": "/rest/users/USERID",
            "posted": "2024-04-20T11:55:00.000Z",
            "message_link": "/rest/users/USERID/conversations/messages/MESSAGEID",
            "ephemeral": null,
            "content": {
                "photo_link": "/rest/photos/PHOTOID", // REST link to the photo asset metadata
                "thumbnail_uri": "pictureurl", // URI for low-res preview
                "fullsize_uri": "pictureurl", // URI for high-res photo access
                "certified": true // If true, the photo was verified after being sent
            }
        },
        {
            "type": "flirt", // Example of a flirt message
            "from_user_link": "/rest/users/USERID",
            "posted": "2026-10-18T15:16:44.987Z",
            "message_link": "/rest/users/USERID/conversations/messages/MESSAGEID",
            "ephemeral": null,
            "content": {
                "flirt_link": "/rest/flirt/13", // REST link to the flirt template
                "text_template": "%%USERNAME%% aimerait en apprendre plus sur toi", // Template string (replace %%USERNAME%% with sender's username)
                "category_image_uri": "https://photos4.jalf.com/0e58LcmW7.svg" // SVG icon URI for the flirt category
            }
        }
    ]
}
```

---

## 3. Flirt Categories & Templates

> [!NOTE]
> **Replacement for Legacy Pokes (`Clins d'oeil`)**: The Flirt conversation feature (`/rest/flirt/*` and `/rest/users/{userId}/conversations/flirts`) is designed to eventually replace legacy Pokes (`/ct/online/8`, `/ct/online/10`, and `/ct/memberProfile/{userId}/5`).

### 3.1 List Flirt Categories
**Endpoint**: `GET /rest/flirt/categories`

Returns all available flirt categories with their titles, headers, and SVG icons.

```json
{
    "categories": [
        {
            "flirt_category_link": "/rest/flirt/categories/1",
            "title": "Première approche",
            "header": "Premier contact? Choisis l'une des salutations suivantes pour une introduction sans embûches.",
            "category_image_uri": "https://photos4.jalf.com/0e58LcmW7.svg"
        },
        {
            "flirt_category_link": "/rest/flirt/categories/2",
            "title": "Compliments",
            "header": "Prends contact en douceur en choisissant l'un des compliments suivants.",
            "category_image_uri": "https://photos4.jalf.com/pOkrc5vt8.svg"
        },
        {
            "flirt_category_link": "/rest/flirt/categories/3",
            "title": "Faire monter la chaleur",
            "header": "Prêt·e à plus? Fais monter l'excitation d'un cran.",
            "category_image_uri": "https://photos4.jalf.com/IKbZel3HC.svg"
        },
        {
            "flirt_category_link": "/rest/flirt/categories/4",
            "title": "Réactions",
            "header": "Envoûté·e par son charme? Dis-lui l'effet qu'il/elle/iel te fait.",
            "category_image_uri": "https://photos4.jalf.com/GJdo6tjzF.svg"
        },
        {
            "flirt_category_link": "/rest/flirt/categories/5",
            "title": "Pratiques sexuelles",
            "header": "L'autre semble intéressé·e? Exprime ton désir d'aller plus loin.",
            "category_image_uri": "https://photos4.jalf.com/FGf1eVMq4.svg"
        },
        {
            "flirt_category_link": "/rest/flirt/categories/6",
            "title": "Fétiches",
            "header": "Fais-lui part de ton fétiche en lui partageant tes envies.",
            "category_image_uri": "https://photos4.jalf.com/hQR6mTQ5h.svg"
        },
        {
            "flirt_category_link": "/rest/flirt/categories/7",
            "title": "BDSM",
            "header": "Vous partagez des fantasmes BDSM? Fais-lui savoir tes désirs.",
            "category_image_uri": "https://photos4.jalf.com/rrPg1Tfim.svg"
        },
        {
            "flirt_category_link": "/rest/flirt/categories/8",
            "title": "Relatif au profil",
            "header": "Tu veux en apprendre plus sur la personne? Fais-lui savoir ce qui te manque.",
            "category_image_uri": "https://photos4.jalf.com/FUUDcNjg0.svg"
        },
        {
            "flirt_category_link": "/rest/flirt/categories/9",
            "title": "Refus",
            "header": "Exprime ton refus en un seul clic.",
            "category_image_uri": "https://photos4.jalf.com/Ud2Hy6vR2.svg"
        },
        {
            "flirt_category_link": "/rest/flirt/categories/10",
            "title": "Statut",
            "header": "Envie de prendre contact? Partage ton statut.",
            "category_image_uri": "https://photos4.jalf.com/8xkgRn5V6.svg"
        }
    ]
}
```

### 3.2 Get Flirt Category Templates
**Endpoint**: `GET /rest/flirt/categories/{categoryId}`

Returns the list of flirt message templates within a category.

```json
{
    "title": "Première approche",
    "header": "Premier contact? Choisis l'une des salutations suivantes pour une introduction sans embûches.",
    "category_image_uri": "https://photos4.jalf.com/0e58LcmW7.svg",
    "flirt_messages": [
        {
            "flirt_link": "/rest/flirt/1",
            "text_template": "%%USERNAME%% te salue"
        },
        {
            "flirt_link": "/rest/flirt/2",
            "text_template": "%%USERNAME%% aimerait te rencontrer"
        },
        {
            "flirt_link": "/rest/flirt/13",
            "text_template": "%%USERNAME%% aimerait en apprendre plus sur toi"
        }
    ]
}
```

---

## 4. Send Flirt Message
**Endpoint**: `POST /rest/users/{userId}/conversations/{conversationId}`
- **Headers**:
  - `Content-Type: application/vnd.jalf.convo.newmessage.flirt+json`
  - `x-csrftoken: {SUID}`

**Request Body**:
```json
{
    "flirt_link": "/rest/flirt/13",
    "ephemeral": false
}
```

**Response**: Returns the updated conversation object (same schema as [2. Get Conversation Details](#2-get-conversation-details)), containing the newly posted `"type": "flirt"` message in `messages`.

