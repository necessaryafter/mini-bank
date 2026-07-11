package com.carbonbank.account.table

import com.carbonbank.common.persistence.BaseIdTable
import com.carbonbank.common.persistence.money
import com.carbonbank.common.persistence.moneyNullable
import com.carbonbank.common.transaction.EntryStatus
import com.carbonbank.common.transaction.TransactionType

object Entries : BaseIdTable("entries") {
    val account = reference("account_id", Accounts)
    val transaction = reference("transaction_id", Transactions)
    val entryType = enumerationByName<TransactionType>("entry_type", 10)
    val status = enumerationByName<EntryStatus>("status", 10)
        .default(EntryStatus.PENDING)

    val amount = money("amount")

    /**
     * Only set once [status] is POSTED — the running balance right after this
     * entry, used as the cheap "last balance" cursor instead of recomputing
     * the whole history. Null while the entry is still a pending hold.
     */
    val balanceAfter = moneyNullable("balance_after")

    /**
     * DB-assigned monotonic counter, used purely as the "latest entry for this
     * account" cursor. `id` is a random UUIDv4 (no ordering guarantee), so
     * [currentBalance][com.carbonbank.account.entity.Account.currentBalance]
     * orders by this column instead.
     */
    val sequence = long("sequence").autoIncrement()

    init {
        index(customIndexName = "idx_entries_account_id_sequence", isUnique = false, account, sequence)
    }
}
