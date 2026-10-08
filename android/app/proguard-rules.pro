# Keep Room entities and generated implementations intact.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
-keep class com.oriol.letsstudy.data.** { *; }
# PDFBox's optional JPX image decoder is intentionally absent: imports extract text only.
-dontwarn com.gemalto.jp2.JP2Decoder
