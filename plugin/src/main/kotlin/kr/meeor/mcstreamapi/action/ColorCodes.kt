package kr.meeor.mcstreamapi.action

private val HEX_COLOR_PATTERN = Regex("(?i)&#([0-9a-f]{6})")
private val LEGACY_COLOR_PATTERN = Regex("(?i)&([0-9a-fk-orx])")

internal fun translateColorCodes(message: String): String {
    val withHexColors = HEX_COLOR_PATTERN.replace(message) { match ->
        buildString {
            append('§')
            append('x')
            match.groupValues[1].forEach { character ->
                append('§')
                append(character)
            }
        }
    }
    val translated = LEGACY_COLOR_PATTERN.replace(withHexColors) { match ->
        "§${match.groupValues[1].lowercase()}"
    }
    return preserveBoldAcrossColors(translated)
}

private fun preserveBoldAcrossColors(message: String): String = buildString(message.length) {
    var bold = false
    var index = 0
    while (index < message.length) {
        if (message[index] != '§' || index + 1 >= message.length) {
            append(message[index++])
            continue
        }

        val code = message[index + 1].lowercaseChar()
        if (code == 'x' && message.hasHexColorAt(index)) {
            append(message, index, index + HEX_COLOR_CODE_LENGTH)
            if (bold) append("§l")
            index += HEX_COLOR_CODE_LENGTH
            continue
        }

        append('§').append(message[index + 1])
        when (code) {
            'l' -> bold = true
            'r' -> bold = false
            in '0'..'9', in 'a'..'f' -> if (bold) append("§l")
        }
        index += 2
    }
}

private fun String.hasHexColorAt(index: Int): Boolean {
    if (index + HEX_COLOR_CODE_LENGTH > length) return false
    return (0 until 6).all { part ->
        val sectionIndex = index + 2 + (part * 2)
        this[sectionIndex] == '§' && this[sectionIndex + 1].isHexDigit()
    }
}

private fun Char.isHexDigit(): Boolean = this in '0'..'9' || lowercaseChar() in 'a'..'f'

private const val HEX_COLOR_CODE_LENGTH = 14
