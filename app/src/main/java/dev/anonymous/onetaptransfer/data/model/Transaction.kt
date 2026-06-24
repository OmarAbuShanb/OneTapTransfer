package dev.anonymous.onetaptransfer.data.model

data class Transaction(
    val id: String,
    val recipient: String,
    val amount: String,
    val type: String, // e.g., "WALLET_1", "MERCHANT_1", "WALLET_2", "MERCHANT_2"
    val timestamp: Long,
    val simSlot: Int = 1
)
