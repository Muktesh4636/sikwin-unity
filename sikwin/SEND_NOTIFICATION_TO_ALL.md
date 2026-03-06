# Send "Good morning" (or any message) to all users

The app subscribes every logged-in user to the FCM topic **`all_users`**. You can send one notification to everyone from Firebase Console.

## One-time: Send "Good morning" to all users

1. **Open Firebase Console**  
   https://console.firebase.google.com/ → select your project (the one used in `google-services.json`).

2. **Go to Cloud Messaging**  
   Left menu: **Engage** → **Messaging** (or **Build** → **Cloud Messaging**).

3. **Create a new campaign**  
   Click **Create your first campaign** or **New campaign** → choose **Firebase Notification messages**.

4. **Compose the notification**  
   - **Notification title:** `Good morning`  
   - **Notification text:** e.g. `Have a great day!` or leave blank for just the title.  
   Click **Next**.

5. **Target:** choose **Topic**  
   - Select **Topic** (not "Single device" or "User segment").  
   - Enter topic name: **`all_users`**  
   (This matches the topic the app subscribes to.)  
   Click **Next**.

6. **Schedule:** send immediately or set a time (e.g. morning).  
   Click **Next** → Review → **Publish**.

Everyone who has opened the app at least once while logged in (and has FCM enabled) is subscribed to `all_users` and will receive this notification.

## Notes

- Users must have opened the app at least once after you added `google-services.json` and the topic subscription, so they get subscribed to `all_users`.
- To send again later (e.g. daily "Good morning"), create another campaign in Messaging and target the topic **`all_users`** with the same steps.
