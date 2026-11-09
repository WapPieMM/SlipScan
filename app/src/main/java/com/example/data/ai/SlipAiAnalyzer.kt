package com.example.data.ai

import android.graphics.Bitmap
import android.util.Log
import com.example.data.model.ExpenseCategory
import com.example.data.model.SlipExtractionResult
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

class SlipAiAnalyzer {

    private val slipSchema = Schema.obj(
        properties = mapOf(
            "merchantName" to Schema.string(
                description = "Recipient name, shop name, or biller extracted from the bank slip (in Thai or English as shown on slip)"
            ),
            "bankName" to Schema.string(
                description = "Source bank or payment app name (e.g., กสิกรไทย (K PLUS), ไทยพาณิชย์ (SCB EASY), กรุงไทย (Krungthai NEXT), กรุงเทพ (Bualuang), กรุงศรี (KMA), ttb touch, ออมสิน (MyMo), พร้อมเพย์)"
            ),
            "amount" to Schema.double(
                description = "Exact transfer or payment amount in Thai Baht (numeric value > 0)"
            ),
            "category" to Schema.enumeration(
                values = listOf(
                    "FOOD",
                    "SHOPPING",
                    "TRANSPORT",
                    "BILLS",
                    "TRANSFER",
                    "ENTERTAINMENT",
                    "HEALTH",
                    "OTHER"
                ),
                description = "Best matching expense category inferred from recipient name and memo"
            ),
            "transactionDate" to Schema.string(
                description = "Transaction date and time shown on the slip (e.g. '9 พ.ย. 69 08:45 น.' or '2026-11-09 08:45')"
            ),
            "referenceNo" to Schema.string(
                description = "Transaction reference number / รหัสอ้างอิง / เลขที่รายการ from the slip"
            ),
            "memo" to Schema.string(
                description = "Short helpful note or memo found on the slip, or a brief Thai description of the expense"
            )
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = slipSchema
            }
        )
    }

    suspend fun analyzeSlipBitmap(bitmap: Bitmap): Result<SlipExtractionResult> {
        val scaledBitmap = scaleBitmapDown(bitmap, maxDimension = 1280)
        return try {
            val response = withTimeout(60_000L) {
                model.generateContent(
                    content {
                        image(scaledBitmap)
                        text(
                            """
                            คุณคือผู้ช่วยวิเคราะห์สลิปโอนเงินธนาคารของไทย (Thai Bank Transfer Slip OCR & Categorizer)
                            กรุณาอ่านข้อมูลจากรูปสลิปโอนเงินหรือใบเสร็จนี้และสกัดข้อมูลต่อไปนี้:
                            1. merchantName: ชื่อผู้รับเงิน ชื่อร้านค้า หรือหน่วยงานที่รับชำระเงิน (ดูจากช่อง 'ไปยัง', 'To', หรือผู้รับเงิน)
                            2. bankName: ชื่อธนาคารต้นทางของสลิป (เช่น กสิกรไทย (K PLUS), ไทยพาณิชย์ (SCB EASY), กรุงไทย (Krungthai NEXT), กรุงเทพ (Bualuang), กรุงศรี (KMA), ttb touch, ออมสิน (MyMo), หรือ พร้อมเพย์)
                            3. amount: จำนวนเงินที่โอนหรือชำระ (ตัวเลขทศนิยม เช่น 65.00, 1840.00)
                            4. category: หมวดหมู่รายจ่ายที่เหมาะสมที่สุดจากตัวเลือก (FOOD, SHOPPING, TRANSPORT, BILLS, TRANSFER, ENTERTAINMENT, HEALTH, OTHER)
                            5. transactionDate: วันที่และเวลาที่ทำรายการตามที่ปรากฏบนสลิป
                            6. referenceNo: เลขที่รายการ หรือ รหัสอ้างอิง บนสลิป (ถ้าไม่มีให้ใส่ค่าว่าง "")
                            7. memo: บันทึกช่วยจำบนสลิป หรือสรุปสั้นๆ ว่าเป็นค่าใช้จ่ายอะไร
                            """.trimIndent()
                        )
                    }
                )
            }

            val jsonString = response.text
            if (jsonString.isNullOrBlank()) {
                return Result.failure(IllegalStateException("โมเดล AI ไม่ได้ส่งข้อมูลกลับมา กรุณาลองใหม่อีกครั้ง"))
            }

            val json = JSONObject(jsonString)
            val merchantName = json.optString("merchantName", "ไม่ระบุชื่อผู้รับ").trim().ifBlank { "ไม่ระบุชื่อผู้รับ" }
            val bankName = json.optString("bankName", "ธนาคาร/พร้อมเพย์").trim().ifBlank { "ธนาคาร/พร้อมเพย์" }
            val amount = json.optDouble("amount", 0.0).let { if (it.isNaN() || it <= 0.0) 1.0 else it }
            val categoryStr = json.optString("category", "OTHER")
            val transactionDate = json.optString("transactionDate", "วันนี้").trim().ifBlank { "วันนี้" }
            val referenceNo = json.optString("referenceNo", "").trim()
            val memo = json.optString("memo", "").trim()

            Result.success(
                SlipExtractionResult(
                    merchantName = merchantName,
                    bankName = bankName,
                    amount = amount,
                    category = ExpenseCategory.fromKey(categoryStr),
                    transactionDate = transactionDate,
                    referenceNo = referenceNo,
                    memo = memo,
                    bitmap = scaledBitmap
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Slip AI extraction timed out", e)
            Result.failure(Exception("การอ่านข้อมูลสลิปใช้เวลานานเกินกำหนด (60 วินาที) กรุณาลองใหม่อีกครั้ง"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Slip AI extraction failed", e)
            Result.failure(Exception(e.localizedMessage ?: "ไม่สามารถอ่านข้อมูลจากรูปสลิปได้"))
        }
    }

    private fun scaleBitmapDown(source: Bitmap, maxDimension: Int): Bitmap {
        val width = source.width
        val height = source.height
        if (width <= maxDimension && height <= maxDimension) return source
        val ratio = width.toFloat() / height.toFloat()
        val newWidth: Int
        val newHeight: Int
        if (width > height) {
            newWidth = maxDimension
            newHeight = (maxDimension / ratio).toInt().coerceAtLeast(1)
        } else {
            newHeight = maxDimension
            newWidth = (maxDimension * ratio).toInt().coerceAtLeast(1)
        }
        return Bitmap.createScaledBitmap(source, newWidth, newHeight, true)
    }

    private companion object {
        const val TAG = "SlipAiAnalyzer"
    }
}
