package com.example.data.model

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class Expense(
    @DocumentId val id: String = "",
    val userId: String = "",
    val merchantName: String = "",
    val bankName: String = "",
    val amount: Double = 0.0,
    val category: String = ExpenseCategory.FOOD.name,
    val transactionDate: String = "",
    val referenceNo: String = "",
    val memo: String = "",
    val isFromSlip: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class UserBudget(
    @DocumentId val id: String = "",
    val userId: String = "",
    val monthlyLimit: Double = 15000.0,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class ExpenseCategory(
    val labelTh: String,
    val labelEn: String,
    val colorHex: Long
) {
    FOOD("อาหารและเครื่องดื่ม", "Food & Drink", 0xFF10B981),
    SHOPPING("ช้อปปิ้งและของใช้", "Shopping", 0xFFF59E0B),
    TRANSPORT("เดินทางและน้ำมัน", "Transport", 0xFF3B82F6),
    BILLS("บิลและค่าน้ำ-ไฟ-เน็ต", "Bills & Utilities", 0xFF8B5CF6),
    TRANSFER("โอนเงินให้บุคคล", "Personal Transfer", 0xFF14B8A6),
    ENTERTAINMENT("บันเทิงและไลฟ์สไตล์", "Entertainment", 0xFFEC4899),
    HEALTH("สุขภาพและการแพทย์", "Health & Wellness", 0xFFEF4444),
    OTHER("อื่นๆ", "Other", 0xFF64748B);

    companion object {
        fun fromKey(key: String?): ExpenseCategory {
            if (key.isNullOrBlank()) return OTHER
            return entries.firstOrNull { it.name.equals(key.trim(), ignoreCase = true) } ?: OTHER
        }
    }
}

enum class ThaiBank(
    val code: String,
    val displayName: String,
    val badgeColorHex: Long
) {
    KBANK("KBank", "กสิกรไทย (K PLUS)", 0xFF00A950),
    SCB("SCB", "ไทยพาณิชย์ (SCB EASY)", 0xFF4E2A84),
    KTB("Krungthai", "กรุงไทย (Krungthai NEXT)", 0xFF00A5E5),
    BBL("Bangkok Bank", "กรุงเทพ (Bualuang mBanking)", 0xFF1E4598),
    BAY("Krungsri", "กรุงศรี (KMA)", 0xFFFFC425),
    TTB("ttb", "ทหารไทยธนชาต (ttb touch)", 0xFF0056FF),
    GSB("GSB", "ออมสิน (MyMo)", 0xFFEB198D),
    PROMPTPAY("PromptPay", "พร้อมเพย์ (PromptPay)", 0xFF1A3C6E),
    OTHER("Other", "เงินสด / ธนาคารอื่น", 0xFF475569);

    companion object {
        fun matchBankColor(bankName: String): Long {
            val lower = bankName.lowercase()
            return when {
                lower.contains("kbank") || lower.contains("กสิกร") || lower.contains("k plus") || lower.contains("kasikorn") -> KBANK.badgeColorHex
                lower.contains("scb") || lower.contains("ไทยพาณิชย์") -> SCB.badgeColorHex
                lower.contains("krungthai") || lower.contains("ktb") || lower.contains("กรุงไทย") -> KTB.badgeColorHex
                lower.contains("bangkok") || lower.contains("bbl") || lower.contains("กรุงเทพ") || lower.contains("บัวหลวง") -> BBL.badgeColorHex
                lower.contains("krungsri") || lower.contains("กรุงศรี") || lower.contains("kma") || lower.contains("bay") -> BAY.badgeColorHex
                lower.contains("ttb") || lower.contains("ทหารไทย") || lower.contains("ธนชาต") -> TTB.badgeColorHex
                lower.contains("gsb") || lower.contains("ออมสิน") || lower.contains("mymo") -> GSB.badgeColorHex
                lower.contains("promptpay") || lower.contains("พร้อมเพย์") -> PROMPTPAY.badgeColorHex
                else -> OTHER.badgeColorHex
            }
        }
    }
}

data class SlipExtractionResult(
    val merchantName: String,
    val bankName: String,
    val amount: Double,
    val category: ExpenseCategory,
    val transactionDate: String,
    val referenceNo: String,
    val memo: String,
    val bitmap: Bitmap? = null
)

data class SampleSlipPreset(
    val id: String,
    val title: String,
    val bankHeader: String,
    val headerColor: Int,
    val senderName: String,
    val receiverName: String,
    val amountText: String,
    val dateTimeText: String,
    val refNo: String,
    val memoText: String
)

object SampleSlipGenerator {
    val presets = listOf(
        SampleSlipPreset(
            id = "kbank_cafe",
            title = "สลิป K PLUS - คาเฟ่อเมซอน (65.00 ฿)",
            bankHeader = "K PLUS • โอนเงินสำเร็จ",
            headerColor = Color.parseColor("#00A950"),
            senderName = "นาย ธนภัทร รักษ์ไทย (xxx-x-x4821-x)",
            receiverName = "บจก. คาเฟ่ อเมซอน สาขาสยาม",
            amountText = "65.00 บาท",
            dateTimeText = "9 พ.ย. 69 08:45 น.",
            refNo = "0143120845198201",
            memoText = "กาแฟเย็น Iced Latte"
        ),
        SampleSlipPreset(
            id = "scb_lotus",
            title = "สลิป SCB EASY - โลตัส โก เฟรช (489.50 ฿)",
            bankHeader = "SCB EASY • จ่ายบิล/พร้อมเพย์สำเร็จ",
            headerColor = Color.parseColor("#4E2A84"),
            senderName = "คุณ ธนภัทร รักษ์ไทย (xxx-xxx901-2)",
            receiverName = "CP AXTRA - Lotus's Go Fresh",
            amountText = "489.50 บาท",
            dateTimeText = "9 พ.ย. 69 12:30 น.",
            refNo = "20261109SCB884210",
            memoText = "ซื้อของใช้เข้าบ้านและผลไม้"
        ),
        SampleSlipPreset(
            id = "ktb_mea",
            title = "สลิป Krungthai NEXT - ค่าไฟฟ้า MEA (1,840.00 ฿)",
            bankHeader = "Krungthai NEXT • ชำระบิลสำเร็จ",
            headerColor = Color.parseColor("#0082C8"),
            senderName = "นาย ธนภัทร รักษ์ไทย",
            receiverName = "การไฟฟ้านครหลวง (MEA Smart Life)",
            amountText = "1,840.00 บาท",
            dateTimeText = "8 พ.ย. 69 19:15 น.",
            refNo = "KTB99201847261",
            memoText = "ค่าไฟประจำเดือน พ.ย."
        ),
        SampleSlipPreset(
            id = "bbl_bts",
            title = "สลิป Bangkok Bank - เติมเที่ยวรถไฟฟ้า BTS (350.00 ฿)",
            bankHeader = "Bualuang mBanking • โอนเงินสำเร็จ",
            headerColor = Color.parseColor("#1E4598"),
            senderName = "นาย ธนภัทร รักษ์ไทย",
            receiverName = "Rabbit Line Pay / BTS SkyTrain",
            amountText = "350.00 บาท",
            dateTimeText = "7 พ.ย. 69 07:50 น.",
            refNo = "BBL7749201123",
            memoText = "เติมบัตรเดินทาง BTS"
        )
    )

    /**
     * Renders a realistic Thai bank transfer e-slip bitmap so the user can test
     * the real Gemini multimodal slip extraction even on a fresh emulator with an empty gallery.
     */
    fun renderSlipBitmap(preset: SampleSlipPreset): Bitmap {
        val width = 760
        val height = 1040
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Slip paper background
        canvas.drawColor(Color.parseColor("#F8FAFC"))

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top bank banner
        paint.color = preset.headerColor
        canvas.drawRoundRect(RectF(28f, 28f, width - 28f, 188f), 28f, 28f, paint)

        // Header text
        paint.color = Color.WHITE
        paint.textSize = 36f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(preset.bankHeader, 64f, 105f, paint)

        paint.textSize = 24f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("วันที่ทำรายการ: ${preset.dateTimeText}", 64f, 152f, paint)

        // Card container
        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(28f, 212f, width - 28f, height - 40f), 28f, 28f, paint)

        // Divider line
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#E2E8F0")
            strokeWidth = 3f
        }

        var y = 290f
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#64748B")
            textSize = 24f
        }
        val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0F172A")
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        // From
        canvas.drawText("จาก (ผู้โอน / From)", 68f, y, labelPaint)
        y += 42f
        canvas.drawText(preset.senderName, 68f, y, valuePaint)
        y += 45f
        canvas.drawLine(68f, y, width - 68f, y, linePaint)

        // To
        y += 55f
        canvas.drawText("ไปยัง (ผู้รับเงิน / ร้านค้า / To)", 68f, y, labelPaint)
        y += 42f
        canvas.drawText(preset.receiverName, 68f, y, valuePaint)
        y += 45f
        canvas.drawLine(68f, y, width - 68f, y, linePaint)

        // Amount highlight box
        y += 45f
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#ECFDF5")
        }
        canvas.drawRoundRect(RectF(68f, y, width - 68f, y + 145f), 20f, 20f, boxPaint)

        canvas.drawText("จำนวนเงิน (Amount)", 96f, y + 48f, labelPaint)
        val amountPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#047857")
            textSize = 48f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        canvas.drawText(preset.amountText, 96f, y + 112f, amountPaint)

        y += 195f
        canvas.drawText("เลขที่รายการ (Reference No.): ${preset.refNo}", 68f, y, labelPaint)
        y += 46f
        canvas.drawText("ค่าธรรมเนียม: 0.00 บาท", 68f, y, labelPaint)
        y += 46f
        canvas.drawText("บันทึกช่วยจำ (Memo): ${preset.memoText}", 68f, y, valuePaint)

        return bitmap
    }
}
