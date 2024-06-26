package soLastStatementInInlineFunctionArgumentAsAnonymous

fun main(args: Array<String>) {
    bar(fun() {
        //Breakpoint!
        nop()
    })
}

inline fun bar(f: () -> Unit) {
    nop()
    f()
}

fun nop() {}

// STEP_OVER: 4
// REGISTRY: debugger.kotlin.step.through.inline.lambdas=false
