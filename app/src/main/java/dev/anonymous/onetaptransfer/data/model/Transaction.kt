package dev.anonymous.onetaptransfer.data.model

data class Transaction(
    val id: String,
    val recipient: String,
    val amount: String,
    val type: String, // e.g., "BANK", "WALLET_1", "WALLET_2", "MERCHANT"
    val timestamp: Long
)