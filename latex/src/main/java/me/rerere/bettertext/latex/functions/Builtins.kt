package me.rerere.bettertext.latex.functions

/** Registers every function, environment and builder exactly once. */
internal object Builtins {
    init {
        registerOrd()
        registerSupSub()
        // delimiters, roots, fractions
        registerDelimSizing()
        registerSqrt()
        registerGenFrac()
        // environments and arrays
        registerEnvironment()
        registerArray()
        registerCr()
        // accents and stretchy decorations
        registerAccent()
        registerHorizBrace()
        registerArrow()
        registerEnclose()
        registerOverline()
        // operators, fonts, styles and the rest
        registerOp()
        registerOperatorName()
        registerMClass()
        registerColor()
        registerFont()
        registerText()
        registerStyling()
        registerKern()
        registerPhantom()
        registerMisc()
    }

    fun ensureRegistered() = Unit
}
