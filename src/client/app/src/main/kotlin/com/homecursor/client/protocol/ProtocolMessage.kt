package com.homecursor.client.protocol

/** Why the host refused a connection. [code] is the wire value. */
enum class RejectReason(val code: Int) {
    Busy(1),
    UnsupportedVersion(2);

    companion object {
        fun fromCode(code: Int): RejectReason? = entries.firstOrNull { it.code == code }
    }
}

/** A mouse button. [code] is the wire value. */
enum class MouseButton(val code: Int) {
    Left(1),
    Right(2),
    Middle(3);

    companion object {
        fun fromCode(code: Int): MouseButton? = entries.firstOrNull { it.code == code }
    }
}

/**
 * A key of the keyboard. [code] is the wire value. Letters, digits and punctuation are physical keys of a US layout
 * (so they work in shortcuts such as Ctrl+C); text in other languages goes through [ProtocolMessage.Text].
 */
enum class SpecialKey(val code: Int) {
    Enter(1),
    Backspace(2),
    Tab(3),
    Escape(4),
    Delete(5),
    Insert(6),
    Left(7),
    Right(8),
    Up(9),
    Down(10),
    Home(11),
    End(12),
    PageUp(13),
    PageDown(14),
    Shift(15),
    Control(16),
    Alt(17),
    Windows(18),
    Space(19),
    F1(32),
    F2(33),
    F3(34),
    F4(35),
    F5(36),
    F6(37),
    F7(38),
    F8(39),
    F9(40),
    F10(41),
    F11(42),
    F12(43),
    A(64),
    B(65),
    C(66),
    D(67),
    E(68),
    F(69),
    G(70),
    H(71),
    I(72),
    J(73),
    K(74),
    L(75),
    M(76),
    N(77),
    O(78),
    P(79),
    Q(80),
    R(81),
    S(82),
    T(83),
    U(84),
    V(85),
    W(86),
    X(87),
    Y(88),
    Z(89),
    D0(96),
    D1(97),
    D2(98),
    D3(99),
    D4(100),
    D5(101),
    D6(102),
    D7(103),
    D8(104),
    D9(105),
    Minus(112),
    Equals(113),
    LeftBracket(114),
    RightBracket(115),
    Backslash(116),
    Semicolon(117),
    Quote(118),
    Comma(119),
    Period(120),
    Slash(121),
    Grave(122);

    /** A modifier is held while another key is pressed, so a shortcut presses it first and releases it last. */
    val isModifier: Boolean get() = this == Shift || this == Control || this == Alt || this == Windows

    companion object {
        fun fromCode(code: Int): SpecialKey? = entries.firstOrNull { it.code == code }
    }
}

/** Wire value of every message type. */
internal object MessageType {
    const val HELLO = 1
    const val WELCOME = 2
    const val REJECT = 3
    const val PING = 4
    const val PONG = 5
    const val MOUSE_MOVE = 16
    const val MOUSE_BUTTON = 17
    const val SCROLL = 18
    const val KEY = 19
    const val TEXT = 20
    const val DISCOVERY_PROBE = 32
    const val DISCOVERY_REPLY = 33
}

/**
 * The protocol messages a client sends or receives: discovering and connecting to a host, and sending pointer
 * and keyboard input.
 */
sealed interface ProtocolMessage {
    /** Client to host: first message on the TCP connection. */
    data class Hello(val protocolVersion: Int, val clientName: String) : ProtocolMessage

    /** Host to client: the connection is accepted. */
    data class Welcome(
        val protocolVersion: Int,
        val sessionId: Long,
        val udpPort: Int,
        val pingIntervalSeconds: Int,
    ) : ProtocolMessage

    /** Host to client: the connection is refused and will be closed. */
    data class Reject(val reason: RejectReason) : ProtocolMessage

    /** Client to host: keep-alive. */
    data object Ping : ProtocolMessage

    /** Host to client: answer to [Ping]. */
    data object Pong : ProtocolMessage

    /** Client to host: move the pointer by a relative number of pixels. Fits an Int16 on the wire. */
    data class MouseMove(val dx: Int, val dy: Int) : ProtocolMessage

    /**
     * Client to host: press or release a mouse button. Named after the change rather than `MouseButton` (as on the
     * host) so it does not hide the [MouseButton] enum inside this interface.
     */
    data class MouseButtonChange(val button: MouseButton, val isDown: Boolean) : ProtocolMessage

    /**
     * Client to host: scroll in 1/120 of a wheel notch. Positive is up ([vertical]) or right ([horizontal]).
     * Fits an Int16 on the wire.
     */
    data class Scroll(val horizontal: Int, val vertical: Int) : ProtocolMessage

    /** Client to host: press or release a key. Reliable, so a release is never lost. */
    data class Key(val key: SpecialKey, val isDown: Boolean) : ProtocolMessage

    /**
     * Client to host: type text, character by character. At most [ProtocolConstants.MAX_STRING_BYTES] UTF-8 bytes;
     * `\n` and `\t` are typed as Enter and Tab.
     */
    data class Text(val text: String) : ProtocolMessage

    /** Client to host (UDP broadcast): who is out there? */
    data class DiscoveryProbe(val protocolVersion: Int) : ProtocolMessage

    /** Host to client (UDP unicast): the answer to a [DiscoveryProbe]. */
    data class DiscoveryReply(
        val protocolVersion: Int,
        val hostName: String,
        val tcpPort: Int,
        val udpPort: Int,
        val isBusy: Boolean,
    ) : ProtocolMessage
}
