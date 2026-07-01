package com.migul.treningsprogram.ui.log

/**
 * Item 9 — the pure state machine behind the calculator-style weight keypad.
 *
 * When the user taps the weight field, they get a keypad with digits, a decimal point, and **+ / −
 * only** (no × ÷). They can enter a value, then press + or − and another number to adjust it
 * arithmetically — the plate-loading mental model ("I have 60 on the bar, add a 5" → 65). The weight
 * field always holds the **live resolved total**, so it is a valid plain number for logging at any
 * moment (no need to press "="). Subtraction floors at 0 (matching the existing − step button).
 *
 * kg only, total value (not per-side). Pure so it is fully unit-testable off-device.
 */
object WeightCalculator {

    enum class Op { ADD, SUB }

    /**
     * @param base     the captured left operand once an operator is armed (0 before that).
     * @param op       the armed operator, or null when the user is just typing a plain number.
     * @param operand  digits typed for the current right-hand operand (or the whole number when no op).
     * @param hasBase  true once an operator has been pressed (so [value] folds base ± operand).
     * @param replaceNext true right after seeding from the field, so the first digit REPLACES the
     *                 seeded value (typing a fresh weight) rather than appending to it.
     */
    data class State(
        val base: Float = 0f,
        val op: Op? = null,
        val operand: String = "",
        val hasBase: Boolean = false,
        val replaceNext: Boolean = false
    )

    /** Seed the pad from whatever is currently in the weight field ("" / "BW" → empty → 0). */
    fun fromField(text: String?): State {
        val cleaned = text?.trim().orEmpty()
        val f = cleaned.toFloatOrNull()
        return if (f == null) State() else State(operand = trimNumber(cleaned), replaceNext = true)
    }

    /** The live resolved total to show in the field and to log — floored at 0. */
    fun value(s: State): Float {
        val o = s.operand.toFloatOrNull() ?: 0f
        val v = if (!s.hasBase) o else when (s.op) {
            Op.ADD -> s.base + o
            Op.SUB -> s.base - o
            null -> o
        }
        return v.coerceAtLeast(0f)
    }

    /** Field text: the live total, formatted without a trailing ".0". */
    fun fieldText(s: State): String = formatWeight(value(s))

    /** A human-readable running expression for the pad's hint line, e.g. "60 + 5" or "60 −". */
    fun expr(s: State): String {
        if (!s.hasBase) return s.operand
        val sign = if (s.op == Op.SUB) "−" else "+"
        return if (s.operand.isEmpty()) "${formatWeight(s.base)} $sign"
        else "${formatWeight(s.base)} $sign ${s.operand}"
    }

    fun digit(s: State, d: Char): State {
        val cur = if (s.replaceNext) "" else s.operand
        // Avoid a leading run of zeros ("007") but allow "0.x".
        val next = if (cur == "0") d.toString() else cur + d
        return s.copy(operand = next, replaceNext = false)
    }

    fun dot(s: State): State {
        val cur = if (s.replaceNext) "" else s.operand
        val next = when {
            cur.contains('.') -> cur
            cur.isEmpty() -> "0."
            else -> "$cur."
        }
        return s.copy(operand = next, replaceNext = false)
    }

    /** Arm an operator: resolve the current value into [base], clear the operand, await the next number. */
    fun operator(s: State, newOp: Op): State =
        State(base = value(s), op = newOp, operand = "", hasBase = true, replaceNext = false)

    fun backspace(s: State): State {
        if (s.operand.isNotEmpty()) return s.copy(operand = s.operand.dropLast(1), replaceNext = false)
        // Nothing left in the operand: drop the armed operator (back to the base as a plain number).
        if (s.hasBase) return State(operand = formatWeight(s.base))
        return State()
    }

    fun clear(): State = State()

    private fun formatWeight(w: Float): String =
        if (w == w.toInt().toFloat()) w.toInt().toString() else w.toString()

    /** Normalises a seeded "60.0" → "60" so it echoes the same as fresh entry. */
    private fun trimNumber(text: String): String {
        val f = text.toFloatOrNull() ?: return text
        return formatWeight(f)
    }
}
