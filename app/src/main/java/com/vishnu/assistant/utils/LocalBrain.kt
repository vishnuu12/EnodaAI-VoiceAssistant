package com.vishnu.assistant.utils

import android.content.Context
import android.os.BatteryManager
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Offline answering engine.
 *
 * When the user selects Offline mode (or when no network is
 * available), EnodaAI answers from the device itself:
 * time, date, battery, simple math and small talk - fully
 * in Tamil or English, with no internet needed.
 *
 * App opening and phone calls are handled separately by
 * [LocalCommandHandler] and [PhoneCallHandler] before the
 * brain is consulted.
 */
class LocalBrain(
    private val context: Context
) {

    enum class Language { ENGLISH, TAMIL }

    /**
     * Returns a spoken-style reply, or null when the brain
     * has nothing to say (caller may then show the offline
     * fallback message itself).
     */
    fun answer(
        message: String,
        language: Language
    ): String? {

        val text = message.trim().lowercase()

        val tamil = language == Language.TAMIL

        // ---------------------------------------------------------
        // Time
        // ---------------------------------------------------------

        val asksTime =
            Regex("\\btime\\b|\\bclock\\b").containsMatchIn(text) ||
                text.contains("\u0ba8\u0bc7\u0bb0\u0bae\u0bcd") ||
                text.contains("\u0bae\u0ba3\u0bbf") ||
                text.contains("\u0b8e\u0ba9\u0bcd\u0ba9 \u0ba8\u0bc7\u0bb0\u0bae\u0bcd")

        if (asksTime) {

            val now = SimpleDateFormat(
                "h:mm a",
                Locale.ENGLISH
            ).format(Date())

            return if (tamil) {
                "இப்போ மணி $now."
            } else {
                "It is $now now."
            }
        }

        // ---------------------------------------------------------
        // Date / day
        // ---------------------------------------------------------

        val asksDate =
            Regex("\\bdate\\b|\\btoday\\b|\\bday\\b").containsMatchIn(text) ||
                text.contains("\u0ba4\u0bc7\u0ba4\u0bbf") ||
                text.contains("\u0b87\u0ba9\u0bcd\u0bb1\u0bc1")

        if (asksDate) {

            val today = SimpleDateFormat(
                "EEEE, d MMMM yyyy",
                Locale.ENGLISH
            ).format(Date())

            return if (tamil) {
                "இன்று $today."
            } else {
                "Today is $today."
            }
        }

        // ---------------------------------------------------------
        // Battery
        // ---------------------------------------------------------

        val asksBattery = listOf(
            "battery", "charge",
            "பேட்டரி", "சார்ஜ்"
        ).any { text.contains(it) }

        if (asksBattery) {

            val level = batteryLevel()

            return if (level == null) {
                if (tamil) {
                    "பேட்டரி நிலையை படிக்க முடியவில்லை."
                } else {
                    "I could not read the battery level."
                }
            } else if (tamil) {
                "பேட்டரி $level சதவீதம் இருக்கு."
            } else {
                "Battery is at $level percent."
            }
        }

        // ---------------------------------------------------------
        // Math
        // ---------------------------------------------------------

        evaluateMath(text)?.let { value ->

            val pretty = if (value == value.toLong().toDouble()) {
                value.toLong().toString()
            } else {
                String.format(Locale.ENGLISH, "%.2f", value)
            }

            return if (tamil) {
                "பதில் $pretty."
            } else {
                "The answer is $pretty."
            }
        }

        // ---------------------------------------------------------
        // Small talk
        // ---------------------------------------------------------

        return smallTalk(text, tamil)
    }

    /**
     * Bilingual small-talk table. Returns null when nothing
     * matches, so the caller can decide the fallback.
     */
    private fun smallTalk(
        text: String,
        tamil: Boolean
    ): String? {

        if (tamil) {

            return when {
                matchesAny(
                    text,
                    "வணக்கம்", "hi", "hello", "hey"
                ) ->
                    "வணக்கம்! நான் உங்க உதவியாளர் EnodaAI. " +
                        "எதுவும் கேளுங்க."

                matchesAny(
                    text,
                    "எப்படி இருக்கீங்க", "எப்படி இருக்க"
                ) ->
                    "நான் நல்லா இருக்கேன், நீங்க எப்படி இருக்கீங்க?"

                matchesAny(
                    text,
                    "நன்றி", "தேங்க்ஸ்", "thanks"
                ) ->
                    "நல்ல மனசுட்டு பேசுறீங்க! இன்னும் " +
                        "ஏதாவது வேணுமா?"

                matchesAny(
                    text,
                    "யார் நீ", "உங்க பெயர்", "who are you"
                ) ->
                    "நான் EnodaAI, உங்க குரல் உதவியாளர். " +
                        "ஆப்பை திறக்கவும், call பண்ணவும், " +
                        "கேள்வி கேக்கவும் சொல்லுங்க."

                matchesAny(
                    text,
                    "bye", "போய் வா", "tata"
                ) ->
                    "சரி, போங்க. கவலை படாதீங்க, " +
                        "நான் இருக்கேன்!"

                else -> null
            }
        }

        return when {
            matchesAny(text, "hello", "hi", "hey", "vanakkam") ->
                "Hello! I am EnodaAI, your voice assistant. " +
                    "What can I do for you?"

            matchesAny(text, "how are you") ->
                "I am doing great! How about you?"

            matchesAny(text, "thank you", "thanks", "nandri") ->
                "Anytime! Anything else you need?"

            matchesAny(text, "who are you", "your name") ->
                "I am EnodaAI, your voice assistant. I can " +
                    "open apps, make calls and answer questions."

            matchesAny(text, "bye", "goodbye") ->
                "Okay, take care! I will be right here."

            else -> null
        }
    }

    private fun matchesAny(
        text: String,
        vararg phrases: String
    ): Boolean {

        // Word-boundary matching so short words such as "hi"
        // never match inside "this" or "which".
        return phrases.any { phrase ->

            val escaped = Regex.escape(phrase)

            Regex(
                "\\b$escaped\\b",
                RegexOption.IGNORE_CASE
            ).containsMatchIn(text)
        }
    }

    /**
     * Evaluates simple arithmetic ("12 * 8 + 5", "40 / 8").
     * Uses a tiny shunting-yard parser - no scripting engine,
     * no dependencies, no eval.
     */
    private fun evaluateMath(text: String): Double? {

        val expression = extractMathExpression(text)
            ?: return null

        if (expression.isEmpty()) {
            return null
        }

        return try {
            MathParser().evaluate(expression)
        } catch (exception: Exception) {
            null
        }
    }

    /**
     * Picks the math part out of sentences such as
     * "what is 12 times 8" or "45 + 89 எவ்வளவு".
     */
    private fun extractMathExpression(text: String): String? {

        var working = text
            .replace("plus", "+")
            .replace("minus", "-")
            .replace("times", "*")
            .replace("multiplied by", "*")
            .replace("into", "*")
            .replace("divided by", "/")
            .replace("கூட்டல்", "+")
            .replace("கழித்தல்", "-")
            .replace("பெருக்கல்", "*")
            .replace("வகுத்தல்", "/")

        // Words that make "into"/"plus" false positives less likely:
        // require at least one operator and two digits.
        val match = Regex("[0-9]\\d*(?:[ .]\\d+)?(?:\\s*[+\\-*/%()]\\s*[0-9]\\d*(?:[ .]\\d+)?)+")
            .findAll(working)
            .map { it.value.replace(" ", "") }
            .firstOrNull { expression ->
                expression.length >= 3
            }

        return match
    }

    private fun batteryLevel(): Int? {

        return try {

            val intent = context.registerReceiver(
                null,
                android.content.IntentFilter(
                    android.content.Intent.ACTION_BATTERY_CHANGED
                )
            ) ?: return null

            val level = intent.getIntExtra(
                BatteryManager.EXTRA_LEVEL,
                -1
            )

            val scale = intent.getIntExtra(
                BatteryManager.EXTRA_SCALE,
                -1
            )

            if (level < 0 || scale <= 0) {
                null
            } else {
                (level * 100.0 / scale).roundToLong().toInt()
            }
        } catch (exception: Exception) {
            null
        }
    }
}

