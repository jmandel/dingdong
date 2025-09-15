# DingDong - Android Notification Breakthrough

Break through silent mode for important calls and notifications on Android.

## Features

- **Breakthrough Silent Mode**: Plays custom alert sounds even when phone is on silent/vibrate
- **Smart Filtering**: Match calls and notifications by:
  - Phone numbers (partial matching)
  - App names (supports both display names and package names)
  - Notification content (keywords and phrases)
- **36 Built-in Sounds**: High-quality notification sounds from the akx/Notifications collection
- **Custom Sounds**: Add your own MP3 files
- **Advanced Configuration**:
  - Minimum volume level (1-100%)
  - Volume restore delay (0-10 seconds)
  - Sound repeat count (1-5 times)
  - Wake lock duration (1-20 seconds)
  - Vibration toggle
  - Volume restoration toggle
- **Event Logging**:
  - Track all calls and notifications
  - See what matched and what triggered alerts
  - Configurable retention (1 hour to 7 days)
  - Enable/disable logging for privacy

## How It Works

1. **Simple String Matching**: Enter text patterns (one per line) to match against
   - Text is normalized to lowercase alphanumeric only
   - Spaces and special characters are ignored
   - Example: "John Smith" app matches search "nsmi"

2. **Multiple Filters = OR Logic**:
   - Each line in a filter box is a separate pattern
   - If ANY pattern matches, the alert triggers

3. **App + Content Matching**:
   - Can require BOTH app name AND content to match
   - Or trigger on EITHER app name OR content match

## Installation

1. Build the APK:
   ```bash
   ./gradlew assembleDebug
   ```

2. Install on device:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Required Permissions

- **Notification Access**: To monitor incoming notifications
- **Phone State & Call Log**: To detect incoming calls
- **Post Notifications**: To show service notifications (Android 13+)

## Setup

1. Grant all required permissions through the app
2. Configure your filter patterns:
   - Phone numbers to alert on
   - App names to monitor
   - Keywords in notification content
3. Choose your alert sound
4. Adjust advanced settings as needed
5. Save settings

## Privacy

- All filtering happens locally on device
- No data is sent to external servers
- Event logs are stored locally and auto-deleted based on retention settings
- Logs can be disabled entirely for privacy

## Development

Built with:
- Kotlin
- Android SDK 35
- Material Design 3
- 36 open-source notification sounds from akx/Notifications

## License

Open source - includes notification sounds from the akx/Notifications repository.

---

🤖 Generated with [Claude Code](https://claude.ai/code)

Co-Authored-By: Claude <noreply@anthropic.com>
