package me.rerere.bettertext.latex.parse

import me.rerere.bettertext.latex.ParseError

/**
 * A space of nameable things (macros) that can be set globally or local to a group,
 * using an undo stack the way TeX does.
 */
internal class Namespace<V : Any>(
    private val builtins: Map<String, V>,
    private val current: MutableMap<String, V?> = HashMap(),
) {
    private class Undo<V>(val value: V?)

    private val undefStack = ArrayList<HashMap<String, Undo<V>>>()

    fun beginGroup() {
        undefStack.add(HashMap())
    }

    fun endGroup() {
        if (undefStack.isEmpty()) {
            throw ParseError("Unbalanced namespace destruction: attempt to pop global namespace")
        }
        val undefs = undefStack.removeAt(undefStack.size - 1)
        for ((key, undo) in undefs) {
            if (undo.value == null) current.remove(key) else current[key] = undo.value
        }
    }

    fun endGroups() {
        while (undefStack.isNotEmpty()) endGroup()
    }

    fun has(name: String): Boolean =
        (current.containsKey(name) && current[name] != null) || builtins.containsKey(name)

    fun get(name: String): V? =
        if (current.containsKey(name)) current[name] else builtins[name]

    /**
     * Set the current value of a name, and optionally set it globally too.
     * Local set() sets the current value and (when appropriate) adds an undo
     * operation to the undo stack. Global set() may change the undo operation
     * at every level, so takes time linear in their number.
     */
    fun set(name: String, value: V?, global: Boolean = false) {
        if (global) {
            for (undefs in undefStack) undefs.remove(name)
            if (undefStack.isNotEmpty()) {
                undefStack.last()[name] = Undo(value)
            }
        } else if (undefStack.isNotEmpty()) {
            val top = undefStack.last()
            if (!top.containsKey(name)) {
                top[name] = Undo(current[name])
            }
        }
        if (value == null) current.remove(name) else current[name] = value
    }
}
