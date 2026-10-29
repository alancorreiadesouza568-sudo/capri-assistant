package com.capriassistant

import android.content.Context
import android.provider.MediaStore

object FileTools {
    fun searchMediaStore(context: Context, query: String, extension: String? = null): String {
        val resolver = context.contentResolver
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATA
        )

        var selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        val args = arrayListOf("%$query%")

        if (!extension.isNullOrBlank()) {
            selection += " AND ${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
            args.add("%.$extension")
        }

        val results = mutableListOf<String>()
        try {
            resolver.query(
                uri, projection, selection, args.toTypedArray(),
                "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"
            )?.use { cursor ->
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

                while (cursor.moveToNext() && results.size < 4) {
                    val name = cursor.getString(nameCol)
                    val path = cursor.getString(pathCol)
                    results.add("📄 $name\n   📍 $path")
                }
            }
        } catch (e: Exception) {
            return "Erro ao ler arquivos: ${e.message}"
        }

        return if (results.isEmpty()) {
            "Não achei nenhum arquivo com o nome '$query', choom."
        } else {
            "Achei estes arquivos:\n\n" + results.joinToString("\n\n")
        }
    }
}
