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

fun StudyPlacementAttemptEntity.answers(): List<Int> = answersCsv.split(',').mapNotNull(String::toIntOrNull)

fun StudyPlacementAttemptEntity.toPlacementResult(): LanguagePlacementResult {
    val calculated = LanguagePlacementTest.result(language, answers(), bankVersion)
    return calculated.copy(
        level = level,
        estimatedRange = estimatedRange,
        startingLevel = startingLevel,
        correct = correct,
        total = total,
        nextFocus = nextFocus,
    )
}

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

    private val alternateBanks = mapOf(
        "English" to listOf(
            item("A1", "Those ___ my keys.", 0, "Use ‘are’ with the plural subject ‘those’. ", "are", "is", "am", "be"),
            item("A1", "Does he ___ near here?", 1, "After ‘does’, use the base form ‘live’. ", "lives", "live", "living", "lived"),
            item("A1", "We ___ lunch at noon.", 2, "Use ‘have’ for a regular meal or routine. ", "has", "having", "have", "had"),
            item("A1", "Where ___ she work?", 0, "Present-simple questions with ‘she’ use ‘does’. ", "does", "do", "is", "has"),
            item("A2", "She has worked here ___ two years.", 2, "Use ‘for’ with a length of time. ", "since", "during", "for", "from"),
            item("A2", "We were eating when the phone ___. ", 1, "A short event interrupting an action takes the simple past ‘rang’. ", "was ringing", "rang", "has rung", "rings"),
            item("A2", "There isn’t ___ milk left.", 2, "‘Milk’ is uncountable, so use ‘much’ in a negative sentence. ", "many", "few", "much", "several"),
            item("A2", "The shop is ___ than the bank.", 0, "Use the comparative adjective ‘closer’ before ‘than’. ", "closer", "closest", "close", "more close"),
            item("B1", "She asked me where I ___.", 1, "In reported speech about the past, ‘live’ usually shifts to ‘lived’. ", "live", "lived", "have lived", "am living"),
            item("B1", "The report ___ by Ana yesterday.", 2, "The past passive is ‘was’ plus the past participle. ", "wrote", "has written", "was written", "is writing"),
            item("B1", "I wish I ___ drive.", 0, "Use a past form after ‘wish’ for a present situation you want to change. ", "could", "can", "will", "am able"),
            item("B1", "We stopped ___ to check the map.", 3, "‘Stop to do’ means pause another activity in order to do something. ", "checking", "checked", "check", "to check"),
            item("B2", "Only after the meeting ___ why the proposal had changed.", 1, "A fronted ‘only after’ phrase triggers subject–auxiliary inversion. ", "we understood", "did we understand", "we did understood", "had we understand"),
            item("B2", "The team acted as though it ___ the final decision.", 0, "Use the past perfect for an imagined earlier action. ", "had made", "has made", "would make", "makes"),
            item("B2", "I’d sooner you ___ the details until the review ends.", 2, "‘Would sooner’ about another person’s action takes a past form. ", "don’t share", "won’t share", "didn’t share", "not sharing"),
            item("B2", "Not until the data was checked ___ the error.", 3, "A sentence beginning ‘not until’ uses inversion in the main clause. ", "the team identified", "identified the team", "the team did identify", "did the team identify"),
            item("C1", "Whatever the outcome, we will proceed ___. ", 2, "‘As planned’ is an adverbial phrase describing how the team will proceed. ", "as planning", "to planned", "as planned", "like plan"),
            item("C1", "Had it not been for the warning, we ___ the deadline.", 1, "This inverted third conditional takes ‘would have’ plus a past participle. ", "miss", "would have missed", "will miss", "had missed"),
            item("C1", "Her account is persuasive, not least ___ it is supported by independent records.", 0, "‘Not least because’ introduces an especially important reason. ", "because", "although", "whereas", "unless"),
            item("C1", "The issue is so nuanced that it ___ easy categorisation.", 3, "‘Defy’ means resist or not fit a simple description. ", "avoids to", "is defying to", "doesn’t defy to", "defies"),
        ),
        "Spanish" to listOf(
            item("A1", "Mis amigos ___ en Madrid.", 2, "Use ‘viven’ with the plural subject ‘mis amigos’. ", "vivo", "vive", "viven", "vivimos"),
            item("A1", "¿___ tienes una hermana?", 0, "The question asks whether ‘you’ have a sister: ‘¿Tienes…?’ ", "Tienes", "Tiene", "Tenemos", "Tener"),
            item("A1", "La tienda ___ a las nueve.", 1, "A shop opening time uses ‘abre’. ", "abren", "abre", "abro", "abrir"),
            item("A1", "Nosotros ___ español en clase.", 3, "The ‘nosotros’ form of ‘estudiar’ is ‘estudiamos’. ", "estudian", "estudio", "estudiáis", "estudiamos"),
            item("A2", "Cuando era pequeña, ___ al parque cada tarde.", 1, "A repeated past habit takes the imperfect ‘iba’. ", "fui", "iba", "iré", "he ido"),
            item("A2", "Este regalo es ___ mi hermana.", 3, "Use ‘para’ to identify the intended recipient. ", "por", "desde", "con", "para"),
            item("A2", "¿Puedes ___ la puerta, por favor?", 0, "After ‘puedes’, use the infinitive ‘cerrar’. ", "cerrar", "cierras", "cerrada", "cerrando"),
            item("A2", "A Marta ___ gustan los libros de historia.", 2, "‘Gustar’ agrees with plural ‘libros’: ‘le gustan’. ", "la", "lo", "le", "les"),
            item("B1", "Me alegra que tus padres ___ venir mañana.", 0, "An emotional reaction followed by ‘que’ takes the subjunctive ‘puedan’. ", "puedan", "pueden", "podrán", "podían"),
            item("B1", "El edificio ___ el año pasado.", 3, "The passive-style ‘se’ construction uses ‘construyó’ for a completed past event. ", "construye", "construía", "ha construido", "se construyó"),
            item("B1", "Si tuviera más tiempo, ___ un segundo idioma.", 2, "The second conditional pairs ‘si tuviera’ with ‘estudiaría’. ", "estudio", "estudiaré", "estudiaría", "estudiaba"),
            item("B1", "No encontramos a nadie que ___ ayudarnos.", 1, "An unknown person sought in a negative clause takes the subjunctive. ", "puede", "pueda", "podía", "pudo"),
            item("B2", "Por mucho que ___, no consiguió cambiar la decisión.", 2, "‘Por mucho que’ takes the subjunctive in this concessive clause. ", "insistía", "insistió", "insistiera", "insiste"),
            item("B2", "No solo mejoró el servicio, ___ también redujo los costes.", 0, "The paired structure is ‘no solo…, sino que también…’. ", "sino que", "pero", "aunque", "por eso"),
            item("B2", "De haber recibido el aviso, nos ___ preparado antes.", 3, "An unreal past condition uses ‘habríamos’ plus the participle. ", "hemos", "habíamos", "hubiéramos", "habríamos"),
            item("B2", "La medida, ___ eficaz en algunos casos, no resuelve todos los problemas.", 1, "‘Aunque’ introduces a concession followed by the indicative here. ", "a pesar", "aunque", "por lo tanto", "con tal de"),
            item("C1", "El comité aprobó la propuesta, ___ las reservas planteadas por varios miembros.", 0, "‘Pese a’ introduces a noun phrase that contrasts with the decision. ", "pese a", "por lo que", "a fin de", "conforme"),
            item("C1", "No es que la evidencia ___ insuficiente, sino que aún debe interpretarse.", 2, "‘No es que’ commonly introduces the subjunctive ‘sea’. ", "es", "será", "sea", "fuera"),
            item("C1", "La decisión fue cuestionada, no tanto por su coste ___ por sus consecuencias.", 1, "The contrastive pairing is ‘no tanto…, sino por…’. ", "como", "sino", "aunque", "mientras"),
            item("C1", "Cuanto más se contrastan las fuentes, ___ resulta la conclusión.", 3, "The paired comparative is ‘cuanto más…, más sólida’. ", "muy sólida", "la más sólida", "tan sólida", "más sólida"),
        ),
        "French" to listOf(
            item("A1", "Tu ___ souvent le bus.", 1, "The second-person present form of ‘prendre’ is ‘prends’. ", "prend", "prends", "prenons", "prendre"),
            item("A1", "Nous n’___ pas de voiture.", 2, "The first-person plural form of ‘avoir’ is ‘avons’. ", "a", "ont", "avons", "avez"),
            item("A1", "Quel âge ___-tu ?", 3, "The expression is ‘Quel âge as-tu ?’ ", "est", "es", "a", "as"),
            item("A1", "Les enfants ___ dans le jardin.", 0, "The plural subject takes ‘jouent’. ", "jouent", "joue", "jouons", "jouez"),
            item("A2", "Quand j’étais petite, je ___ au bord de la mer.", 2, "A repeated or descriptive past situation uses the imparfait ‘vivais’. ", "vis", "ai vécu", "vivais", "vivrai"),
            item("A2", "Il faut acheter ___ pain pour le dîner.", 1, "Use the partitive article ‘du’ with an unspecified amount of bread. ", "de la", "du", "des", "un"),
            item("A2", "Nous irons au musée ___ il pleut.", 3, "‘Même s’il pleut’ means ‘even if it rains’. ", "malgré", "pendant", "parce qu’il", "même s’il"),
            item("A2", "Elle vient ___ finir son travail.", 0, "‘Venir de’ plus an infinitive describes something just completed. ", "de", "à", "pour", "en"),
            item("B1", "Je ne savais pas qu’il ___ déjà.", 2, "The departure happened before another past event: ‘était parti’. ", "est parti", "partira", "était parti", "part"),
            item("B1", "Il faut que tu ___ ce formulaire.", 1, "‘Il faut que’ is followed by the subjunctive ‘remplisses’. ", "remplis", "remplisses", "rempliras", "remplissais"),
            item("B1", "Si nous avions réservé, nous ___ une table.", 0, "A past unreal condition takes the conditional perfect ‘aurions eu’. ", "aurions eu", "avons eu", "aurons", "avions"),
            item("B1", "Elle a continué à travailler ___ elle était malade.", 3, "‘Même si’ introduces a concession followed by the indicative. ", "bien qu’", "malgré qu’", "afin qu’", "même si"),
            item("B2", "Ce n’est qu’après son départ ___ compris la décision.", 2, "The paired structure is ‘ce n’est qu’après… que…’. ", "qui j’ai", "dont j’ai", "que j’ai", "où j’ai"),
            item("B2", "Il a nié ___ les documents.", 0, "‘Nier’ can be followed by the infinitive passé ‘avoir modifié’. ", "avoir modifié", "à modifier", "de modifier", "modifiant à"),
            item("B2", "Il faudrait que chacun ___ sa part.", 3, "‘Il faudrait que’ takes the subjunctive ‘fasse’. ", "fait", "fera", "faisait", "fasse"),
            item("B2", "___ soient les difficultés, ils ont terminé.", 1, "‘Quelles que soient’ agrees with the feminine plural noun ‘difficultés’. ", "Quelque", "Quelles que", "Quels que", "Quelle que"),
            item("C1", "Encore faut-il que les résultats ___ confirmés.", 2, "The subjunctive ‘soient’ follows ‘il faut que’. ", "sont", "seront", "soient", "étaient"),
            item("C1", "Il n’en demeure pas moins ___ la décision doit être réexaminée.", 0, "The fixed expression is ‘il n’en demeure pas moins que’. ", "que", "dont", "si", "où"),
            item("C1", "Pour peu qu’il ___ davantage, le projet aboutira.", 3, "‘Pour peu que’ is followed by the subjunctive ‘s’investisse’. ", "s’investit", "s’investira", "s’investissait", "s’investisse"),
            item("C1", "Je ne saurais trop ___ l’importance de cette mesure.", 1, "The formal expression is ‘je ne saurais trop souligner’. ", "soulignant", "souligner", "souligné", "à souligner"),
        ),
        "Thai" to listOf(
            item("A1", "พวกเขา ___ กาแฟทุกเช้า", 1, "‘ดื่มกาแฟ’ หมายถึง drink coffee", "กิน", "ดื่ม", "ไป", "อ่าน"),
            item("A1", "พรุ่งนี้ฉัน ___ ไปทำงาน", 0, "ใช้ ‘จะ’ เพื่อบอกเหตุการณ์ในอนาคต", "จะ", "เคย", "กำลัง", "แล้ว"),
            item("A1", "หนังสืออยู่ ___ โต๊ะ", 2, "‘บนโต๊ะ’ หมายถึง on the table", "ใต้", "กับ", "บน", "จาก"),
            item("A1", "เขาไม่ ___ อาหารเผ็ด", 3, "‘ไม่ชอบ’ หมายถึง does not like", "เป็น", "มี", "อยู่", "ชอบ"),
            item("A2", "ฉัน ___ เขาตั้งแต่ปีที่แล้ว", 2, "‘รู้จัก’ ใช้บอกว่ารู้จักบุคคล", "รู้", "เรียน", "รู้จัก", "เห็น"),
            item("A2", "ระหว่างที่แม่ทำอาหาร ฉัน ___ โต๊ะ", 0, "‘จัดโต๊ะ’ หมายถึง set the table", "จัด", "ปิด", "เปิด", "ล้าง"),
            item("A2", "ร้านนี้ ___ วันอาทิตย์", 1, "‘ปิดวันอาทิตย์’ หมายถึง closed on Sundays", "เปิด", "ปิด", "เดิน", "อยู่"),
            item("A2", "ถ้าเราขึ้นรถไฟเร็ว เรา ___ ถึงก่อนเที่ยง", 3, "ใช้ ‘จะ’ เพื่อบอกผลที่คาดในอนาคต", "เคย", "กำลัง", "แล้ว", "จะ"),
            item("B1", "เขาบอกว่าเขา ___ งานเสร็จแล้ว", 1, "‘ทำงานเสร็จแล้ว’ หมายถึง has finished the work", "กำลังทำ", "ทำ", "จะทำ", "ทำต่อ"),
            item("B1", "แม้ว่าอากาศจะร้อน เราก็ ___ เดินต่อ", 3, "‘ยังคง’ ใช้บอกว่ายังคงทำสิ่งเดิม", "เพิ่ง", "เกือบ", "คงจะ", "ยังคง"),
            item("B1", "ถ้ารู้ก่อน ฉัน ___ ช่วยคุณ", 0, "‘คงจะช่วย’ บอกผลที่น่าจะเกิดในเงื่อนไขสมมติ", "คงจะ", "กำลัง", "เคย", "เพิ่ง"),
            item("B1", "ฉันกำลังคิด ___ ย้ายบ้าน", 2, "‘คิดจะย้าย’ หมายถึง thinking of moving", "ที่", "จาก", "จะ", "กับ"),
            item("B2", "ยิ่งอ่านมาก ___ เข้าใจมากขึ้น", 1, "โครงสร้าง ‘ยิ่ง…ก็ยิ่ง…’ แสดงความสัมพันธ์ที่เพิ่มขึ้น", "แต่", "ก็", "เพราะ", "หรือ"),
            item("B2", "งานนี้ควรทำให้ ___ ก่อนวันศุกร์", 0, "‘ทำให้เสร็จ’ หมายถึง finish it", "เสร็จ", "เปิด", "กลับ", "หาย"),
            item("B2", "แม้ว่าเขาจะมีประสบการณ์มาก ___ เขาก็ยังเรียนรู้ต่อ", 3, "‘แต่’ เชื่อมใจความที่ขัดแย้งกับ ‘แม้ว่า’", "หรือ", "เพราะ", "และ", "แต่"),
            item("B2", "เธออธิบายเรื่องนี้อย่าง ___ จนทุกคนเข้าใจ", 2, "‘ชัดเจน’ หมายถึง clear", "รวดเร็ว", "เงียบ", "ชัดเจน", "กว้าง"),
            item("C1", "ข้อเสนอได้รับการ ___ หลังจากหารืออย่างรอบคอบ", 1, "‘เห็นชอบ’ หมายถึง approved", "ปฏิเสธ", "เห็นชอบ", "เปลี่ยนแปลง", "หยุด"),
            item("C1", "ไม่ว่าผลจะเป็นอย่างไร เรา ___ ดำเนินงานต่อ", 3, "‘ยังคงดำเนินงานต่อ’ หมายถึง continue regardless", "เพิ่ง", "เกือบ", "อาจจะ", "ยังคง"),
            item("C1", "การตัดสินใจนี้มีผล ___ ต่อการวางแผนระยะยาว", 0, "‘อย่างมีนัยสำคัญ’ เป็นสำนวนทางการหมายถึง significantly", "อย่างมีนัยสำคัญ", "ในทันที", "โดยบังเอิญ", "อย่างใกล้ชิด"),
            item("C1", "ยิ่งหลักฐานชัดเจนเท่าไร ข้อสรุปก็ยิ่ง ___", 2, "‘สมเหตุสมผล’ หมายถึง reasonable", "รวดเร็ว", "เงียบ", "สมเหตุสมผล", "กว้างขวาง"),
        ),
    )

    fun questions(language: String, bankVersion: Int = 0): List<LanguagePlacementQuestion> {
        val source = if (bankVersion % 2 == 1) alternateBanks else banks
        return source[language] ?: source.getValue("English")
    }

    fun result(language: String, selectedAnswers: List<Int>, bankVersion: Int = 0): LanguagePlacementResult {
        val questions = questions(language, bankVersion)
        val scores = bands.map { band ->
            LanguagePlacementBandScore(band, questions.zip(selectedAnswers).count { (question, selected) -> question.band == band && question.answerIndex == selected })
        }
        val correct = scores.sumOf { it.correct }
        val strongestBandIndex = scores.indexOfLast { it.correct >= 3 }
        val supportedBandIndex = scores.indexOfLast { it.correct >= 2 }
        val scoreBandIndex = if (correct < 3) -1 else ((correct - 1) / 4).coerceAtMost(bands.lastIndex)
        val overallScoreCanRaiseEstimate = correct >= 12 && supportedBandIndex >= 0
        val estimatedBandIndex = maxOf(
            strongestBandIndex,
            if (overallScoreCanRaiseEstimate) minOf(scoreBandIndex, supportedBandIndex) else -1,
        )
        val level = bands.getOrNull(estimatedBandIndex) ?: "Pre-A1"
        val frontier = scores.getOrNull(estimatedBandIndex + 1)?.takeIf { it.correct >= 2 }
        val estimatedRange = if (frontier == null) level else "$level–${frontier.band}"
        val startingLevel = level
        val focus = when (level) {
            "Pre-A1" -> "A1 everyday words and sentence patterns"
            "C1" -> "C1 advanced reading and precision"
            else -> "$level vocabulary, grammar and reading"
        }
        return LanguagePlacementResult(language, level, estimatedRange, startingLevel, correct, questions.size, scores, focus)
    }

    private fun item(band: String, prompt: String, answerIndex: Int, explanation: String, vararg options: String) =
        LanguagePlacementQuestion(band, prompt, options.toList(), answerIndex, explanation)
}
