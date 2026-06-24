package dev.anonymous.onetaptransfer.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import dev.anonymous.onetaptransfer.data.local.PreferenceManager
import dev.anonymous.onetaptransfer.data.model.PinnedContact
import dev.anonymous.onetaptransfer.data.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val prefManager = PreferenceManager(application)

    private val _history = MutableStateFlow(prefManager.getHistory())
    val history: StateFlow<List<Transaction>> = _history

    private val _pinnedContacts =
        MutableStateFlow(prefManager.getPinnedContacts())
    val pinnedContacts: StateFlow<List<PinnedContact>> = _pinnedContacts

    private val _lastTab = MutableStateFlow(prefManager.getLastTab())
    val lastTab: StateFlow<Int> = _lastTab

    private val _lastSimSlot = MutableStateFlow(prefManager.getLastSimSlot())
    val lastSimSlot: StateFlow<Int> = _lastSimSlot

    fun saveTransaction(recipient: String, amount: String, type: String, simSlot: Int = 1) {
        val newTransaction = Transaction(
            id = UUID.randomUUID().toString(),
            recipient = recipient,
            amount = amount,
            type = type,
            timestamp = System.currentTimeMillis(),
            simSlot = if (simSlot == 2) 2 else 1
        )
        val updatedHistory = listOf(newTransaction) + _history.value.take(19)
        _history.value = updatedHistory
        prefManager.saveHistory(updatedHistory)
    }

    fun deleteTransaction(transactionId: String) {
        val updatedHistory = _history.value.filter { it.id != transactionId }
        _history.value = updatedHistory
        prefManager.saveHistory(updatedHistory)
    }

    fun togglePinContact(name: String, number: String, type: String) {
        val currentPinned = _pinnedContacts.value
        val existing = currentPinned.find { it.number == number }
        if (existing != null) {
            val updated = currentPinned.filter { it.number != number }
            _pinnedContacts.value = updated
            prefManager.savePinnedContacts(updated)
        } else {
            val newPin = PinnedContact(
                id = UUID.randomUUID().toString(),
                name = name,
                number = number,
                type = type
            )
            val updated = currentPinned + newPin
            _pinnedContacts.value = updated
            prefManager.savePinnedContacts(updated)
        }
    }

    fun saveLastTab(index: Int) {
        _lastTab.value = index
        prefManager.saveLastTab(index)
    }

    fun saveLastSimSlot(simSlot: Int) {
        val normalizedSlot = if (simSlot == 2) 2 else 1
        _lastSimSlot.value = normalizedSlot
        prefManager.saveLastSimSlot(normalizedSlot)
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefManager.saveHistory(emptyList())
    }

    fun generateUssdCode(type: String, recipient: String, amount: String): String {
        return when (type) {
            "BANK" -> "*267#"
            "WALLET_1" -> "*370*1*1*$recipient*$amount#"
            "MERCHANT_1" -> "*370*2*1*$recipient*$amount#"
            "WALLET_2" -> "*268*1*$recipient*$amount#"
            "MERCHANT_2" -> "*268*2*$recipient*$amount#"
            else -> ""
        }
    }
}
