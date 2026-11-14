package com.myassistant.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import kotlinx.coroutines.delay

/** The "brain": turns what you type into phone actions. Add your own rules in step(). */
class Commands(private val c: Context) {
    private var torchOn = false
    private val help = "Try: open youtube · play lofi · light · volume up · alarm 7am · timer 5 min · navigate to Angkor Wat · call 012345678 · back · home · scroll down · click Subscribe"

    private fun go(i: Intent): Boolean = try { c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }
    private fun view(url: String, pkg: String? = null): Boolean {
        val i = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        if (pkg != null && go(Intent(i).setPackage(pkg))) return true
        return go(i)
    }
    private fun q(s: String) = Uri.encode(s)

    fun torch(on: Boolean?): String {
        val cm = c.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            ?: return "No flashlight found"
        val want = on ?: !torchOn
        return try { cm.setTorchMode(id, want); torchOn = want; if (want) "Light on" else "Light off" } catch (e: Exception) { "Can’t reach the flashlight" }
    }

    private fun volume(arg: String): String {
        val am = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val s = AudioManager.STREAM_MUSIC; val max = am.getStreamMaxVolume(s); val ui = AudioManager.FLAG_SHOW_UI
        val n = arg.filter { it.isDigit() }.toIntOrNull()
        when {
            "up" in arg -> am.adjustStreamVolume(s, AudioManager.ADJUST_RAISE, ui)
            "down" in arg -> am.adjustStreamVolume(s, AudioManager.ADJUST_LOWER, ui)
            "max" in arg -> am.setStreamVolume(s, max, ui)
            "mute" in arg -> am.setStreamVolume(s, 0, ui)
            n != null -> am.setStreamVolume(s, n.coerceIn(0, 100) * max / 100, ui)
            else -> return "Say: volume up, down, max, mute or a number"
        }
        return "Volume changed"
    }

    private fun a11y(ok: String, f: (AssistantService) -> Boolean): String {
        val s = AssistantService.instance
        if (s == null) { go(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); return "Turn on “My Assistant” in Accessibility, then try again" }
        return if (f(s)) ok else "I couldn’t do that on this screen"
    }

