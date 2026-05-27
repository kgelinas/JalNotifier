# JALF REST API: Conversations

This document describes the endpoints for listing conversation threads and managing detailed message histories.

## 1. List Conversations
**Endpoint**: `GET /rest/users/{userId}/conversations/{folder}`
- `{folder}` can be `active`, `new`, or `archived`.

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
    "can_post": "yes", // "yes" if the user can send a reply, "no" otherwise
    "messages_count": true, // (Unknown flag, usually true)
    "folder_link": "/rest/users/USERID/conversations/active", // REST link to the category this thread belongs to (active/new/archived)
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
            "type": "text", // Message type: "text" or "photo"
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
        }
    ]
}
```
