package com.drdisagree.xmorphclock.xposed.hook

import java.lang.reflect.Field
import java.util.concurrent.ConcurrentHashMap

private val MISSING_FIELD = Any()
private val fieldCache = ConcurrentHashMap<Class<*>, ConcurrentHashMap<String, Any>>()

fun Class<*>.findField(name: String): Field? {
    val byName = fieldCache.computeIfAbsent(this) { ConcurrentHashMap() }
    byName[name]?.let { cached -> return if (cached === MISSING_FIELD) null else cached as Field }

    var resolved: Field? = null
    var current: Class<*>? = this
    while (current != null) {
        try {
            resolved = current.getDeclaredField(name).apply { isAccessible = true }
            break
        } catch (_: NoSuchFieldException) {
            current = current.superclass
        }
    }
    byName.putIfAbsent(name, resolved ?: MISSING_FIELD)
    return resolved
}

fun Any.fieldValue(name: String): Any? = try {
    javaClass.findField(name)?.get(this)
} catch (_: Throwable) {
    null
}

fun Any.callMethod(name: String): Any? = try {
    javaClass.findZeroArgMethod(name)?.invoke(this)
} catch (_: Throwable) {
    null
}

fun staticFieldValue(className: String, name: String): Any? = try {
    HookEnv.findClass(className)?.findField(name)?.get(null)
} catch (_: Throwable) {
    null
}

fun Any.setFieldValue(name: String, value: Any?) {
    try {
        javaClass.findField(name)?.set(this, value)
    } catch (_: Throwable) {
    }
}
