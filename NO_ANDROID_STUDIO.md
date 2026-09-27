# RoboDrive Controller — Build WITHOUT Android Studio

You can build the APK from a browser using GitHub Actions.

1. Create a new GitHub repository.
2. Upload the complete contents of this `RoboDriveController` folder (not the parent folder).
3. Open the repository's **Actions** tab.
4. Select **Build RoboDrive Controller APK**.
5. Click **Run workflow**.
6. When the workflow finishes, open the completed run and download the artifact named **RoboDrive-Controller-debug-apk**.
7. Extract the artifact ZIP to get `app-debug.apk` and install that APK on the Android phone.

The workflow installs JDK 17, Android SDK platform 37, Build Tools 36.0.0, Gradle 9.6.0, and builds the debug APK in the cloud. Android Studio is not required on the computer doing the build.
