#!/bin/sh
# Builds the desktop app and installs it for the current user: the app in
# ~/.local/share/journal-desktop and a launcher entry, so it shows up in the app menu.
# Run again to update. Remove with: rm -r ~/.local/share/journal-desktop ~/.local/share/applications/nl.markbroeders.journal.desktop
set -e
cd "$(dirname "$0")/.."
./gradlew :desktop:createDistributable -q

dest="${XDG_DATA_HOME:-$HOME/.local/share}"
app="$dest/journal-desktop"
rm -rf "$app"
mkdir -p "$app" "$dest/applications"
cp -r desktop/build/compose/binaries/main/app/journal/. "$app/"

# On Wayland the app is a native Wayland window (bundled JetBrains Runtime). Under X11,
# _JAVA_AWT_WM_NONREPARENTING makes Java follow window resizes in tiling window managers (i3);
# it does no harm elsewhere.
cat > "$dest/applications/nl.markbroeders.journal.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Journal
Comment=Write daily notes in your Obsidian vault
Exec=env _JAVA_AWT_WM_NONREPARENTING=1 "$app/bin/journal"
Icon=$app/lib/journal.png
Categories=Office;
StartupWMClass=nl.markbroeders.journal.desktop.MainKt
DESKTOP
echo "Installed. Start Journal from the app menu, or run: $app/bin/journal"
