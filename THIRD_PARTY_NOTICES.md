# Third-party notices

## Study Workspace typography

DM Sans and Manrope are redistributed under the SIL Open Font License 1.1. Sources: [DM Sans](https://github.com/google/fonts/tree/main/ofl/dmsans) and [Manrope](https://github.com/google/fonts/tree/main/ofl/manrope), obtained 7 October 2026. Fonts are in `android/app/src/main/res/font`; full notices are in `docs/licenses` and APK `assets/licenses`.

## PDFBox Android

Version 2.0.27.0 from [TomRoush/PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) extracts imported PDF text. Apache 2.0 LICENSE and NOTICE are bundled in APK `assets/licenses`. The optional JPX image decoder is not bundled because imports extract text rather than render images. Settings exposes all bundled notices.

## Gemma 4 E2B instruction-tuned

The application offers an optional, one-time download of Gemma 4 E2B instruction-tuned, converted to LiteRT-LM format by `litert-community`. The model is authored by Google DeepMind and is licensed under the Apache License, Version 2.0. The Android app bundles a copy of that license at `android/app/src/main/assets/licenses/Apache-2.0.txt` and displays it from the model setup card.

Model repository: <https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm>

Official model card and license: <https://ai.google.dev/gemma/docs/core/model_card_4>

## LiteRT-LM

The Android app uses Google's LiteRT-LM Android runtime. See the [LiteRT-LM project](https://github.com/google-ai-edge/LiteRT-LM) for its source and license notices.
