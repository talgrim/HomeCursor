package com.homecursor.client.shortcuts

import com.homecursor.client.protocol.SpecialKey.A
import com.homecursor.client.protocol.SpecialKey.Alt
import com.homecursor.client.protocol.SpecialKey.C
import com.homecursor.client.protocol.SpecialKey.Control
import com.homecursor.client.protocol.SpecialKey.D
import com.homecursor.client.protocol.SpecialKey.Enter
import com.homecursor.client.protocol.SpecialKey.Escape
import com.homecursor.client.protocol.SpecialKey.F11
import com.homecursor.client.protocol.SpecialKey.F4
import com.homecursor.client.protocol.SpecialKey.Space
import com.homecursor.client.protocol.SpecialKey.Tab
import com.homecursor.client.protocol.SpecialKey.V
import com.homecursor.client.protocol.SpecialKey.Windows
import com.homecursor.client.protocol.SpecialKey.Z

/** The shortcuts a fresh install starts with, and that "restore defaults" brings back. */
object DefaultShortcuts {
    val all: List<Shortcut> = listOf(
        Shortcut("default.fullscreen", "Fullscreen video", listOf(Alt, Enter)),
        Shortcut("default.play-pause", "Play / pause", listOf(Space)),
        Shortcut("default.escape", "Escape", listOf(Escape)),
        Shortcut("default.fullscreen-f11", "Fullscreen (F11)", listOf(F11)),
        Shortcut("default.switch-window", "Switch window", listOf(Alt, Tab)),
        Shortcut("default.close-window", "Close window", listOf(Alt, F4)),
        Shortcut("default.show-desktop", "Show desktop", listOf(Windows, D)),
        Shortcut("default.select-all", "Select all", listOf(Control, A)),
        Shortcut("default.copy", "Copy", listOf(Control, C)),
        Shortcut("default.paste", "Paste", listOf(Control, V)),
        Shortcut("default.undo", "Undo", listOf(Control, Z)),
    )
}
