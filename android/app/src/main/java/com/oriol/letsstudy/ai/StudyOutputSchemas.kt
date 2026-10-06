package com.oriol.letsstudy.ai

object StudyOutputSchemas {
    val INITIAL_BATCH = """
        {
          "type": "object",
          "properties": {
            "sourceReadable": { "type": "boolean" },
            "title": { "type": "string" },
            "summary": { "type": "string" },
            "requirements": { "type": "array", "items": { "type": "string" } },
            "sourceContext": { "type": "string" },
            "coveredTopicsSummary": { "type": "string" },
            "questions": {
              "type": "array", "minItems": 5, "maxItems": 5,
              "items": {
                "type": "object",
                "properties": {
                  "category": { "type": "string" },
                  "prompt": { "type": "string" },
                  "topic": { "type": "string" },
                  "options": { "type": "array", "minItems": 4, "maxItems": 4, "items": { "type": "string" } },
                  "correctOptionIndex": { "type": "integer" },
                  "explanation": { "type": "string" },
                  "sourceBasis": { "type": "string" }
                },
                "required": ["category", "prompt", "topic", "options", "correctOptionIndex", "explanation", "sourceBasis"],
                "additionalProperties": false
              }
            }
          },
          "required": ["sourceReadable", "title", "summary", "requirements", "sourceContext", "coveredTopicsSummary", "questions"],
          "additionalProperties": false
        }
    """.trimIndent()

    val QUESTION_BATCH = """
        {
          "type": "object",
          "properties": {
            "coveredTopicsSummary": { "type": "string" },
            "questions": {
              "type": "array",
              "minItems": 5,
              "maxItems": 5,
              "items": {
                "type": "object",
                "properties": {
                  "category": { "type": "string" },
                  "prompt": { "type": "string" },
                  "topic": { "type": "string" },
                  "options": {
                    "type": "array",
                    "minItems": 4,
                    "maxItems": 4,
                    "items": { "type": "string" }
                  },
                  "correctOptionIndex": { "type": "integer" },
                  "explanation": { "type": "string" },
                  "sourceBasis": { "type": "string" }
                },
                "required": [
                  "category",
                  "prompt",
                  "topic",
                  "options",
                  "correctOptionIndex",
                  "explanation",
                  "sourceBasis"
                ],
                "additionalProperties": false
              }
            }
          },
          "required": ["coveredTopicsSummary", "questions"],
          "additionalProperties": false
        }
    """.trimIndent()
}
