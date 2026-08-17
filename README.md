# Legal Case Manager — Phase 1

Private, offline-first Android APK for personal/workspace case management.

## Phase 1 features

- Local SQLite database on the phone
- Add/edit/delete cases
- Current/new case number
- Multiple old/previous case numbers
- Offline CNR format validation
- e-Jagriti reference number field
- Source field (Manual / eCourts / e-Jagriti)
- Court, state, district, case type and party information
- Advocate information
- Status and priority
- Filing, registration and next hearing dates
- Local notes
- Hearing records
- Local tasks
- Document attachment using Android's document picker
- Local case search across case number, old number, CNR, parties, advocate, court and district
- GitHub Actions workflow that builds a debug APK

## Important CNR behavior

Phase 1 validates the CNR format locally only:
- exactly 16 characters
- alphanumeric
- spaces/hyphens are normalized

A valid format does **not** prove that the CNR exists in eCourts. Existence/remote verification belongs to Phase 2.

## Build in GitHub

1. Create a **private** GitHub repository.
2. Upload all files/folders from this project.
3. Push to the `main` branch.
4. GitHub Actions will run automatically.
5. Open the repository's **Actions** tab.
6. Open the latest **Build Android APK** run.
7. Under **Artifacts**, download `LegalCaseManager-debug-apk`.
8. Extract the ZIP and install `app-debug.apk` on your Android phone.

You can also run the workflow manually:
Actions → Build Android APK → Run workflow.

## Local-only design

Phase 1 does not use:
- Firebase
- AWS
- a cloud database
- a web server
- a login system
- Play Store publication

All structured case data is stored in the app's private SQLite database on the device. Attached documents remain referenced through Android document URIs.

## Phase roadmap

### Phase 1
Local case management and offline storage.

### Phase 2
eCourts + e-Jagriti integration and local import/update.

### Phase 3
Automatic synchronisation, change detection, notifications and background update engine.
