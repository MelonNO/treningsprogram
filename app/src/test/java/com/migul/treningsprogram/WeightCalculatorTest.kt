package com.migul.treningsprogram

import com.migul.treningsprogram.ui.log.WeightCalculator
import com.migul.treningsprogram.ui.log.WeightCalculator.Op
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Item 9 — the +/− calculator keypad state machine. Covers the plate-loading flow, repeated adds,
 * the floor-at-0 rule, and that plain typing still just works.
 */
class WeightCalculatorTest {

    private fun typeDigits(start: WeightCalculator.State, s: String): WeightCalculator.State {
        var st = start
        for (c in s) st = if (c == '.') WeightCalculator.dot(st) else WeightCalculator.digit(st, c)
        return st
    }

    @Test fun plainNumberEntry() {
        val s = typeDigits(WeightCalculator.State(), "60")
        assertEquals(60f, WeightCalculator.value(s), 0f)
        assertEquals("60", WeightCalculator.fieldText(s))
    }

    @Test fun addFive_givesSixtyFive() {
        var s = typeDigits(WeightCalculator.State(), "60")
        s = WeightCalculator.operator(s, Op.ADD)
        s = WeightCalculator.digit(s, '5')
        assertEquals(65f, WeightCalculator.value(s), 0f)
        assertEquals("65", WeightCalculator.fieldText(s))
    }

    @Test fun repeatedAdds_chain() {
        var s = typeDigits(WeightCalculator.State(), "60")
        s = WeightCalculator.operator(s, Op.ADD); s = WeightCalculator.digit(s, '5')  // 65
        s = WeightCalculator.operator(s, Op.ADD); s = WeightCalculator.digit(s, '5')  // 70
        assertEquals(70f, WeightCalculator.value(s), 0f)
    }

    @Test fun subtract() {
        var s = typeDigits(WeightCalculator.State(), "100")
        s = WeightCalculator.operator(s, Op.SUB); s = typeDigits(s, "5")
        assertEquals(95f, WeightCalculator.value(s), 0f)
    }

    @Test fun subtractionFloorsAtZero() {
        var s = typeDigits(WeightCalculator.State(), "5")
        s = WeightCalculator.operator(s, Op.SUB); s = typeDigits(s, "10")
        assertEquals(0f, WeightCalculator.value(s), 0f)
        assertEquals("0", WeightCalculator.fieldText(s))
    }

    @Test fun seededField_firstDigitReplaces() {
        // Field seeded with "60"; typing 8 should mean a fresh 80, not "608".
        var s = WeightCalculator.fromField("60")
        s = WeightCalculator.digit(s, '8')
        s = WeightCalculator.digit(s, '0')
        assertEquals(80f, WeightCalculator.value(s), 0f)
    }

    @Test fun seededField_thenAdd_usesSeedAsBase() {
        var s = WeightCalculator.fromField("60")
        s = WeightCalculator.operator(s, Op.ADD); s = WeightCalculator.digit(s, '5')
        assertEquals(65f, WeightCalculator.value(s), 0f)
    }

    @Test fun decimalEntry() {
        val s = typeDigits(WeightCalculator.State(), "62.5")
        assertEquals(62.5f, WeightCalculator.value(s), 0f)
        assertEquals("62.5", WeightCalculator.fieldText(s))
    }

    @Test fun onlyOneDecimalPoint() {
        var s = typeDigits(WeightCalculator.State(), "1.2")
        s = WeightCalculator.dot(s)          // ignored
        s = WeightCalculator.digit(s, '5')
        assertEquals(1.25f, WeightCalculator.value(s), 0f)
    }

    @Test fun backspaceTrimsOperand() {
        var s = typeDigits(WeightCalculator.State(), "62")
        s = WeightCalculator.backspace(s)
        assertEquals(6f, WeightCalculator.value(s), 0f)
    }

    @Test fun backspaceAfterOperator_dropsOperator() {
        var s = typeDigits(WeightCalculator.State(), "60")
        s = WeightCalculator.operator(s, Op.ADD)  // armed, operand empty
        s = WeightCalculator.backspace(s)         // drop the +, back to 60
        assertEquals(60f, WeightCalculator.value(s), 0f)
    }

    @Test fun clearResetsToZero() {
        var s = typeDigits(WeightCalculator.State(), "999")
        s = WeightCalculator.clear()
        assertEquals(0f, WeightCalculator.value(s), 0f)
    }

    @Test fun fromBlankOrBwField_isZero() {
        assertEquals(0f, WeightCalculator.value(WeightCalculator.fromField("")), 0f)
        assertEquals(0f, WeightCalculator.value(WeightCalculator.fromField("BW")), 0f)
        assertEquals(0f, WeightCalculator.value(WeightCalculator.fromField(null)), 0f)
    }

    @Test fun expr_showsPendingOperation() {
        var s = WeightCalculator.fromField("60")
        s = WeightCalculator.operator(s, Op.ADD)
        assertEquals("60 +", WeightCalculator.expr(s))
        s = WeightCalculator.digit(s, '5')
        assertEquals("60 + 5", WeightCalculator.expr(s))
    }
}
