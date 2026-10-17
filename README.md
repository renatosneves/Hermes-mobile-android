# Hermes APK builds

Debug builds for sideloading on the Fold. Not signed for release.

This branch keeps only the **last 5 betas** and is rebuilt as a single commit each time a new beta is added, so old APKs do not pile up in git history. Every build is also archived in the Claude project files (`hermes-app/`).

Download: `https://raw.githubusercontent.com/renatosneves/Hermes-mobile-android/apk-builds/hermes-mobile-v2.1.0-beta.<N>.apk`

- `hermes-mobile-v2.1.0-beta.49.apk` (latest): push notifications via UnifiedPush/ntfy.
- `hermes-mobile-v2.1.0-beta.48.apk`: checks for new bot messages every 15 minutes while the app is closed.
- `hermes-mobile-v2.1.0-beta.47.apk`: cron replies reload the chat history.
- `hermes-mobile-v2.1.0-beta.46.apk`: camera photo fix.
- `hermes-mobile-v2.1.0-beta.45.apk`: reply to a message, local reactions, yellow toy buttons.

## Adding a new beta

1. Start from the current branch tree, add the new APK, delete the oldest so 5 remain, and update this list.
2. Commit it as a new root commit (`git checkout --orphan`) and `git push --force origin apk-builds`.

## Server

- `server/install-hermes-push.sh`: one-line installer for the hermes-push plugin:
  `curl -fsSL https://raw.githubusercontent.com/renatosneves/Hermes-mobile-android/apk-builds/server/install-hermes-push.sh | sh`
- `server/hermes-push.tar.gz`: the plugin itself.
