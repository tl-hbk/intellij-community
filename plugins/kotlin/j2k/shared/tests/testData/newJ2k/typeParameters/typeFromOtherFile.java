// IGNORE_K2
// !ADD_JAVA_API
import java.util.*;
import javaApi.T;

class A {
    public Object foo(T t) {
        return Collections.nCopies(1, t.set);
    }
}