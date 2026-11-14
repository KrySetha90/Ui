MY ASSISTANT  (Android app: type a command, your phone does it)
================================================================

WHAT IT IS
  Screen 1: your 3D character in a teal card, a "hello...!" speech bubble,
            a text box + send arrow, a "+" button (model screen) and a
            "light" button (flashlight on/off).
  Screen 2: "upload 3d model here", "open folder", and a list of your saved models.
  Your Chisa .glb is built in as the default character.

----------------------------------------------------------------
1) GET THE APK  (pick one)
----------------------------------------------------------------
A) FROM YOUR PHONE ONLY (no computer): build it in the cloud, free
   1. Make a free account at github.com and create a new repository.
   2. Upload ALL files from this zip, keeping the folders
      (the hidden folder .github/workflows must come along).
   3. Open the "Actions" tab -> "Build APK" -> wait about 5-8 minutes.
   4. Open the finished run -> download "MyAssistant-apk" -> unzip -> install app-debug.apk.
      (Android will ask you to allow "install unknown apps" for your browser.)

B) ON A COMPUTER with Android Studio
   File -> Open -> choose this folder -> wait for Gradle sync ->
   Build -> Build APK(s) -> the file is in app/build/outputs/apk/debug/

NOTE: this project was written without being compiled on my side. If the build
shows an error, copy the red error text and send it to me; it is usually a
one-line fix.

----------------------------------------------------------------
2) TURN ON THE "HANDS" (one time)
----------------------------------------------------------------
Back / home / scroll / click / type need Android's Accessibility permission.
Settings -> Accessibility -> Installed apps -> "My Assistant" -> turn on.
(Or just type "back" in the app and it opens that screen for you.)
Android shows a scary warning. That is normal for any app that controls the phone.
The app only acts when YOU type a command, and has no analytics.

----------------------------------------------------------------
3) COMMANDS
----------------------------------------------------------------
Apps       open youtube / open whatsapp / open <ANY installed app name>
           open example.com
Search     search best pizza      open youtube and search cats
Media      play lofi music (searches YouTube)
Phone      light | light on | light off | volume up/down/max/mute | volume 40
           alarm 7:30 am | timer 5 min | camera | settings | bluetooth | wifi
People     call 012345678 (opens dialer) | text 012345678 hello
Maps       navigate to Angkor Wat
Hands*     back | home | recents | notifications | lock | screenshot
           scroll down | scroll up | click Subscribe | type hello world
Chaining   open youtube then search cats then scroll down  (steps run 1.8 s apart)
           * needs the Accessibility permission above

----------------------------------------------------------------
4) WHAT ANDROID DOES NOT ALLOW (honest limits)
----------------------------------------------------------------
- Wi-Fi / Bluetooth / mobile data cannot be switched by apps since Android 10.
  The app opens the right panel instead; you tap once.
- Calls open the dialer (no CALL_PHONE permission = safer); you press green.
- "click X" only works on buttons with visible text or a label, and not on
  banking or other protected screens.
- Voice is not included yet (you chose typing).

----------------------------------------------------------------
5) CHANGE THINGS
----------------------------------------------------------------
New commands       Commands.kt -> step(): copy any line starting with m("...")
Character framing  assets/avatar.html -> the two numbers marked 0.3 and 0.8
Colours            top of MainActivity.kt (Teal, Mint, Gray)
Default character  replace assets/models/chisa.glb (keep the name) or just upload in the app
Offline 3D         download model-viewer.min.js and put it in assets/. Without it the
                   character needs internet the first time (it loads from Google's CDN).
App name / icon    AndroidManifest.xml (android:label) / res/mipmap-xxxhdpi/ic_launcher.png

----------------------------------------------------------------
6) TROUBLESHOOTING
----------------------------------------------------------------
Character is blank      Check internet once (see Offline 3D). Big models (20 MB+) take a few seconds.
"Turn on My Assistant"  Accessibility permission is off (step 2).
"light" does nothing    Phone has no flash, or another app is using the camera.
Can't find an app       Say the name as shown under its icon.

----------------------------------------------------------------
7) IDEAS FOR NEXT VERSION
----------------------------------------------------------------
Voice input; AI that understands any sentence (needs an API key); character
that reacts (smile, nod) to replies; read notifications aloud; custom routines
like "good morning" = light off + alarm + open news.
