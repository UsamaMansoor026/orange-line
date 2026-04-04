package com.webscare.orangelinelahore.common.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.webscare.orangelinelahore.common.datastore.PreferenceDataStoreKeysConstants
import com.webscare.orangelinelahore.common.datastore.PreferencesDataStoreHelper
import com.webscare.orangelinelahore.common.enums.NavigationMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * On device reboot, we intentionally clear navigation state so the app does NOT
 * think navigation is still running (service won't be restarted on boot).
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != "android.intent.action.LOCKED_BOOT_COMPLETED") return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = PreferencesDataStoreHelper(context.applicationContext)
                prefs.putPreference(PreferenceDataStoreKeysConstants.NAVIGATION_MODE, NavigationMode.IDLE.name)
                prefs.putPreference(PreferenceDataStoreKeysConstants.DEST_LAT, "")
                prefs.putPreference(PreferenceDataStoreKeysConstants.DEST_LNG, "")
                prefs.putPreference(PreferenceDataStoreKeysConstants.NAV_ROUTE_ID, "")
                prefs.putPreference(PreferenceDataStoreKeysConstants.NAV_FROM_ID, "")
                prefs.putPreference(PreferenceDataStoreKeysConstants.NAV_TO_ID, "")
                prefs.putPreference(PreferenceDataStoreKeysConstants.NAV_INDEX, "0")
            } finally {
                pending.finish()
            }
        }
    }
}