    private fun openTarget(name: String, bare: Boolean = false): String {
        val n = name.removePrefix("the ").removeSuffix(" app").trim()
        val pm = c.packageManager
        val list = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
        val hit = list.firstOrNull { it.loadLabel(pm).toString().lowercase() == n }
            ?: if (n.length >= 2) list.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(n) } else null
        if (hit != null) {
            pm.getLaunchIntentForPackage(hit.activityInfo.packageName)?.let { go(it) }
            return "Opening " + hit.loadLabel(pm)
        }
        if (Regex("^([a-z0-9-]+\\.)+[a-z]{2,}(/\\S*)?$").matches(n)) { view("https://$n"); return "Opening $n" }
        if (bare) return "I didn’t get that. $help"
        view("https://www.google.com/search?q=" + q(n))
        return "No app called “$n”. Searching Google instead"
    }

    private fun step(raw: String): String {
        val orig = raw.trim()
        val t = orig.lowercase().trimEnd('.', '!', '?')
        fun m(p: String) = Regex(p).find(t)

        m("^(?:open|launch|start)\\s+(.+?)\\s+and\\s+(?:search|find)\\s+(?:for\\s+)?(.+)$")?.let { r ->
            val app = r.groupValues[1]; val s = r.groupValues[2]
            return when {
                "youtube" in app -> { view("https://www.youtube.com/results?search_query=" + q(s), "com.google.android.youtube"); "YouTube: $s" }
                "map" in app -> { view("geo:0,0?q=" + q(s)); "Maps: $s" }
                "spotify" in app -> { view("spotify:search:" + q(s)); "Spotify: $s" }
                else -> { view("https://www.google.com/search?q=" + q(s)); "Google: $s" }
            }
        }
        m("^(?:turn |switch )?(on |off )?(?:the )?(?:flash ?light|torch|light)(?: (on|off))?$")?.let { r ->
            val s = (r.groupValues[1] + r.groupValues[2]).trim()
            return torch(if (s == "on") true else if (s == "off") false else null)
        }
        m("^volume\\s*(.*)$")?.let { return volume(it.groupValues[1]) }
        if (Regex("wi-?fi|internet|mobile data").containsMatchIn(t)) { go(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)); return "Opened the Wi‑Fi panel (Android doesn’t let apps flip it directly)" }
        if ("bluetooth" in t) { go(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); return "Opened Bluetooth settings" }
        if ("brightness" in t) { go(Intent(Settings.ACTION_DISPLAY_SETTINGS)); return "Opened display settings" }
        if (t == "settings" || t == "open settings") { go(Intent(Settings.ACTION_SETTINGS)); return "Opening Settings" }
        m("^(?:set )?(?:an? )?alarm(?: for| at)?\\s*(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)?$")?.let { r ->
            var h = r.groupValues[1].toInt(); val min = r.groupValues[2].ifEmpty { "0" }.toInt(); val ap = r.groupValues[3]
            if (ap == "pm" && h < 12) h += 12
            if (ap == "am" && h == 12) h = 0
            go(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h).putExtra(AlarmClock.EXTRA_MINUTES, min))
            return "Alarm set for %d:%02d".format(h, min)
        }
        m("^(?:set )?(?:a )?timer(?: for)?\\s*(\\d+)\\s*(sec|second|min|minute|hour)s?$")?.let { r ->
            val mult = when (r.groupValues[2]) { "hour" -> 3600; "min", "minute" -> 60; else -> 1 }
            go(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, r.groupValues[1].toInt() * mult).putExtra(AlarmClock.EXTRA_SKIP_UI, true))
            return "Timer started"
        }
        m("^(?:call|dial|phone)\\s+([+\\d][\\d\\s\\-]{2,})$")?.let { go(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + it.groupValues[1].replace(Regex("[^\\d+]"), "")))); return "Dialing (press the green button)" }
        m("^(?:sms|text)\\s+([+\\d][\\d\\-]{2,})(?:\\s+(.+))?$")?.let { r -> go(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + r.groupValues[1])).putExtra("sms_body", r.groupValues[2])); return "Text ready to send" }
        m("^(?:navigate|directions?|take me)(?: to)?\\s+(.+)$")?.let { view("geo:0,0?q=" + q(it.groupValues[1])); return "Finding ${it.groupValues[1]}" }
        m("^play\\s+(.+?)(?: on youtube)?$")?.let { view("https://www.youtube.com/results?search_query=" + q(it.groupValues[1]), "com.google.android.youtube"); return "Searching YouTube for ${it.groupValues[1]}" }
        if (t in listOf("camera", "open camera", "take photo", "take a photo")) { go(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); return "Opening camera" }

        when (t) {
            "back", "go back" -> return a11y("Went back") { it.global(AccessibilityService.GLOBAL_ACTION_BACK) }
            "home", "go home" -> return a11y("Went home") { it.global(AccessibilityService.GLOBAL_ACTION_HOME) }
            "recents", "recent apps" -> return a11y("Showing recent apps") { it.global(AccessibilityService.GLOBAL_ACTION_RECENTS) }
            "notifications", "open notifications" -> return a11y("Notifications open") { it.global(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS) }
            "lock", "lock screen" -> return a11y("Locked") { it.global(AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN) }
            "screenshot", "take screenshot" -> return a11y("Screenshot taken") { it.global(AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT) }
            "scroll down" -> return a11y("Scrolled down") { it.scroll(true) }
            "scroll up" -> return a11y("Scrolled up") { it.scroll(false) }
            "help", "?" -> return help
        }
        m("^(?:click|tap|press)\\s+(?:on\\s+)?(.+)$")?.let { r -> return a11y("Tapped “${r.groupValues[1]}”") { it.clickText(r.groupValues[1]) } }
        Regex("^type\\s+(.+)$", RegexOption.IGNORE_CASE).find(orig)?.let { r -> return a11y("Typed it") { it.typeText(r.groupValues[1]) } }

        m("^(?:open|launch|start|go to|visit)\\s+(.+)$")?.let { return openTarget(it.groupValues[1]) }
        m("^(?:search|google|find)\\s+(.+)$")?.let { view("https://www.google.com/search?q=" + q(it.groupValues[1])); return "Searching Google" }
        return openTarget(t, bare = true)
    }

    /** "open youtube then scroll down" runs both steps, 1.8s apart. */
    suspend fun run(raw: String): String {
        var out = ""
        raw.split(Regex("\\s+(?:and then|then)\\s+|\\s*;\\s*", RegexOption.IGNORE_CASE)).forEachIndexed { i, p ->
            if (i > 0) delay(1800)
            out = step(p)
        }
        return out
    }
}
