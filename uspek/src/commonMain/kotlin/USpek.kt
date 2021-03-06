package pl.mareklangiewicz.uspek

import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

fun uspek(code: () -> Unit) = GlobalUSpekContext.uspek(code)

suspend fun suspek(code: suspend () -> Unit) = coroutineContext.ucontext.uspek { code() }

private inline fun USpekContext.uspek(code: () -> Unit) {
    while (true) try {
        branch = root
        code()
        break
    } catch (e: USpekException) {
        branch.end = e
        uspekLog(branch)
    }
}

infix fun String.o(code: () -> Unit) = GlobalUSpekContext.o(this, code)

suspend infix fun String.so(code: suspend () -> Unit): Unit = coroutineContext.ucontext.o(this) { code() }

private inline fun USpekContext.o(name: String, code: () -> Unit) {
    val subbranch = branch.branches.getOrPut(name) { USpekTree(name) }
    subbranch.end == null || return // already tested so skip this whole subbranch
    branch = subbranch // step through the tree into the subbranch
    uspekLog(subbranch)
    throw try { code(); USpekException() }
    catch (e: USpekException) { e }
    catch (e: Throwable) { USpekException(e) }
}

@Deprecated("Enable this test code", ReplaceWith("o(code)"))
infix fun String.ox(code: () -> Unit) = Unit

@Deprecated("Enable this test code", ReplaceWith("so(code)"))
infix fun String.sox(code: suspend () -> Unit) = Unit

data class USpekContext(
    val root: USpekTree = USpekTree("uspek"),
    var branch: USpekTree = root
) : CoroutineContext.Element {
    override val key: CoroutineContext.Key<USpekContext> = Key
    companion object Key : CoroutineContext.Key<USpekContext>
}

val GlobalUSpekContext = USpekContext()

val CoroutineContext.ucontext get() = this[USpekContext] ?: GlobalUSpekContext

data class USpekTree(
    val name: String,
    val branches: MutableMap<String, USpekTree> = mutableMapOf(),
    var end: USpekException? = null,
    var data: Any? = null
)

class USpekException(cause: Throwable? = null) : RuntimeException(cause)

var uspekLog: (USpekTree) -> Unit = { println(it.status) }

val USpekTree.status get() = when {
    failed -> "FAILURE.($location)\nBECAUSE.($causeLocation)\n"
    finished -> "SUCCESS.($location)\n"
    else -> name
}

val USpekTree.finished get() = end !== null

val USpekTree.failed get() = end?.cause !== null

expect val USpekTree?.location: CodeLocation?

expect val Throwable.causeLocation: CodeLocation?

val USpekTree?.causeLocation get() = this?.end?.causeLocation

infix fun <T> T.eq(expected: T) = check(this == expected) { "$this != $expected" }


data class CodeLocation(val fileName: String, val lineNumber: Int) {
    override fun toString() = "$fileName:$lineNumber"
}
