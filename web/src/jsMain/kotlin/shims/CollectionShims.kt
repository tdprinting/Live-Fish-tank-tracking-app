@file:Suppress("unused")

package kotlin.collections

/** java.util.Map.merge */
fun <K, V> MutableMap<K, V>.merge(key: K, value: V, remap: (V, V) -> V?): V? {
    val old = this[key]
    val nv = if (old == null) value else remap(old, value)
    if (nv == null) remove(key) else this[key] = nv
    return nv
}
