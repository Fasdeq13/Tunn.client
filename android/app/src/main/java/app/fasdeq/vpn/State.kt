package app.fasdeq.vpn

import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KProperty

class Cell<T>(@Volatile var value: T) {
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = value
    operator fun setValue(thisRef: Any?, property: KProperty<*>, v: T) {
        value = v
    }
}

fun <T> mutableStateOf(value: T): Cell<T> = Cell(value)
fun mutableIntStateOf(value: Int): Cell<Int> = Cell(value)
fun mutableLongStateOf(value: Long): Cell<Long> = Cell(value)
fun mutableFloatStateOf(value: Float): Cell<Float> = Cell(value)

class SafeList<T> : AbstractMutableList<T>(), RandomAccess {
    @Volatile private var snap: List<T> = emptyList()

    override val size: Int get() = snap.size

    override fun get(index: Int): T = snap[index]

    @Synchronized
    override fun set(index: Int, element: T): T {
        val n = ArrayList(snap)
        val old = n.set(index, element)
        snap = n
        return old
    }

    @Synchronized
    override fun add(index: Int, element: T) {
        val n = ArrayList(snap)
        n.add(index, element)
        snap = n
    }

    @Synchronized
    override fun addAll(elements: Collection<T>): Boolean {
        if (elements.isEmpty()) return false
        val n = ArrayList(snap)
        n.addAll(elements)
        snap = n
        return true
    }

    @Synchronized
    override fun removeAt(index: Int): T {
        val n = ArrayList(snap)
        val old = n.removeAt(index)
        snap = n
        return old
    }

    @Synchronized
    override fun clear() {
        snap = emptyList()
    }

    override fun iterator(): MutableIterator<T> {
        val copy = snap
        return object : MutableIterator<T> {
            var i = 0
            override fun hasNext() = i < copy.size
            override fun next(): T = copy[i++]
            override fun remove() {
                throw UnsupportedOperationException()
            }
        }
    }
}

fun <T> mutableStateListOf(): MutableList<T> = SafeList()
fun <K : Any, V : Any> mutableStateMapOf(): MutableMap<K, V> = ConcurrentHashMap()
