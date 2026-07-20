package com.carbonbank.account.statement

import com.carbonbank.account.controller.dto.StatementResponse
import com.carbonbank.common.transaction.TransactionType
import com.lowagie.text.Document
import com.lowagie.text.Element
import com.lowagie.text.Font
import com.lowagie.text.FontFactory
import com.lowagie.text.PageSize
import com.lowagie.text.Paragraph
import com.lowagie.text.Phrase
import com.lowagie.text.pdf.PdfPCell
import com.lowagie.text.pdf.PdfPTable
import com.lowagie.text.pdf.PdfWriter
import org.springframework.stereotype.Component
import java.awt.Color
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Renders a [StatementResponse] to a self-contained PDF. Pure formatting: it
 * reads the already-projected statement and owns no data access, so it stays
 * trivial to unit-test without a database or S3.
 */
@Component
class StatementPdfRenderer {

    fun render(statement: StatementResponse): ByteArray {
        val out = ByteArrayOutputStream()
        val document = Document(PageSize.A4, 48f, 48f, 56f, 48f)
        PdfWriter.getInstance(document, out)

        document.open()
        document.add(header(statement))
        document.add(movementsTable(statement))
        document.close()

        return out.toByteArray()
    }

    private fun header(statement: StatementResponse): Element {
        val block = Paragraph()
        block.add(Paragraph("Account Statement", TITLE))
        block.add(Paragraph("Owner: ${statement.ownerName}", LABEL))
        block.add(Paragraph("Account: ${statement.accountId}", LABEL))
        block.add(Paragraph("Current balance: ${money(statement.currentBalance)}", LABEL))
        block.spacingAfter = 16f
        return block
    }

    private fun movementsTable(statement: StatementResponse): PdfPTable {
        val table = PdfPTable(floatArrayOf(3f, 5f, 2f, 2.5f, 2.5f))
        table.widthPercentage = 100f
        table.headerRows = 1

        listOf("Date (UTC)", "Description", "Type", "Amount", "Balance").forEach { table.addCell(headerCell(it)) }

        if (statement.lines.isEmpty()) {
            val empty = PdfPCell(Phrase("No movements yet.", LABEL))
            empty.colspan = 5
            empty.horizontalAlignment = Element.ALIGN_CENTER
            empty.setPadding(8f)
            table.addCell(empty)
            return table
        }

        statement.lines.forEach { line ->
            table.addCell(bodyCell(DATE_FORMAT.format(line.createdAt), Element.ALIGN_LEFT))
            table.addCell(bodyCell(line.description, Element.ALIGN_LEFT))
            table.addCell(bodyCell(line.type.name, Element.ALIGN_CENTER))
            table.addCell(bodyCell(signedAmount(line.type, line.amount), Element.ALIGN_RIGHT))
            table.addCell(bodyCell(line.balanceAfter?.let(::money) ?: "—", Element.ALIGN_RIGHT))
        }
        return table
    }

    private fun headerCell(text: String): PdfPCell {
        val cell = PdfPCell(Phrase(text, TABLE_HEADER))
        cell.backgroundColor = HEADER_BG
        cell.setPadding(6f)
        return cell
    }

    private fun bodyCell(text: String, alignment: Int): PdfPCell {
        val cell = PdfPCell(Phrase(text, BODY))
        cell.horizontalAlignment = alignment
        cell.setPadding(5f)
        return cell
    }

    // A debit leaves the account, a credit arrives — show the direction with a sign
    // so the column reads like a real bank statement.
    private fun signedAmount(type: TransactionType, amount: BigDecimal): String {
        val sign = if (type == TransactionType.DEBIT) "-" else "+"
        return "$sign${money(amount)}"
    }

    private fun money(amount: BigDecimal): String = "%,.2f".format(amount)

    companion object {
        private val DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneOffset.UTC)

        private val HEADER_BG = Color(0x22, 0x3A, 0x2B)
        private val TITLE: Font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18f, Color.BLACK)
        private val LABEL: Font = FontFactory.getFont(FontFactory.HELVETICA, 10f, Color.DARK_GRAY)
        private val TABLE_HEADER: Font = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10f, Color.WHITE)
        private val BODY: Font = FontFactory.getFont(FontFactory.HELVETICA, 10f, Color.BLACK)
    }
}
