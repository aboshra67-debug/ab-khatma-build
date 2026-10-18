# P84 / VC108 APK review

User requested an APK for personal review before any AAB or Play publication.
Baseline: P83 / VC107, commit40aa707d0e5b8d152a04bfe4f2260581d630ddc0.

Bounded change: add an offline, scrollable Arabic privacy disclosure under
My account, with a user-initiated email request for account deletion. Contact
uses the developer email already public in this repository:aboshra67@gmail.com.
The owner must review the contact and disclosure before public distribution.
No email is sent by the app; the user reviews and sends it in their email app.
No account or report is changed when opening this page.

Only the profile entry and version metadata change; one new Compose file is
added. Authentication, Google sign-in, sessions, existing account deletion,
notifications, audio, pages, preferences and data are retained byte-for-byte.
No new dependency or Android permission is introduced. The privacy page makes
no network request and opens no device permission prompt.

The retained session tests and release APK build must pass. AAB generation is
deliberately absent from this workflow. This is a review build, not Play approval.

Public privacy hosting/Data Safety, the Google external-deletion web resource,
recording rights and actual phone checks still require completion before Play.
The old source audit file still says recording rights are unverified; streaming
does not itself supply a licence. Do not remove working audio without discussion.

Useful primary references:
- https://developer.android.com/reference/android/speech/SpeechRecognizer
- https://support.google.com/googleplay/android-developer/answer/13327111
- https://quranicaudio.com/about

Packaging must use the original owner signing key locally only. Never put a
keystore, private setup archive or password in Git or Safe Source.
