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
        private const val KEY_LAST_SIM_SLOT = "last_sim_slot"
        private const val KEY_QUICK_TRANSFER_ENABLED = "quick_transfer_enabled"
        private const val KEY_QUICK_TRANSFER_PIN = "quick_transfer_pin"
        private const val KEY_VERSION_CODE = "app_version_code"
    }

    fun saveHistory(history: List<Transaction>) {
        val json = gson.toJson(history)
        prefs.edit { putString(KEY_HISTORY, json) }
    }

    fun getHistory(): List<Transaction> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        val type = object : TypeToken<List<Transaction>>() {}.type
        return gson.fromJson<List<Transaction>>(json, type).map { transaction ->
            if (transaction.simSlot in 1..2) transaction else transaction.copy(simSlot = 1)
        }
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
        return prefs.getInt(KEY_LAST_TAB, 0)
    }

    fun saveLastSimSlot(simSlot: Int) {
        prefs.edit { putInt(KEY_LAST_SIM_SLOT, if (simSlot == 2) 2 else 1) }
    }

    fun getLastSimSlot(): Int {
        return prefs.getInt(KEY_LAST_SIM_SLOT, 1).let { if (it == 2) 2 else 1 }
    }

    fun saveQuickTransferEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_QUICK_TRANSFER_ENABLED, enabled) }
    }

    fun isQuickTransferEnabled(): Boolean {
        return prefs.getBoolean(KEY_QUICK_TRANSFER_ENABLED, false)
    }

    fun saveQuickTransferPin(pin: String) {
        prefs.edit { putString(KEY_QUICK_TRANSFER_PIN, pin) }
    }

    fun getQuickTransferPin(): String {
        return prefs.getString(KEY_QUICK_TRANSFER_PIN, "").orEmpty()
    }

    fun isAppUpdated(currentVersionCode: Int): Boolean {
        val lastVersionCode = prefs.getInt(KEY_VERSION_CODE, -1)
        prefs.edit { putInt(KEY_VERSION_CODE, currentVersionCode) }
        return lastVersionCode != -1 && currentVersionCode > lastVersionCode
    }
}
