package com.webscare.orangeline.common.datastore

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object PreferenceDataStoreKeysConstants {
    val FIRST_RUN = booleanPreferencesKey("FIRST_RUN")
    val DARK_MODE = booleanPreferencesKey("DARK_MODE")

    // 🌐 Language
    val LANGUAGE = stringPreferencesKey("LANGUAGE")
    val MAP_VIEW  = stringPreferencesKey("MAP_VIEW ")

    // 📏 Distance Unit
    val DISTANCE_UNIT = stringPreferencesKey("DISTANCE_UNIT")

    // ⏰ Time Format
    val TIME_FORMAT = stringPreferencesKey("TIME_FORMAT")

    // 🚗 Navigation Mode & Location
    val NAVIGATION_MODE = stringPreferencesKey("NAVIGATION_MODE")  // NAVIGATING, ROUTE_PREVIEW, IDLE
    val DEST_LAT = stringPreferencesKey("DEST_LAT")
    val DEST_LNG = stringPreferencesKey("DEST_LNG")

    // 🚗 Navigation restore state
    val NAV_ROUTE_ID = stringPreferencesKey("NAV_ROUTE_ID")
    val NAV_FROM_ID = stringPreferencesKey("NAV_FROM_ID")
    val NAV_TO_ID = stringPreferencesKey("NAV_TO_ID")
    val NAV_INDEX = stringPreferencesKey("NAV_INDEX")
}