package com.example.mobile_image_retrieval.ai

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VietnameseOcrTest {
    @Test fun bundledModelReadsVietnameseReceiptAndMessage() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        @Suppress("DEPRECATION")
        val permissions = context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty()
        assertTrue("OCR must work without network permission", "android.permission.INTERNET" !in permissions)
        val bitmap = Bitmap.createBitmap(1080, 720, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 48f
                typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            }
            listOf("HÓA ĐƠN", "CỬA HÀNG MINH ANH", "Cà phê sữa 35.000 đ", "Tổng cộng 70.000 đ", "Hẹn gặp lúc 19 giờ").forEachIndexed { index, line ->
                canvas.drawText(line, 60f, 100f + index * 110f, paint)
            }
            val result = BundledPhotoTextRecognizer().recognize(bitmap)
            val folded = VietnameseText.searchable(result)
            assertTrue(result, folded.contains("hoa don"))
            assertTrue(result, folded.contains("tong cong 70 000"))
            assertTrue(result, folded.contains("hen gap luc 19 gio"))
            assertTrue("Expected Vietnamese accents: $result", result.contains("ĐƠN"))
        } finally { bitmap.recycle() }
    }
}
