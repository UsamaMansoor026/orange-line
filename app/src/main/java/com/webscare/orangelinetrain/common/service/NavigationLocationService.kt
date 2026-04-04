package com.webscare.orangelinetrain.common.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.webscare.orangelinetrain.MainActivity
import com.webscare.orangelinetrain.common.datastore.PreferenceDataStoreKeysConstants
import com.webscare.orangelinetrain.common.datastore.PreferencesDataStoreHelper
import com.webscare.orangelinetrain.common.enums.NavigationMode
import com.webscare.orangelinetrain.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class NavigationLocationService : Service() {

    companion object {
        const val ACTION_START = "nav_action_start"
        const val ACTION_STOP = "nav_action_stop"

        const val CHANNEL_ID = "nav_foreground_channel"
        const val NOTIF_ID = 5001

        const val ACTION_LOCATION = "nav_location_update"
        const val EXTRA_LAT = "lat"
        const val EXTRA_LNG = "lng"
        const val EXTRA_BEARING = "bearing"
        const val EXTRA_SPEED = "speed"
        const val EXTRA_ACCURACY = "accuracy"

        // ✅ Destination extras
        const val EXTRA_DEST_LAT = "dest_lat"
        const val EXTRA_DEST_LNG = "dest_lng"

        // ✅ State extras
        const val EXTRA_DISTANCE_LEFT = "distance_left"
        const val EXTRA_REACHED = "reached"
        const val EXTRA_ARRIVAL_SOON = "arrival_soon"

        // Broadcast when user exits navigation from notification
        const val ACTION_NAV_EXITED = "nav_action_exited"

        // Notification IDs
        const val NOTIF_ARRIVAL_SOON_ID = 6001
        const val NOTIF_REACHED_ID = 6002

        const val EXTRA_FROM_NOTIFICATION = "from_notification"
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private var callback: LocationCallback? = null

    private var destLat = 0.0
    private var destLng = 0.0

    private var arrivalSoonNotified = false
    private var reachedNotified = false

    // thresholds (tune)
    private val ARRIVAL_SOON_METERS = 150.0
    private val REACHED_METERS = 45.0


    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {

            ACTION_START -> {
                // ✅ read destination coords
                Log.d("train", "Service Started")
                destLat = intent.getDoubleExtra(EXTRA_DEST_LAT, 0.0)
                destLng = intent.getDoubleExtra(EXTRA_DEST_LNG, 0.0)

                // reset flags on every start
                arrivalSoonNotified = false
                reachedNotified = false

                startForeground(NOTIF_ID, buildForegroundNotification("Navigation running"))
                startUpdates()
            }

            ACTION_STOP -> {
                // User explicitly exited navigation from notification
                // 1) Clear persisted navigation state so app doesn't restore NAVIGATING
                Log.d("train", "Service Stopped")
                val fromNotification = intent.getBooleanExtra(EXTRA_FROM_NOTIFICATION, false)

                if (fromNotification) {
                    val exitAppIntent = Intent(this, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("EXIT_NAV", true)
                    }
                    startActivity(exitAppIntent)
                }

                CoroutineScope(Dispatchers.IO).launch {
                    val prefs = PreferencesDataStoreHelper(applicationContext)
                    prefs.putPreference(PreferenceDataStoreKeysConstants.NAVIGATION_MODE, NavigationMode.ROUTE_PREVIEW.name)
//                    prefs.putPreference(PreferenceDataStoreKeysConstants.DEST_LAT, "")
//                    prefs.putPreference(PreferenceDataStoreKeysConstants.DEST_LNG, "")
//                    prefs.putPreference(PreferenceDataStoreKeysConstants.NAV_ROUTE_ID, "")
//                    prefs.putPreference(PreferenceDataStoreKeysConstants.NAV_FROM_ID, "")
//                    prefs.putPreference(PreferenceDataStoreKeysConstants.NAV_TO_ID, "")
//                    prefs.putPreference(PreferenceDataStoreKeysConstants.NAV_INDEX, "0")
                }

                sendBroadcast(Intent(ACTION_NAV_EXITED).apply {
                    setPackage(packageName)
                })

//                val exitAppIntent = Intent(this, MainActivity::class.java).apply {
//                    // This is the CRITICAL flag to fix the crash
//                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
//
//                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
//
//                    putExtra("EXIT_NAV", true)
//                }
//                startActivity(exitAppIntent)
                stopUpdates()
                stopForeground(true)
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startUpdates() {

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 800L)
            .setMinUpdateDistanceMeters(2f)
            .build()

        val cb = object : LocationCallback() {
            @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
            override fun onLocationResult(result: LocationResult) {
                val loc = result.lastLocation ?: return

                val distLeft = if (destLat != 0.0 && destLng != 0.0) {
                    distanceMeters(loc.latitude, loc.longitude, destLat, destLng)
                } else {
                    Double.MAX_VALUE
                }

                val arrivalSoon = distLeft <= ARRIVAL_SOON_METERS
                val reached = distLeft <= REACHED_METERS

                // 🔔 arrival soon notification
                if (!arrivalSoonNotified && arrivalSoon && !reached) {
                    arrivalSoonNotified = true
                    sendArrivalSoonNotification()
                }

                // 🔔 reached notification
                if (!reachedNotified && reached) {
                    reachedNotified = true
                    sendReachedDestinationNotification()
                }

                // optional: update foreground notification text
                if (reached) {
                    updateForegroundNotification("Destination reached")
                } else {
                    val formattedDistance = formatDistance(distLeft)
                    updateForegroundNotification("Distance left: $formattedDistance")
                }

                broadcastLocation(loc, distLeft, reached, arrivalSoon)
            }
        }

        callback = cb
        fusedClient.requestLocationUpdates(request, cb, mainLooper)
    }

    private fun stopUpdates() {
        callback?.let { fusedClient.removeLocationUpdates(it) }
        callback = null
    }

    private fun broadcastLocation(
        loc: Location,
        distLeft: Double,
        reached: Boolean,
        arrivalSoon: Boolean
    ) {
        val i = Intent(ACTION_LOCATION).apply {
            putExtra(EXTRA_LAT, loc.latitude)
            putExtra(EXTRA_LNG, loc.longitude)
            putExtra(EXTRA_BEARING, loc.bearing)
            putExtra(EXTRA_SPEED, loc.speed)
            putExtra(EXTRA_ACCURACY, loc.accuracy)

            putExtra(EXTRA_DISTANCE_LEFT, distLeft)
            putExtra(EXTRA_REACHED, reached)
            putExtra(EXTRA_ARRIVAL_SOON, arrivalSoon)
        }
        sendBroadcast(i)
    }

    private fun buildForegroundNotification(text: String): Notification {
        // Action to exit navigation from the notification
        val stopIntent = Intent(this, NavigationLocationService::class.java).apply {
            action = ACTION_STOP
            putExtra(EXTRA_FROM_NOTIFICATION, true)
        }

        val stopPendingIntentFlags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT

        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            stopPendingIntentFlags
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.webscare.orangelinetrain.R.drawable.ic_nav_arrow)
            .setContentTitle("Train Map Navigation")
            .setContentText(text)
            .setOngoing(true)
            // Extra hardening so OEMs/launchers don't allow swipe dismiss
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(
                R.drawable.ic_nav_arrow,
                "Exit navigation",
                stopPendingIntent
            )
            .build()
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun updateForegroundNotification(text: String) {
        val notif = buildForegroundNotification(text)
        NotificationManagerCompat.from(this).notify(NOTIF_ID, notif)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun sendArrivalSoonNotification() {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_arrow)
            .setContentTitle("Almost there!")
            .setContentText("Arriving at destination soon.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(NOTIF_ARRIVAL_SOON_ID, notif)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    private fun sendReachedDestinationNotification() {
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_nav_arrow)
            .setContentTitle("Destination Reached")
            .setContentText("You have arrived at your destination.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(this).notify(NOTIF_REACHED_ID, notif)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Navigation Tracking",
                NotificationManager.IMPORTANCE_LOW
            )
            ch.description = "Live navigation tracking"

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(ch)
        }
    }

    private fun formatDistance(meters: Double): String {
        val unit = runBlocking {
            val prefs = PreferencesDataStoreHelper(applicationContext)
            prefs.getPreference(PreferenceDataStoreKeysConstants.DISTANCE_UNIT, "km").first()
        }

        return if (unit == "mi") {
            val miles = meters * 0.000621371
            if (miles < 0.1) "${(miles * 5280).roundToInt()} ft"
            else String.format("%.2f mi", miles)
        } else {
            if (meters < 1000) "${meters.roundToInt()} m"
            else String.format("%.2f km", meters / 1000.0)
        }
    }

    private fun distanceMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)

        val a =
            sin(dLat / 2).pow(2.0) +
                    cos(Math.toRadians(lat1)) *
                    cos(Math.toRadians(lat2)) *
                    sin(dLon / 2).pow(2.0)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    override fun onDestroy() {
        stopUpdates()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
