#!/bin/bash

# Setup notification sounds from akx/Notifications repository

cd app/src/main/res/raw/

# Remove existing files
rm -f *.mp3 *.ogg

# Clone the repository temporarily
git clone --depth 1 https://github.com/akx/Notifications.git temp_notifications

# Convert all OGG files to MP3 with proper naming
echo "Converting OGG files to MP3..."
for ogg_file in temp_notifications/OGG/*.ogg; do
    if [ -f "$ogg_file" ]; then
        filename=$(basename "$ogg_file" .ogg)
        # Convert to lowercase and replace spaces/special chars with underscore
        clean_name=$(echo "$filename" | tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9]/_/g' | sed 's/__*/_/g' | sed 's/^_//;s/_$//')
        echo "Converting $filename to $clean_name.mp3"
        ffmpeg -i "$ogg_file" -acodec libmp3lame -ab 128k "${clean_name}.mp3" -y 2>/dev/null
    fi
done

# Clean up
rm -rf temp_notifications

# Generate Kotlin code for SoundManager
echo ""
echo "Generating Kotlin code for SoundManager.kt:"
echo "  val builtInSounds = listOf("

for mp3_file in *.mp3; do
    if [ -f "$mp3_file" ]; then
        # Get the base name without extension
        base_name=$(basename "$mp3_file" .mp3)
        # Create display name by capitalizing and replacing underscores
        display_name=$(echo "$base_name" | sed 's/_/ /g' | sed 's/\b\(.\)/\u\1/g')
        echo "    AlertSound(\"$base_name\", \"$display_name\", R.raw.$base_name),"
    fi
done | sed '$ s/,$//'  # Remove trailing comma from last line

echo "  )"

echo ""
echo "Total sounds: $(ls -1 *.mp3 2>/dev/null | wc -l)"