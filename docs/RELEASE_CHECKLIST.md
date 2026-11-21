# Release checklist — blocker until completed

- [ ] Android Studio sync, `assembleDebug`, `lintDebug`, `testDebugUnitTest` pass.
- [ ] `connectedDebugAndroidTest` passes on Android 16 and at least one older API.
- [ ] UI on Samsung and Pixel validated in portrait, dark mode, large font, TalkBack.
- [ ] Foreground-service battery/start/stop behavior validated under Doze, forced process death and notification denial.
- [ ] Original v1.1 history migrates through an **in-place signed upgrade** and is retained without loss.
- [ ] Old malformed-history file can be exported, import warning and recovery are understandable.
- [ ] Privacy disclosure, local-only backup claim, deletion policy and data security review approved.
- [ ] `specialUse` foreground-service eligibility reviewed against Google Play restrictions if publishing there.
- [ ] Original signing keystore/Play App Signing lineage available; **never commit the key to source**.
- [ ] Build release AAB/APK with key, validate signature certificate identity, install and smoke-test it.
- [ ] Version code increment, screenshots and release notes prepared.

Current outcome: **NOT READY FOR PRODUCTION** (source implementation candidate only).
