package com.m57.hermescontrol.ui.chat.markdown

/**
 * Where a reply that is still being written can be cut into a settled part and a growing tail:
 * just after its last blank line that isn't inside a code fence. Everything before the cut is
 * finished Markdown that won't change, so it can be parsed and laid out once instead of on
 * every new word; only the tail is redrawn as text arrives. Returns 0 when there is no cut yet.
 */
fun streamingSettledLength(text: String): Int {
    var cut = 0
    var inFence = false
    var lineStart = 0
    while (lineStart <= text.length) {
        val lineEnd = text.indexOf('\n', lineStart).let { if (it < 0) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inFence = !inFence
        } else if (!inFence && line.isBlank() && lineStart > 0 && lineEnd < text.length) {
            cut = lineEnd + 1
        }
        lineStart = lineEnd + 1
    }
    return cut
}
