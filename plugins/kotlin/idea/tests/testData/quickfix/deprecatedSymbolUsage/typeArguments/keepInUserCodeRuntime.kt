// "Replace with 'newFun(p)'" "true"
// WITH_STDLIB

@Deprecated("", ReplaceWith("newFun(p)"))
fun oldFun(p: List<String>) {
    newFun(p)
}

fun newFun(p: List<String>){}

fun foo() {
    <caret>oldFun(listOf<String>("a"))
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.replaceWith.DeprecatedSymbolUsageFix
// FUS_K2_QUICKFIX_NAME: org.jetbrains.kotlin.idea.k2.codeinsight.fixes.replaceWith.DeprecatedSymbolUsageFix