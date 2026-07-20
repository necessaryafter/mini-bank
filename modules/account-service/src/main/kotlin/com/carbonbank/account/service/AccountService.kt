package com.carbonbank.account.service

import com.carbonbank.account.controller.dto.AccountResponse
import com.carbonbank.account.controller.dto.CreateAccountRequest
import com.carbonbank.account.entity.Account
import com.carbonbank.account.entity.Entry
import com.carbonbank.account.entity.Transaction
import com.carbonbank.account.idempotency.AccountIdempotencyService
import com.carbonbank.common.transaction.EntryStatus
import com.carbonbank.common.transaction.TransactionStatus
import com.carbonbank.common.transaction.TransactionType
import com.carbonbank.common.types.IdempotencyKey
import com.carbonbank.common.types.Money
import com.carbonbank.common.web.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class AccountService(
    private val idempotencyService: AccountIdempotencyService,
) {

    /**
     * Reserves the idempotency key in Redis before touching Postgres so
     * concurrent requests with the same key race for a single winner; the
     * loser reads the winner's account id back and returns it instead of
     * creating a second account. If the winner fails before committing, the
     * reservation is released so the key isn't stuck. Mirrors
     * [TransferService.createTransfer].
     */
    @Transactional
    fun createAccount(request: CreateAccountRequest, idempotencyKey: IdempotencyKey): AccountResponse {
        val accountId = UUID.randomUUID()
        val reserved = idempotencyService.reserve(idempotencyKey, accountId)
        if (!reserved) {
            val existingId = idempotencyService.accountIdFor(idempotencyKey)
                ?: throw ApiException(HttpStatus.CONFLICT, "Idempotency key is already being processed, retry shortly")
            return toResponse(loadAccount(existingId))
        }

        return try {
            val account = Account.new(accountId) {
                ownerName = request.ownerName
                this.idempotencyKey = idempotencyKey
            }

            val initialBalance = Money.from(request.initialBalance)
            if (initialBalance > Money.ZERO) {
                openingDeposit(account, initialBalance)
            }
            toResponse(account)
        } catch (ex: Exception) {
            idempotencyService.release(idempotencyKey)
            throw ex
        }
    }

    @Transactional
    fun getAccount(accountId: UUID): AccountResponse =
        toResponse(loadAccount(accountId))

    /**
     * Issues the opening balance as a real double-entry transaction: debit the
     * genesis account, credit the new account. Both entries are POSTED at once —
     * this bypasses [TransferCaptureService] because there is no source-balance
     * check to run (the issuer funds it).
     *
     * The genesis debit intentionally leaves `balanceAfter` null: the issuer's
     * running balance is never read, so not maintaining it means concurrent
     * account creations don't serialize on (or lost-update) the genesis row.
     * The debit/credit invariant is upheld by the entries existing with equal
     * amounts, not by the denormalized balance cursor. See docs/adr/0011.
     */
    private fun openingDeposit(account: Account, amount: Money) {
        val genesis = Account.findById(Account.GENESIS_ID)
            ?: throw IllegalStateException("Genesis account ${Account.GENESIS_ID} is missing; check migration V3")

        val opening = Transaction.new {
            description = "Opening balance for ${account.id.value}"
            idempotencyKey = IdempotencyKey.random()
            status = TransactionStatus.COMPLETED
        }
        Entry.new {
            transaction = opening
            this.account = genesis
            entryType = TransactionType.DEBIT
            this.amount = amount
            status = EntryStatus.POSTED
        }
        Entry.new {
            transaction = opening
            this.account = account
            entryType = TransactionType.CREDIT
            this.amount = amount
            status = EntryStatus.POSTED
            balanceAfter = amount
        }
    }

    private fun loadAccount(accountId: UUID): Account =
        Account.findById(accountId)
            ?: throw ApiException(HttpStatus.NOT_FOUND, "Account $accountId not found")

    private fun toResponse(account: Account): AccountResponse =
        AccountResponse(
            id = account.id.value,
            ownerName = account.ownerName,
            balance = account.currentBalance().toBigDecimal(),
        )
}
