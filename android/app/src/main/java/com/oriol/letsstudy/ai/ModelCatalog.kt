package com.oriol.letsstudy.ai

import android.content.Context
import java.io.File

object ModelCatalog {
    const val REVISION = "b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1"
    const val FILE_NAME = "gemma-4-E2B-it.litertlm"
    const val EXPECTED_SIZE_BYTES = 2_588_147_712L
    const val SHA256 = "181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c"
    const val DISPLAY_SIZE = "2.59 GB"

    val downloadUrl: String =
        "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/$REVISION/$FILE_NAME"

    fun modelDirectory(context: Context): File = File(context.noBackupFilesDir, "study-model").apply {
        if (!exists()) mkdirs()
    }

    fun modelFile(context: Context): File = File(modelDirectory(context), FILE_NAME)

    fun partialFile(context: Context): File = File(modelDirectory(context), "$FILE_NAME.partial")
}
