package com.nexo.app.data

import android.content.Context

/**
 * Persists which gym the signed-in user last had selected, so relaunching
 * the app after Phase 3's auto-select doesn't silently jump back to
 * whichever gym happens to sort first. An interface (rather than a raw
 * `SharedPreferences` dependency on [com.nexo.app.ui.SessionViewModel])
 * so gym-selection logic stays unit-testable in a plain JVM test, the
 * same fake/mock convention as [com.nexo.app.data.repository.BackendRepository].
 */
interface SessionStore {
    fun getLastGymId(): String?
    fun setLastGymId(gymId: String?)
}

class SharedPreferencesSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("nexo_session", Context.MODE_PRIVATE)

    override fun getLastGymId(): String? = prefs.getString(KEY_LAST_GYM_ID, null)

    override fun setLastGymId(gymId: String?) {
        prefs.edit().putString(KEY_LAST_GYM_ID, gymId).apply()
    }

    private companion object {
        const val KEY_LAST_GYM_ID = "last_gym_id"
    }
}
