// FIR_COMPARISON

fun test(i: Int?, foo: Int.(String) -> Char) {
    i?.fo<caret>
}

// EXIST: { lookupString: "foo", itemText: "foo", tailText: "(String) for Int", typeText: "Char", attributes: "bold", icon: "Parameter"}