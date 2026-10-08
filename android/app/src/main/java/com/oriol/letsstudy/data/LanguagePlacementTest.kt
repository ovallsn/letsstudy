package com.oriol.letsstudy.data

data class LanguagePlacementQuestion(
    val band: String,
    val prompt: String,
    val options: List<String>,
    val answerIndex: Int,
    val explanation: String,
)

data class LanguagePlacementBandScore(val band: String, val correct: Int, val total: Int = 4)

data class LanguagePlacementResult(
    val language: String,
    val level: String,
    val estimatedRange: String,
    val startingLevel: String,
    val correct: Int,
    val total: Int,
    val bandScores: List<LanguagePlacementBandScore>,
    val nextFocus: String,
)

object LanguagePlacementTest {
    val languages = listOf("English", "Spanish", "French", "Thai")
    val bands = listOf("A1", "A2", "B1", "B2", "C1")
    private val banks = mapOf(
        "English" to listOf(
            item("A1", "I ___ from Spain.", 0, "Use ‘am’ with ‘I’ in the present tense.", "am", "is", "are", "be"),
            item("A1", "She ___ coffee every morning.", 1, "Use the third-person singular form ‘drinks’.", "drink", "drinks", "drinking", "drank"),
            item("A1", "There ___ two books on the table.", 2, "Use ‘are’ with a plural noun.", "is", "be", "are", "was"),
            item("A1", "‘Can you help me?’ ‘Yes, ___.’", 1, "A short answer to ‘Can you…?’ uses ‘I can’.", "I do", "I can", "I am", "I help"),
            item("A2", "Yesterday we ___ to the museum.", 0, "‘Yesterday’ calls for the simple past: ‘went’.", "went", "go", "have gone", "are going"),
            item("A2", "I've lived here ___ 2022.", 1, "Use ‘since’ with the point when something began.", "for", "since", "during", "from"),
            item("A2", "If it ___ tomorrow, we'll stay home.", 2, "In a first conditional, the if-clause uses the present simple.", "will rain", "rained", "rains", "has rained"),
            item("A2", "You ___ wear a helmet; it's the law.", 3, "‘Must’ expresses a strong obligation.", "might", "could", "would", "must"),
            item("B1", "Although she ___ tired, she finished the report.", 0, "The completed past actions use ‘was’.", "was", "is", "has been", "be"),
            item("B1", "By the time we arrived, the train ___.", 1, "The earlier past action takes the past perfect: ‘had left’.", "left", "had left", "has left", "was leave"),
            item("B1", "If I ___ more time, I'd take the course.", 3, "A second conditional uses past simple after ‘if’.", "have", "will have", "would have", "had"),
            item("B1", "Could you let me know ___ the schedule changes?", 2, "‘If’ introduces the condition in this indirect request.", "unless", "despite", "if", "because of"),
            item("B2", "Rarely ___ such a clear explanation.", 0, "A negative adverb at the start triggers subject–auxiliary inversion.", "have I heard", "did I heard", "I have heard", "I did heard"),
            item("B2", "The proposal was rejected, ___ carefully it had been prepared.", 3, "‘However carefully’ means ‘no matter how carefully’.", "although", "despite", "nevertheless", "however"),
            item("B2", "The manager recommended that each applicant ___ a portfolio.", 1, "After ‘recommend that’, formal English uses the base verb.", "submits", "submit", "submitted", "will submit"),
            item("B2", "No sooner ___ than the alarm went off.", 2, "‘No sooner’ at the start uses inversion and the past perfect.", "we had sat down", "did we sit down", "had we sat down", "were we sitting down"),
            item("C1", "I would rather you ___ me before making the change.", 1, "‘Would rather’ about another person's past action takes the past perfect.", "consult", "had consulted", "would consult", "have consulted"),
            item("C1", "It is imperative that the report ___ by noon.", 3, "The formal mandative subjunctive uses ‘be’ after ‘imperative that’.", "is finished", "will be finished", "has finished", "be finished"),
            item("C1", "The evidence was far from ___.", 0, "‘Far from’ is followed by an adjective here.", "conclusive", "conclusively", "conclusion", "conclude"),
            item("C1", "Had the policy not been revised, costs ___ significantly.", 2, "This inverted third conditional takes ‘would have’ plus a past participle.", "increase", "would increase", "would have increased", "had increased"),
        ),
        "Spanish" to listOf(
            item("A1", "Yo ___ estudiante.", 1, "‘Ser’ describes identity: ‘Yo soy estudiante’.", "está", "soy", "es", "son"),
            item("A1", "¿Dónde ___ los baños?", 2, "Use ‘están’ for the location of plural things.", "es", "está", "están", "hay"),
            item("A1", "María ___ dos hermanos.", 0, "The verb ‘tener’ is used for age and possession.", "tiene", "tienen", "tengo", "está"),
            item("A1", "¿A qué hora ___ el tren?", 3, "Use ‘sale’ for a scheduled departure.", "salir", "salgo", "salen", "sale"),
            item("A2", "Ayer ___ una película en casa.", 0, "‘Ayer’ signals the preterite: ‘vimos’.", "vimos", "vemos", "hemos visto", "veíamos"),
            item("A2", "Vivo aquí ___ 2021.", 2, "Use ‘desde’ with the starting point in time.", "por", "durante", "desde", "hace"),
            item("A2", "Si mañana ___, nos quedamos en casa.", 1, "A real future condition uses the present tense after ‘si’.", "lloverá", "llueve", "lloviera", "ha llovido"),
            item("A2", "¿Te importa ___ la ventana?", 3, "After ‘te importa’, use the infinitive ‘cerrar’.", "cerrada", "cierro", "cerrando", "cerrar"),
            item("B1", "Aunque ___ cansado, terminó el informe.", 0, "A known past fact takes the indicative ‘estaba’.", "estaba", "estuviera", "esté", "estaría"),
            item("B1", "Cuando llegamos, el tren ya ___.", 2, "The train left before another past event: ‘había salido’.", "salió", "ha salido", "había salido", "saldría"),
            item("B1", "Si tuviera más tiempo, ___ un curso.", 1, "The second conditional uses the conditional: ‘haría’.", "hago", "haría", "hice", "habré hecho"),
            item("B1", "No creo que ella ___ la respuesta.", 3, "Negating a belief triggers the present subjunctive ‘sepa’.", "sabe", "sabrá", "sabía", "sepa"),
            item("B2", "No solo presentó el proyecto, ___ también lo defendió.", 0, "The paired construction is ‘no solo…, sino que también…’.", "sino que", "pero que", "aunque", "por lo que"),
            item("B2", "De haberlo sabido, te ___ llamado.", 2, "A past unreal condition uses ‘habría’ plus the participle.", "he", "había", "habría", "hubiera"),
            item("B2", "Es importante que todos ___ a tiempo.", 1, "‘Es importante que’ is followed by the subjunctive.", "llegan", "lleguen", "llegarían", "llegaron"),
            item("B2", "Cuanto más practiques, ___ te resultará.", 3, "The paired comparative is ‘cuanto más…, más…’.", "muy fácil", "más fácilmente que", "lo más fácil", "más fácil"),
            item("C1", "No atribuyó el retraso a la falta de recursos, ___ a una decisión tardía.", 0, "Use ‘sino’ to contrast what was not the cause with the actual cause.", "sino", "que", "lo que", "por lo cual"),
            item("C1", "Por mucho que ___, no logrará convencerlos.", 0, "A concessive expression with an uncertain effort takes the subjunctive.", "insista", "insistirá", "insiste", "insistía"),
            item("C1", "De ahí que se ___ otra reunión.", 1, "‘De ahí que’ introduces a consequence with the subjunctive.", "convoca", "convoque", "convocará", "convocó"),
            item("C1", "El informe, cuyos datos ___ en revisión, se publicará mañana.", 3, "‘Cuyos datos’ is the plural subject, so use ‘están’.", "está", "estuvo", "esté", "están"),
        ),
        "French" to listOf(
            item("A1", "Je ___ espagnol.", 0, "The first-person form of ‘être’ is ‘suis’.", "suis", "es", "est", "sommes"),
            item("A1", "Nous ___ à Paris.", 3, "The first-person plural form of ‘habiter’ is ‘habitons’.", "habitez", "habitent", "habite", "habitons"),
            item("A1", "Elle a ___ livre.", 1, "Use ‘un’ before a masculine singular noun.", "une", "un", "des", "du"),
            item("A1", "Tu ___ du café ?", 2, "The second-person form is ‘veux’.", "veut", "voulez", "veux", "voulons"),
            item("A2", "Hier, ils ___ au cinéma.", 1, "‘Aller’ uses ‘être’ in the passé composé: ‘sont allés’.", "ont allé", "sont allés", "allaient", "vont aller"),
            item("A2", "Je travaille ici ___ deux ans.", 3, "Use ‘depuis’ for an action that began in the past and continues.", "pendant", "pour", "en", "depuis"),
            item("A2", "S'il ___, on prendra un taxi.", 0, "A likely future condition uses the present after ‘si’.", "pleut", "pleuvra", "pleuvait", "a plu"),
            item("A2", "J'ai acheté ___ pommes.", 2, "Use the plural indefinite article ‘des’.", "du", "de la", "des", "une"),
            item("B1", "Bien qu'il ___ malade, il est venu.", 3, "‘Bien que’ is followed by the subjunctive: ‘soit’.", "est", "était", "sera", "soit"),
            item("B1", "Quand nous sommes arrivés, le film ___.", 1, "The film had started earlier: ‘avait commencé’.", "a commencé", "avait commencé", "commençait", "commencera"),
            item("B1", "Si j'avais le temps, je ___ ce cours.", 2, "The second conditional uses the conditional: ‘suivrais’.", "suis", "suivrai", "suivrais", "suivais"),
            item("B1", "Il faut que tu ___ plus tôt.", 0, "‘Il faut que’ takes the subjunctive: ‘partes’.", "partes", "pars", "partiras", "partais"),
            item("B2", "Non seulement elle a réussi, ___ elle a aidé ses collègues.", 1, "The paired phrase is ‘non seulement…, mais encore…’.", "ou encore", "mais encore", "malgré cela", "alors que"),
            item("B2", "Aussitôt que nous ___, nous vous appellerons.", 3, "For a completed future action, use the future perfect: ‘serons arrivés’.", "sommes arrivés", "arriverions", "arrivons", "serons arrivés"),
            item("B2", "Je doute qu'il ___ la vérité.", 2, "Doubt triggers the subjunctive: ‘dise’.", "dit", "dira", "dise", "disait"),
            item("B2", "Plus il s'entraîne, ___ il progresse.", 0, "The paired comparative is ‘plus…, plus…’.", "plus", "davantage de", "mieux que", "le plus"),
            item("C1", "Il eût fallu qu'elle ___ plus tôt.", 3, "This formal past construction takes the plus-que-parfait du subjonctif: ‘fût venue’.", "venait", "est venue", "viendrait", "fût venue"),
            item("C1", "Mieux vaut qu'elle ___ au courant.", 2, "‘Mieux vaut que’ is followed by the subjunctive ‘soit’.", "est", "sera", "soit", "était"),
            item("C1", "Quoiqu'il ___, la décision ne changera pas.", 1, "‘Quoique’ meaning ‘although’ is followed by the subjunctive here.", "en dit", "en dise", "en dira", "en disait"),
            item("C1", "Le rapport a été accepté, pourvu qu'il ___ quelques corrections.", 0, "‘Pourvu que’ is followed by the subjunctive ‘subisse’.", "subisse", "subit", "subira", "subissait"),
        ),
        "Thai" to listOf(
            item("A1", "ฉัน ___ นักเรียน", 2, "ใช้ ‘เป็น’ เพื่อบอกอาชีพหรือสถานะ", "อยู่", "มี", "เป็น", "ทำ"),
            item("A1", "เขา ___ น้ำทุกวัน", 1, "‘ดื่มน้ำ’ หมายถึง drink water", "กิน", "ดื่ม", "ไป", "ดู"),
            item("A1", "ห้องนี้ ___ ใหญ่", 3, "วาง ‘ไม่’ ไว้หน้าคำคุณศัพท์เพื่อปฏิเสธ", "ยัง", "ก็", "จะ", "ไม่"),
            item("A1", "คุณจะไป ___?", 0, "‘ไปไหน’ ใช้ถามจุดหมาย", "ไหน", "ใคร", "เมื่อไร", "เท่าไร"),
            item("A2", "เมื่อวานฉัน ___ ตลาด", 3, "‘เมื่อวาน’ ใช้กับเหตุการณ์ในอดีต; ‘ไปตลาด’ คือ went to the market", "จะไป", "กำลังไป", "ไปแล้วจะ", "ไป"),
            item("A2", "ฉันทำงานที่นี่ ___ สามปีแล้ว", 1, "‘มาได้สามปีแล้ว’ บอกระยะเวลาที่ดำเนินต่อมาถึงปัจจุบัน", "ใน", "มาได้", "ระหว่าง", "จาก"),
            item("A2", "ถ้าพรุ่งนี้ฝนตก เรา ___ อยู่บ้าน", 2, "ใช้ ‘จะ’ ในผลลัพธ์ที่คาดว่าจะเกิดในอนาคต", "อยู่", "อยู่แล้ว", "จะ", "เคย"),
            item("A2", "ฉันชอบชา ___ กาแฟ", 0, "‘มากกว่า’ ใช้เปรียบเทียบความชอบ", "มากกว่า", "ที่สุด", "เพราะ", "ก่อน"),
            item("B1", "แม้ว่าเขา ___ เหนื่อย แต่ก็ทำงานต่อ", 1, "โครงสร้าง ‘แม้ว่า…แต่…’ แสดงความขัดแย้ง", "ไม่", "จะ", "แล้ว", "กว่า"),
            item("B1", "ก่อนที่รถไฟจะมาถึง เรา ___ ตั๋วเรียบร้อยแล้ว", 3, "‘เรียบร้อยแล้ว’ เน้นว่างานซื้อเสร็จก่อนอีกเหตุการณ์", "กำลังซื้อ", "จะซื้อ", "ซื้อทีหลัง", "ซื้อ"),
            item("B1", "ถ้าฉันมีเวลามากกว่านี้ ฉัน ___ ภาษาไทยเพิ่ม", 0, "ใช้ ‘จะ’ เพื่อบอกผลที่คาดในเงื่อนไขนี้", "จะเรียน", "เคยเรียน", "กำลังเรียน", "เรียนแล้ว"),
            item("B1", "เธอบอกว่าเธอ ___ พรุ่งนี้", 2, "‘จะมา’ แสดงแผนในอนาคตที่ถูกรายงาน", "มาแล้ว", "มาถึง", "จะมา", "มาเมื่อวาน"),
            item("B2", "ยิ่งฝึก ___ ก็ยิ่งพูดคล่อง", 1, "โครงสร้าง ‘ยิ่ง…ยิ่ง…’ ใช้ขยายความสัมพันธ์", "นาน", "มาก", "บ่อยกว่า", "ที่สุด"),
            item("B2", "ไม่เพียงแต่เขา ___ ภาษาอังกฤษได้ แต่ยังเขียนได้ดีด้วย", 0, "‘พูดภาษาอังกฤษได้’ เป็นทักษะที่จับคู่กับการเขียน", "พูด", "ฟัง", "อ่าน", "แปล"),
            item("B2", "แม้จะมีเวลาไม่มาก เขาก็ ___ งานเสร็จทัน", 3, "‘ทำงานเสร็จทัน’ สื่อว่าทำงานเสร็จตามเวลา", "ทำงาน", "กำลังทำ", "ทำงานไว้", "ทำ"),
            item("B2", "หากทราบล่วงหน้า เรา ___ เตรียมตัวได้ดีกว่านี้", 2, "‘คง…ได้’ ใช้กล่าวถึงผลที่น่าจะเป็นไปได้", "เคย", "กำลัง", "คง", "ยัง"),
            item("C1", "ประเด็นดังกล่าวได้รับการ ___ อย่างรอบด้าน", 3, "‘ได้รับการพิจารณา’ เป็นรูปประโยคถูกกระทำที่เหมาะกับบริบททางการ", "คิด", "เห็น", "รู้", "พิจารณา"),
            item("C1", "ข้อจำกัดที่ไม่อาจ ___ ได้", 1, "‘ไม่อาจมองข้ามได้’ เป็นสำนวนทางการที่หมายถึงละเลยไม่ได้", "มองหา", "มองข้าม", "มองเห็น", "มองกลับ"),
            item("C1", "การตัดสินใจดังกล่าวส่งผล ___ ต่อชุมชนในระยะยาว", 0, "‘อย่างมีนัยสำคัญ’ ใช้ขยายผลกระทบอย่างเป็นทางการ", "อย่างมีนัยสำคัญ", "โดยทันที", "ในที่สุด", "อย่างใกล้ชิด"),
            item("C1", "ยิ่งข้อมูลชัดเจนมากเท่าไร การตัดสินใจก็ยิ่ง ___ ขึ้นเท่านั้น", 2, "‘รอบคอบ’ สอดคล้องกับการตัดสินใจที่ดีขึ้นเมื่อข้อมูลชัดเจน", "รวดเร็ว", "กว้างขวาง", "รอบคอบ", "ซับซ้อน"),
        ),
    )

