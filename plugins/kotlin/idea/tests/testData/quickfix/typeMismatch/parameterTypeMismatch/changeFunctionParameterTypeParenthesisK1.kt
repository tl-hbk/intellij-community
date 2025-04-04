// "Change parameter 'x' type of function 'foo' to 'Any?'" "true"
// DISABLE_ERRORS
// LANGUAGE_VERSION: 1.8

fun <T> foo(x: T & Any) {}

fun <T> bar(x: T) {
    foo((<caret>x))
}

// FUS_QUICKFIX_NAME: org.jetbrains.kotlin.idea.quickfix.ChangeParameterTypeFix
// IGNORE_K2
// For K2-specific behaviour, see changeFunctionParameterTypeParenthesisK2.kt