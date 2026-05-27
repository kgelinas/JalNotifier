# JALF REST API: Server-Sent Events (SSE)

JALF uses Server-Sent Events to provide real-time updates for messages, profile visits, and synchronization of unread counts.

## Endpoint
**URL**: `GET /rest/sse` (or derived from metadata)  
**Headers**:
- `Accept: text/event-stream`
- `Cookie`: Valid session cookie
- `x-csrftoken`: SUID token

---

## Event Types

### 1. Messaging Events
These events typically trigger a refresh of conversation lists and unread badges.

#### `message`
Sent when a new message is received in any thread.
```json
{
    "conversation_link": "/rest/users/USERID/conversations/CONVID",
    "message_link": "/rest/users/USERID/conversations/messages/MSGID",
    "source": {
        "user_link": "/rest/users/SENDERID"
    }
}
```

#### `convo_typing`
Sent when a user starts typing in a conversation.
```json
{
    "conversation_link": "/rest/users/USERID/conversations/CONVID",
    "source": {
        "user_link": "/rest/users/SENDERID"
    }
}
```

#### `convo_all_unread_messages_count_changed`
Sent when the global unread count changes (e.g., after reading a message on another device).
```json
{
    "total_unread_messages_count": 5
}
```

Other messaging events: `convo_new`, `convo_archived`, `convo_deleted`.

---

### 2. Social & Presence Events

#### `looked`
Sent when another user visits your profile.
```json
{
    "source": {
        "user_link": "/rest/users/VISITORID"
    }
}
```

#### `user_stats_changed`
Sent when general user statistics (favorites count, visits count, etc.) are updated.
```json
{
    "stats": {
        "favorites_count": 10,
        "lookers_count": 5
    }
}
```

#### `login` / `joined`
Sent when contacts or followed users log in or join the platform.
```json
{
    "source": {
        "user_link": "/rest/users/USERID"
    }
}
```

---

## Client Handling Logic

The JALF Android app (`JalfSseService.java`) handles these events as follows:

1.  **Heartbeat Filtering**: Ignores any data containing "heartbeat".
2.  **Global Debounce**: Any non-heartbeat event triggers a debounced (2s) `JalfNotificationTask` to ensure the local database and badges remain in sync with the server.
3.  **UI Refresh**:
    *   **Messaging events**: Trigger `fetchAllUnreadCounts()` in `MainActivity`.
    *   **Social events**: Trigger `fetchEvents()` and `fetchFavorites()`.
4.  **Instant Notifications**:
    *   `convo_typing`: Displays a local "is typing..." notification.
    *   `looked`: Displays a "Profile visited" notification.
5.  **Auto-Messaging**: `looked` events trigger a check for queued targeted auto-messages.
