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

## How Filtering Works

### Text Matching Rules
- **Normalization**: All text (both filters and content) is converted to lowercase, alphanumeric only
- **Spaces/punctuation ignored**: "John Smith" becomes "johnsmith", "(555) 123-4567" becomes "5551234567"
- **Partial matching**: Filter "nsmi" matches "johnsmith" (contains substring)

### Multiple Line Filters (OR Logic)
Each text box accepts multiple filters, one per line. These are OR'd together:

**Phone Numbers Example:**
```
555           # Matches any number with "555"
212           # OR any number with "212"
8005551234    # OR this specific number
```

**App Names Example:**
```
slack         # Matches Slack app
whats         # OR WhatsApp (partial match)
teams         # OR Microsoft Teams
nsmi          # OR any app with "nsmi" in name (e.g., "John Smith")
```

**Notification Content Example:**
```
urgent        # Matches notifications with "urgent"
prod          # OR "production" (partial match)
down          # OR "down", "shutdown", etc.
emergency     # OR "emergency"
```

### Combining App and Content Filters

- **Default (Require BOTH unchecked)**:
  - Triggers if ANY app filter matches OR ANY content filter matches
  - Example: Will alert for ALL Slack notifications OR any app with "urgent" in content

- **Require BOTH checked**:
  - Triggers only if AT LEAST ONE app filter AND AT LEAST ONE content filter match
  - Example: Only alerts for Slack notifications that also contain "urgent"

### Examples

**Example 1: Monitor specific apps**
- App filters: `slack`, `teams`
- Content filters: (empty)
- Result: Alerts for ALL Slack and Teams notifications

**Example 2: Keyword monitoring across all apps**
- App filters: (empty)
- Content filters: `server`, `down`, `critical`
- Result: Alerts for ANY app with these keywords

**Example 3: Specific app + keyword**
- App filters: `slack`
- Content filters: `prod`, `urgent`
- Require BOTH: ✓
- Result: Only Slack messages containing "prod" or "urgent"

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
