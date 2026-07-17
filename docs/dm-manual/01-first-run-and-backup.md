# First Run & Backup

## Starting the Application

```bash
java -jar dmhelper-<version>.jar
```

The application starts an embedded server (default port 8080). Open `http://localhost:8080` in a browser.

## PIN Setup

On first access, you are prompted to set a numeric PIN. This PIN is required for all subsequent sessions. There is no password recovery — store the PIN securely.

## Data Directory

All campaign data, configuration, and embedded database files live under:

- **Linux/macOS:** `~/.dmhelper/`
- **Windows:** `%USERPROFILE%\.dmhelper\`

The directory is created automatically on first start.

## Startup Backup

Every time the application starts, it creates a timestamped ZIP backup of the data directory under `~/.dmhelper/backups/`. Backups are rotated — only the 10 most recent are kept.

To restore a backup, see [Offline & Troubleshooting](06-offline-and-troubleshooting.md).
