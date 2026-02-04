package com.phenix.wirelessadb.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Utility for generating QR codes (v1.2.0).
 *
 * Uses ZXing library (already a dependency) to generate QR codes
 * for P2P token sharing and other connection codes.
 */
object QrCodeGenerator {

  private const val DEFAULT_SIZE = 512
  private const val MARGIN = 1

  /**
   * Generate a QR code bitmap from text content.
   *
   * @param content Text to encode (e.g., P2P token)
   * @param size Image size in pixels (default 512)
   * @return Bitmap containing the QR code
   */
  fun generate(content: String, size: Int = DEFAULT_SIZE): Result<Bitmap> {
    return try {
      val writer = QRCodeWriter()

      val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to MARGIN
      )

      val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

      val width = bitMatrix.width
      val height = bitMatrix.height
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

      for (x in 0 until width) {
        for (y in 0 until height) {
          bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
        }
      }

      Result.success(bitmap)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Generate a QR code with custom colors.
   *
   * @param content Text to encode
   * @param foreground Color for QR code modules
   * @param background Color for background
   * @param size Image size in pixels
   * @return Bitmap containing the QR code
   */
  fun generateWithColors(
    content: String,
    foreground: Int = Color.BLACK,
    background: Int = Color.WHITE,
    size: Int = DEFAULT_SIZE
  ): Result<Bitmap> {
    return try {
      val writer = QRCodeWriter()

      val hints = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.MARGIN to MARGIN
      )

      val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints)

      val width = bitMatrix.width
      val height = bitMatrix.height
      val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

      for (x in 0 until width) {
        for (y in 0 until height) {
          bitmap.setPixel(x, y, if (bitMatrix[x, y]) foreground else background)
        }
      }

      Result.success(bitmap)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  /**
   * Generate QR code with logo overlay.
   *
   * @param content Text to encode
   * @param logo Bitmap logo to place in center
   * @param size Image size in pixels
   * @return Bitmap containing the QR code with logo
   */
  fun generateWithLogo(
    content: String,
    logo: Bitmap,
    size: Int = DEFAULT_SIZE
  ): Result<Bitmap> {
    return generate(content, size).map { qrBitmap ->
      val canvas = Canvas(qrBitmap)

      // Calculate logo size (20% of QR code)
      val logoSize = (size * 0.2f).toInt()
      val logoX = (size - logoSize) / 2f
      val logoY = (size - logoSize) / 2f

      // Draw white background for logo
      val paint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(10f, 0f, 0f, 0x40000000.toInt())
      }

      // Draw rounded logo background
      val bgRect = RectF(logoX - 4f, logoY - 4f, logoX + logoSize + 4f, logoY + logoSize + 4f)
      canvas.drawRoundRect(bgRect, 8f, 8f, paint)

      // Draw logo with rounded corners
      val roundedLogo = Bitmap.createBitmap(logoSize, logoSize, logo.config)
      val canvas2 = Canvas(roundedLogo)
      val paint2 = Paint().apply {
        isAntiAlias = true
      }
      val rect = RectF(0f, 0f, logoSize.toFloat(), logoSize.toFloat())
      canvas2.drawRoundRect(rect, 8f, 8f, paint2)
      paint2.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN)
      canvas2.drawBitmap(logo, null, rect, paint2)

      canvas.drawBitmap(roundedLogo, logoX, logoY, null)

      qrBitmap
    }
  }
}
