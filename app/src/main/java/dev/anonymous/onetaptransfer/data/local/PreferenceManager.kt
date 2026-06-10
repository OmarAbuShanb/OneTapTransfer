package dev.anonymous.onetaptransfer.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dev.anonymous.onetaptransfer.data.model.PinnedContact
import dev.anonymous.onetaptransfer.data.model.Transaction

class PreferenceManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    companion object {
        private const val KEY_HISTORY = "history"
        private const val KEY_PINNED = "pinned"
        private const val KEY_LAST_TAB = "last_tab"
    }

    fun saveHistory(history: List<Transaction>) {
        val json = gson.toJson(history)
        prefs.edit { putString(KEY_HISTORY, json) }
    }

    fun getHistory(): List<Transaction> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<Transaction>>() {}.type
        return gson.fromJson(json, type)
    }

    fun savePinnedContacts(contacts: List<PinnedContact>) {
        val json = gson.toJson(contacts)
        prefs.edit { putString(KEY_PINNED, json) }
    }

    fun getPinnedContacts(): List<PinnedContact> {
        val json = prefs.getString(KEY_PINNED, null) ?: return emptyList()
        val type = object : TypeToken<List<PinnedContact>>() {}.type
        return gson.fromJson(json, type)
    }

    fun saveLastTab(tabIndex: Int) {
        prefs.edit { putInt(KEY_LAST_TAB, tabIndex) }
    }

    fun getLastTab(): Int {
        return prefs.getInt(KEY_LAST_TAB, 1) // Default to Wallet 1 as per requirements
    }
}