// "Replace with 'getX()'" "true"

interface X {
    @Deprecated("", ReplaceWith("getX()"))
    val x: String

    fun getX(): String
}

fun foo(x: X): String {
    return x.<caret>x
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix