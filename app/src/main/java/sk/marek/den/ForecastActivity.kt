package sk.marek.den

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import java.util.Locale

class ForecastActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Resolve at tap time so revoked location access or disabled weather never shares cached coordinates.
        val prefs = getSharedPreferences("day", MODE_PRIVATE)
        val weather = if (hasLocation() && prefs.getBoolean("weather_enabled", false)) cachedWeather(this) else null
        val url = weather?.takeIf { it.latitude.isFinite() && it.longitude.isFinite() &&
            it.latitude in -90.0..90.0 && it.longitude in -180.0..180.0 }?.let {
            String.format(Locale.US, "https://www.windy.com/%.2f/%.2f", roundedCoordinate(it.latitude), roundedCoordinate(it.longitude))
        } ?: "https://www.windy.com/"
        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
        catch (_: ActivityNotFoundException) { Toast.makeText(this, "Na otvorenie predpovede potrebuješ webový prehliadač.", Toast.LENGTH_LONG).show() }
        finally { finish() }
    }
}
