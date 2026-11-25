# Optional browser check

Manual dispatch only: Actions -> "Browser files check (optional)". It starts the seeded
pod from `deploy/test/pod.sh`, then runs Playwright against it.

Locally:

```sh
cd deploy/test/browser
npm install
PYLON_PASSWORD=<fixture password> npm test   # expects a pod already up on :19119
```

Covers the transport contract (sign-in, upload, list, download, sha256) and, when the app
dist is built, that the page boots. Does not cover the canvas UI: Compose renders into a
single canvas, so the picker and row actions have no selectors.
