package com.gachon.janjan.utils

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ImageUtils {
    /**
     * URI의 내용을 앱 내부 캐시 디렉토리에 임시 파일로 저장하고 해당 파일을 반환합니다.
     * 권한 문제(READ_EXTERNAL_STORAGE 등)를 우회하기 위한 안전한 방법입니다.
     */
    fun getFileFromUri(context: Context, uri: Uri): File? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return null
            val tempFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(tempFile)
            
            inputStream.copyTo(outputStream)
            
            inputStream.close()
            outputStream.close()
            tempFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