/**
 * Minimal shunting-yard evaluator for + - * / % and parentheses.
 */
private class MathParser {

    private val numbers = ArrayDeque<Double>()

    private val operators = ArrayDeque<Char>()

    fun evaluate(expression: String): Double {

        var index = 0

        while (index < expression.length) {

            val character = expression[index]

            when {

                character.isDigit() || character == '.' -> {

                    var end = index

                    while (
                        end < expression.length &&
                        (expression[end].isDigit() ||
                            expression[end] == '.')
                    ) {
                        end++
                    }

                    numbers.addLast(
                        expression
                            .substring(index, end)
                            .toDouble()
                    )

                    index = end
                }

                character == '(' -> {
                    operators.addLast(character)
                    index++
                }

                character == ')' -> {

                    while (
                        operators.isNotEmpty() &&
                        operators.last() != '('
                    ) {
                        applyTopOperator()
                    }

                    if (operators.isEmpty()) {
                        throw IllegalArgumentException("Mismatched parentheses")
                    }

                    operators.removeLast()
                    index++
                }

                character in "+-*/%" -> {

                    while (
                        operators.isNotEmpty() &&
                        operators.last() != '(' &&
                        precedence(operators.last()) >=
                        precedence(character)
                    ) {
                        applyTopOperator()
                    }

                    operators.addLast(character)
                    index++
                }

                else -> {
                    // Skip anything unexpected.
                    index++
                }
            }
        }

        while (operators.isNotEmpty()) {
            applyTopOperator()
        }

        if (numbers.size != 1) {
            throw IllegalArgumentException("Invalid expression")
        }

        return numbers.last()
    }

    private fun precedence(operator: Char): Int =

        when (operator) {
            '+', '-' -> 1
            '*', '/', '%' -> 2
            else -> 0
        }

    private fun applyTopOperator() {

        val operator = operators.removeLastOrNull()
            ?: throw IllegalArgumentException("Missing operator")

        val right = numbers.removeLastOrNull()
            ?: throw IllegalArgumentException("Missing operand")

        val left = numbers.removeLastOrNull()
            ?: throw IllegalArgumentException("Missing operand")

        val result = when (operator) {
            '+' -> left + right
            '-' -> left - right
            '*' -> left * right
            '/' ->
                if (right == 0.0) {
                    throw IllegalArgumentException("Division by zero")
                } else {
                    left / right
                }

            '%' ->
                if (right == 0.0) {
                    throw IllegalArgumentException("Division by zero")
                } else {
                    left % right
                }

            else -> throw IllegalArgumentException("Unknown operator")
        }

        numbers.addLast(result)
    }
}
