package io.github.saberbird.cidget.core

import kotlin.test.Test
import kotlin.test.assertEquals

class TerminalTest {
    @Test
    fun widgetTextIsShellPrompt() {
        assertEquals("~/ $ ci", Terminal.WIDGET_TEXT)
    }
}