    fun questions(language: String): List<LanguagePlacementQuestion> = banks[language] ?: banks.getValue("English")

    fun result(language: String, selectedAnswers: List<Int>): LanguagePlacementResult {
        val questions = questions(language)
        val scores = bands.map { band ->
            LanguagePlacementBandScore(band, questions.zip(selectedAnswers).count { (question, selected) -> question.band == band && question.answerIndex == selected })
        }
        val firstUnpassedIndex = scores.indexOfFirst { it.correct < 3 }
        val foundationIndex = if (firstUnpassedIndex < 0) scores.lastIndex else firstUnpassedIndex - 1
        val level = scores.getOrNull(foundationIndex)?.band ?: "Pre-A1"
        val frontier = scores.getOrNull(foundationIndex + 1)
        val transitionBand = frontier?.takeIf { it.correct >= 2 }
        val startingLevel = transitionBand?.band ?: level
        val estimatedRange = when {
            foundationIndex < 0 && transitionBand != null -> "Pre-A1–A1"
            foundationIndex < 0 -> "Pre-A1"
            foundationIndex == scores.lastIndex -> "C1+"
            transitionBand != null -> "$level–${transitionBand.band}"
            else -> level
        }
        val next = scores.firstOrNull { it.correct < 3 }?.band ?: "C2"
        val focus = when (next) {
            "C2" -> "C1 advanced reading and precision"
            "Pre-A1" -> "A1 everyday words and sentence patterns"
            else -> "$next vocabulary, grammar and reading"
        }
        return LanguagePlacementResult(language, level, estimatedRange, startingLevel, scores.sumOf { it.correct }, questions.size, scores, focus)
    }

    private fun item(band: String, prompt: String, answerIndex: Int, explanation: String, vararg options: String) =
        LanguagePlacementQuestion(band, prompt, options.toList(), answerIndex, explanation)
}
