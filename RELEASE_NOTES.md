# Release Notes

## App Rename to Numbawang
The app has been renamed from Tuntivelho to Numbawang to respect trademark guidelines.

**Important Migration Warnings:**
1. **applicationId change**: The application ID has changed to `com.numbawang.leimaus`. Android treats this as a NEW app. You must uninstall the old app. Stored credentials and history will be lost on Android.
2. **Room database**: The database filename (`tuntivelho_database`) is kept to preserve punch history if possible, with a comment explaining this decision.
3. **Preferences**: DataStore/EncryptedSharedPreferences names are kept as-is to preserve saved credentials, with a comment explaining this.
4. **Notification channels**: Notification channel IDs have been changed to `numbawang_notification`. Your per-channel notification settings in Android will be reset.
