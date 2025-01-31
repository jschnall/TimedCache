import kotlinx.coroutines.*
import java.time.Clock
import java.time.ZoneId
import java.util.WeakHashMap
import kotlin.random.Random

class TimedBlockCache<K,V>(
    private val maxEntryLife: Long,
    private val clock: Clock = Clock.system(ZoneId.systemDefault()),
    private val coroutineScope: CoroutineScope,
    private val cancelScopeOnDestroy: Boolean = false
) {
    constructor(
        maxEntryLife: Long,
        clock: Clock = Clock.system(ZoneId.systemDefault()),
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): this(maxEntryLife, clock, CoroutineScope( SupervisorJob() + dispatcher), true)

    data class Block<V>(
        val expires: Long
    ) {
        val entries: MutableList<Entry<V>> = mutableListOf()

        fun add(entry: Entry<V>) {
            entries.add(entry)
        }
    }

    data class Entry<V> (
        val value: V,
        val expires: Long
    )

    private val map = WeakHashMap<K, Entry<V>>()
    private val blocks = mutableSetOf<Block<V>>()
    var lastBlock: Block<V>? = null

    fun add(key: K, value: V, lifetime: Long): Boolean {
        if (lifetime in 1..< maxEntryLife) {
            val expires = clock.millis() + lifetime

            if (lastBlock == null || lastBlock!!.expires < expires) {
                lastBlock = addBlock()
            }

            lastBlock?.let {
                val entry = Entry(value, expires)
                it.add(entry)
                map[key] = entry
                if (DEBUG) {
                    println("Added $entry to $lastBlock")
                }
                return true
            }
        }
        return false
    }

    private fun addBlock(): Block<V> {
        val lifetime = BLOCK_WIDTH * maxEntryLife
        val block = Block<V>(expires = clock.millis() + lifetime)

        coroutineScope.launch {
            delay(lifetime)
            blocks.remove(block)
            if (DEBUG) {
                println("Expired: $block, ${blocks.size} blocks remain")
            }
        }

        blocks.add(block)
        if (DEBUG) {
            println("Created $block")
        }

        return block
    }

    fun get(key: K): V? {
        map[key]?.let {
            if (it.expires > clock.millis()) return it.value
        }
        return null
    }

    fun remove(key: K): V? {
        map.remove(key)?.let {
            if (it.expires > clock.millis()) return it.value
        }
        return null
    }

    fun destroy() {
        if (cancelScopeOnDestroy) {
            coroutineScope.cancel()
        }
    }

    fun print() {
        println("${clock.millis()}: (${map.entries})")
    }


    companion object {
        const val DEBUG = true
        const val BLOCK_WIDTH = 2
    }
}

fun main() = runBlocking {
//    blockTest()
//    blockTest2()
    blockTest3()
}

suspend fun blockTest() {
    val cache = TimedBlockCache<Int, String>(5_000)

    cache.add(1, "Red", 3_000)
    cache.add(2, "Blue", 2_000)
    cache.add(3, "Green", 1_000)
    cache.print()
    delay(5_000)
    cache.print()
    delay(10_000)
    cache.print()

    cache.destroy()
}

suspend fun blockTest2() {
    val cache = TimedBlockCache<Int, String>(5_000)

    cache.add(1, "Red", 3_000)
    cache.add(2, "Blue", 2_000)
    cache.add(3, "Green", 1_000)
    println("${cache.get(1)}, ${cache.get(2)}")
    delay(5_000)
    println("${cache.get(1)}, ${cache.get(2)}")
    delay(10_000)
    println("${cache.get(1)}, ${cache.get(2)}")

    cache.destroy()
}

suspend fun blockTest3() {
    val cache = TimedBlockCache<Int, String>(5_000)

    val lifetimes = listOf<Long>(0, 1, 100, 1000, 2000, 5000)
    for (i in 1 .. 1000) {
        cache.add(
            key = i,
            value = Random.nextInt().toString(),
            lifetime = lifetimes[Random.nextInt(lifetimes.size)]
        )
    }
    delay(8_000)
    for (i in 900 .. 2000) {
        cache.add(
            key = i,
            value = Random.nextInt().toString(),
            lifetime = lifetimes[Random.nextInt(lifetimes.size)]
        )
    }
    cache.print()
    delay(12_000)
    cache.print()
    System.gc()
    delay(1000)
    cache.print()

    cache.destroy()
}
