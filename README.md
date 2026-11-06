# Lost & Found System | نظام المفقودات والموجودات

An **Arabic-first Android lost-and-found management application** that helps members of a university community report missing or found belongings, search reports, review possible matches, and coordinate verified returns through a lost-and-found center.

> **Project context:** The current code uses a university ID / `@ksu.edu.sa` email convention and includes university-themed interface assets. This repository represents a software project, not a claim of official university endorsement.

## Features

- **Lost and found reports:** Submit detailed reports with category, item name, description, location, date, and an optional photo.
- **Arabic and right-to-left UI:** Arabic text and RTL layout support throughout the app.
- **Search and filtering:** Browse lost and found items and filter reports.
- **Matching suggestions:** Compare lost and found reports by weighted category, item name, location, date, and description similarity.
- **Review workflow:** Review, approve, or reject reports and suggested matches; handle claims and disputes when an item is not the correct match.
- **Role-based screens:** Separate experiences for community members, administrators, and lost-and-found center staff.
- **Center handover:** Track items received at the center, ownership verification, and return confirmation.
- **Report tracking:** View submitted reports, status updates, and activity-based in-app notifications.
- **Profiles and help:** Profile management, password-reset emails, and FAQs.

## Matching logic

The matching algorithm is implemented in `MatchScore.java`. When comparison data is available, its configured field weights are:

| Field | Weight |
| --- | ---: |
| Category | 25% |
| Item name | 25% |
| Location | 20% |
| Date | 15% |
| Description | 15% |

The matching score is normalized over available fields. A score of **75% or higher** is labeled high, **50–74%** medium, and **30–49%** low; values below 30% are not shown as suggestions. Additional guards in the source code can limit suggestions when there is insufficient evidence.

## Tech stack

| Area | Technology |
| --- | --- |
| Platform | Android (native) |
| Language | Java 11 compatibility |
| User interface | Android XML, Material Components, AndroidX |
| Authentication | Firebase Authentication (email/password) |
| Database | Cloud Firestore |
| Image handling | Local image compression and Base64 encoding into report data |
| Build | Gradle Kotlin DSL, Gradle Wrapper |
| Testing | JUnit, AndroidX Test / Espresso |

**Project configuration (as currently declared):** `minSdk 24`, `targetSdk 37`, `compileSdk 37`, Firebase BoM `33.10.0`, Android Gradle Plugin `9.4.0`, Gradle Wrapper `9.6.0`.

## Getting started

1. **Clone** this repository and open its root directory (the folder containing `settings.gradle.kts`) in Android Studio.
2. Install the Android SDK version and development tools needed by the project's Gradle/Android plugin configuration. Allow Gradle sync to finish.
3. In the [Firebase Console](https://console.firebase.google.com/), create or select a Firebase project. Register an **Android app** with package/application ID:
   ```text
   com.example.lostandfoundsystem
   ```
4. Download that project's `google-services.json` and place it **locally** at `app/google-services.json`. This configuration file is intentionally not included in the public repository and is covered by `.gitignore`.
5. Enable **Email/Password** sign-in in Firebase Authentication and create suitable test accounts. The supplied login screen signs in existing accounts; it does not include a public self-registration flow.
6. Create a **Cloud Firestore** database and configure appropriate access rules. At minimum, review the `users`, `lost_items`, and `found_items` collections used by the app; do not grant unrestricted public access.
7. Run the application on an Android emulator or a connected Android device.

The login form converts an entered university ID into an email using the configured `@ksu.edu.sa` suffix. An explicit email address can also be entered. Authorized staff roles are read from the signed-in user's Firestore document; assign roles through a trusted administrative workflow and enforce permissions **server-side with Firestore Security Rules**.

### Build from Windows CMD

```bat
cd path\to\lostandfoundsystem
gradlew.bat assembleDebug
```

Run available unit tests with:

```bat
gradlew.bat testDebugUnitTest
```

These are **commands to run locally**; a successful build or test run is not claimed here.

## Project layout

```text
lostandfoundsystem/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/example/lostandfoundsystem/
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   ├── test/
│   │   └── androidTest/
│   └── build.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── .gitignore
└── README.md
```

Notable source files include `ReportItemActivity.java` (report submission), `MatchScore.java` and `Matcher.java` (match scoring/suggestions), `MatchOps.java` (match workflow), `AdminDashboardActivity.java`, `CenterDashboardActivity.java`, `CenterOps.java`, `SessionRouter.java`, and `Db.java` (Firestore collection constants).

## Report lifecycle

The app defines report statuses such as `Pending`, `Under Review`, `Approved`, `Rejected`, `Matched`, `Verified`, `At Center`, and `Returned`. Which transitions are available depends on the report state and the user's role.

## Public repository notes

- Generated build files, Gradle/IDE caches, machine-local settings, debug APKs, and an extra source ZIP were left out of this GitHub-ready archive.
- The Firebase configuration file and common signing keys/secret files are excluded. **Never upload service-account JSON, tokens, passwords, or private signing keys.** Check any new files before committing.
- The original local Tajawal font binaries are **not included** in this public archive. References to them have been replaced with the Android system sans-serif font so the resources are self-contained. This changes typography only; if you need the original typeface locally, restore your original assets and font references in your private working copy after checking their redistribution terms.
- Firebase configuration and security rules are required for real operation; they are not generated or deployed by this repository.
- This project contains no open-source license file. Review licensing/ownership before reusing or redistributing its code and visual assets.

## Potential future enhancements

- Add automated integration tests for role-based workflows and Firestore rules.
- Add a release pipeline and publish screenshots of the main user flows.
- Separate environment configuration for development and production Firebase projects.

---

**Project status:** GitHub-ready source archive; Android build, runtime, and backend rules were not independently verified.
