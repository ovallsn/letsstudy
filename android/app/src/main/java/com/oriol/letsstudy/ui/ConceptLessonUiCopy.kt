package com.oriol.letsstudy.ui

data class ConceptLessonUiCopy(
    val lessonTitle: String,
    val lessonIntro: String,
    val learnAction: String,
    val openAction: String,
    val loading: String,
    val retryAction: String,
    val closeAction: String,
    val keyTermsTitle: String,
    val rememberTitle: String,
    val networkError: String,
    val quotaError: String,
    val appCheckError: String,
    val timeoutError: String,
    val invalidResponseError: String,
    val genericError: String,
) {
    fun errorFor(code: String?): String? = when (code) {
        null -> null
        "NETWORK" -> networkError
        "QUOTA" -> quotaError
        "APP_CHECK" -> appCheckError
        "TIMEOUT" -> timeoutError
        "INVALID_RESPONSE" -> invalidResponseError
        else -> genericError
    }

    companion object {
        fun forLanguage(language: String): ConceptLessonUiCopy = when {
            language.equals("Spanish", ignoreCase = true) || language.startsWith("es", ignoreCase = true) -> spanish
            language.equals("Thai", ignoreCase = true) || language.startsWith("th", ignoreCase = true) -> thai
            else -> english
        }

        private val english = ConceptLessonUiCopy(
            lessonTitle = "A deeper look",
            lessonIntro = "Build the theory behind this answer, step by step.",
            learnAction = "Learn this topic",
            openAction = "Open lesson",
            loading = "Building your lesson…",
            retryAction = "Try again",
            closeAction = "Back to question",
            keyTermsTitle = "Key terms",
            rememberTitle = "Remember this",
            networkError = "Couldn’t connect. Check your internet and try again.",
            quotaError = "The study service is temporarily unavailable. Try again later.",
            appCheckError = "Firebase couldn’t verify this app. Check its App Check registration.",
            timeoutError = "The lesson took too long to arrive. Try again.",
            invalidResponseError = "The lesson came back incomplete. Try again.",
            genericError = "Couldn’t create this lesson. Your answer is safe; try again.",
        )

        private val spanish = ConceptLessonUiCopy(
            lessonTitle = "Una explicación más a fondo",
            lessonIntro = "Aprende la teoría detrás de esta respuesta, paso a paso.",
            learnAction = "Aprender este tema",
            openAction = "Abrir explicación",
            loading = "Preparando la explicación…",
            retryAction = "Intentar de nuevo",
            closeAction = "Volver a la pregunta",
            keyTermsTitle = "Conceptos clave",
            rememberTitle = "Qué recordar",
            networkError = "No se pudo conectar. Comprueba internet e inténtalo de nuevo.",
            quotaError = "El servicio de estudio no está disponible en este momento. Inténtalo más tarde.",
            appCheckError = "Firebase no pudo verificar esta app. Revisa su registro de App Check.",
            timeoutError = "La explicación tardó demasiado. Inténtalo de nuevo.",
            invalidResponseError = "La explicación llegó incompleta. Inténtalo de nuevo.",
            genericError = "No se pudo crear la explicación. Tu respuesta está guardada; inténtalo de nuevo.",
        )

        private val thai = ConceptLessonUiCopy(
            lessonTitle = "เจาะลึกแนวคิด",
            lessonIntro = "เรียนรู้ทฤษฎีเบื้องหลังคำตอบนี้ทีละขั้นตอน",
            learnAction = "เรียนรู้หัวข้อนี้",
            openAction = "เปิดบทเรียน",
            loading = "กำลังสร้างบทเรียน…",
            retryAction = "ลองอีกครั้ง",
            closeAction = "กลับไปที่คำถาม",
            keyTermsTitle = "คำศัพท์สำคัญ",
            rememberTitle = "สิ่งที่ควรจำ",
            networkError = "เชื่อมต่อไม่ได้ ตรวจสอบอินเทอร์เน็ตแล้วลองอีกครั้ง",
            quotaError = "บริการการเรียนยังไม่พร้อมใช้งาน ลองอีกครั้งภายหลัง",
            appCheckError = "Firebase ยืนยันแอปนี้ไม่ได้ ตรวจสอบการลงทะเบียน App Check",
            timeoutError = "บทเรียนใช้เวลานานเกินไป ลองอีกครั้ง",
            invalidResponseError = "บทเรียนที่ได้รับไม่สมบูรณ์ ลองอีกครั้ง",
            genericError = "สร้างบทเรียนไม่ได้ คำตอบของคุณยังปลอดภัย ลองอีกครั้ง",
        )
    }
}
