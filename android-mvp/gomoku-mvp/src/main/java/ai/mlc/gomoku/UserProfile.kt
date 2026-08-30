package ai.mlc.gomoku

import android.content.Context

class UserProfile(context: Context) {
    private val prefs = context.getSharedPreferences("player_profile", Context.MODE_PRIVATE)

    fun name(): String = prefs.getString("name", "棋友") ?: "棋友"
    fun isSignedIn(): Boolean = prefs.getBoolean("signed_in", false)
    fun signIn(name: String) {
        prefs.edit().putBoolean("signed_in", true).putString("name", name.trim().ifBlank { "棋友" }).apply()
    }
    fun signOut() { prefs.edit().clear().apply() }
}
