# IA local para Let’sStudy: viabilidad y validación

> Documento histórico de la etapa exclusivamente local. La app actual añade generación rápida con Firebase AI Logic y conserva la IA en el móvil como alternativa. Consulta el [README](../README.md) y la [arquitectura actual](architecture.md).

Fecha de actualización: 2026-10-06. Estado: prototipo implementado y APK de depuración instalado en el Samsung SM-S911B. **La inferencia real todavía no se ha validado**: el modelo de 2,59 GB no se ha descargado ni cargado en ese teléfono.

## Resultado hasta ahora

La arquitectura es viable para probar sin cuentas de IA, claves, cuotas de proveedor ni backend de inferencia. Let’sStudy incorpora descarga dentro de la app y una ruta local para crear tandas, evaluar respuestas y guardar sesiones. El teléfono solo necesita conexión para descargar el modelo por primera vez y para leer una oferta pública desde su URL.

Verificaciones de software completadas:

- `:app:testDebugUnitTest` pasó y `:app:assembleDebug` generó el APK.
- El APK se instaló en un dispositivo ADB Samsung SM-S911B; Android aceptó la orden de abrir la actividad.
- Un build de depuración también pasó cuando `google-services.json` se retiró temporalmente y luego se restauró intacto.
- El árbol de dependencias Android no incluye Firebase AI Logic ni App Check.
- Se preserva la configuración Firebase local del propietario, pero el build la ignora y `.gitignore` evita añadirla a Git.

## Modelo y motor seleccionados

- Motor Android: LiteRT-LM 0.17.1.
- Modelo: Gemma 4 E2B instruction-tuned, variante LiteRT de `litert-community`.
- Revisión fijada: `b3ca0d2f076785a8f4b2219ddbd2bdb99954eae1`.
- Archivo: `gemma-4-E2B-it.litertlm`, tamaño exacto 2.588.147.712 bytes; SHA-256 `181938105e0eefd105961417e8da75903eacda102c4fce9ce90f50b97139a63c`.
- Gemma 4 está bajo Apache 2.0 según su ficha oficial. La app incluye una copia de la licencia, atribución y pantalla para verla.
- Se comprobó una solicitud HTTP Range anónima contra el host público durante la investigación inicial. No se ha completado todavía la descarga íntegra desde el teléfono.

La app guarda los pesos en `noBackupFilesDir`, valida tamaño y SHA-256, reanuda descargas interrumpidas, recomienda Wi-Fi y no comienza hasta que la persona pulsa el botón. Antes de usar datos móviles muestra una confirmación de 2,59 GB.

## Datos iniciales del teléfono conectado

- Modelo: Samsung SM-S911B.
- Al consultar almacenamiento, `/data` mostraba 21 GB disponibles.
- La memoria disponible del sistema en ese instante era aproximadamente 1,8 GiB; cambia según las apps abiertas.
- La pantalla del dispositivo estaba bloqueada durante la captura, así que no se pudo hacer inspección visual de la pantalla instalada.
- No hay datos medidos todavía de inicialización del motor, tiempo hasta la primera pregunta, duración de una tanda, consumo de memoria durante inferencia, temperatura, calidad, batería o uso sin conexión.

Esos valores son una foto puntual de capacidad libre y no garantizan que el motor pueda cargar correctamente. Gemma E2B puede tardar, calentar el teléfono o no cargar en algunos dispositivos. El primer modo usa el backend CPU para priorizar la compatibilidad.

## Qué debe probarse ahora

1. Desbloquear el teléfono y abrir Let’sStudy; en la tarjeta **Set up your study model**, pulsar **Download on Wi‑Fi** si se acepta la descarga grande.
2. Esperar a que la app termine y verifique el modelo. La descarga es reanudable y tiene notificación de progreso.
3. Pegar una descripción de oferta o introducir una URL pública. Si la página está bloqueada, usar **Paste text**.
4. Crear la primera tanda; confirmar 15 preguntas en el idioma elegido, respuestas y feedback local.
5. Continuar una vez y comprobar otras 15 preguntas; luego cerrar la app, activar modo avión y abrir la sesión guardada.
6. Registrar tiempos, memoria/temperatura y cualquier error de LiteRT-LM. Hasta que esto se complete, el prototipo no debe anunciarse como compatible con todos los Android.

No se deben registrar tokens de App Check, porque esta ruta no usa Firebase AI. No se hacen llamadas de generación en la nube como alternativa.

## Alcance actual

Este documento describe el prototipo inicial de IA local. Desde la versión 0.9, el workspace también ofrece temas, importación PDF/PPTX/TXT y nueve formatos de práctica. El tutor y las primeras mini-lecciones usan Gemini online de forma explícita; no son funciones sin conexión. Las páginas con inicio de sesión, JavaScript obligatorio o controles de acceso requieren pegar el texto; no se intentan eludir. El README describe el alcance vigente.

## Fuentes primarias

- [Ficha oficial de Gemma 4](https://ai.google.dev/gemma/docs/core/model_card_4)
- [Licencia Apache 2.0 de Gemma 4](https://ai.google.dev/gemma/apache_2)
- [Repositorio del modelo LiteRT](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
- [SDK Kotlin de LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md)
