package com.drdisagree.xmorphclock.xposed.hook

import io.github.libxposed.api.XposedInterface.Chain
import io.github.libxposed.api.XposedInterface.ExceptionMode
import io.github.libxposed.api.XposedInterface.HookHandle
import java.lang.reflect.Executable
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

private val MISSING_METHOD = Any()
private val zeroArgMethodCache = ConcurrentHashMap<Class<*>, ConcurrentHashMap<String, Any>>()

fun Class<*>.findZeroArgMethod(name: String): Method? {
    val byName = zeroArgMethodCache.computeIfAbsent(this) { ConcurrentHashMap() }
    byName[name]?.let { cached -> return if (cached === MISSING_METHOD) null else cached as Method }
    val resolved = findMethod(name) { it.parameterCount == 0 }
    byName.putIfAbsent(name, resolved ?: MISSING_METHOD)
    return resolved
}

fun Class<*>.findMethod(name: String, predicate: (Method) -> Boolean = { true }): Method? {
    var current: Class<*>? = this
    while (current != null) {
        val methods = current.declaredMethods
        for (method in methods) {
            if (method.name == name && predicate(method)) {
                method.isAccessible = true
                return method
            }
        }
        current = current.superclass
    }
    return null
}

fun Executable.intercept(hooker: (Chain) -> Any?): HookHandle? = try {
    HookEnv.module.hook(this)
        .setExceptionMode(ExceptionMode.PROTECTIVE)
        .intercept { chain -> hooker(chain) }
} catch (throwable: Throwable) {
    HookEnv.log("Hook failed: $this", throwable)
    null
}

fun Executable.hookAfter(block: (Chain, Any?) -> Unit): HookHandle? = intercept { chain ->
    val result = chain.proceed()
    block(chain, result)
    result
}
