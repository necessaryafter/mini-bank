package com.carbonbank.account.table

import com.carbonbank.common.persistence.BaseIdTable

object Accounts : BaseIdTable("accounts") {
    val ownerName = varchar("owner_name", 255)
}
