import kotlinx.coroutines.*
import java.time.Clock
import java.time.ZoneId
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

class TimedCache<K,V>(
    private val clock: Clock = Clock.system(ZoneId.systemDefault()),
    private val coroutineScope: CoroutineScope,
    private val cancelScopeOnDestroy: Boolean = false
) {
    constructor(
        clock: Clock = Clock.system(ZoneId.systemDefault()),
        dispatcher: CoroutineDispatcher = Dispatchers.IO
    ): this(clock, CoroutineScope( SupervisorJob() + dispatcher), true)

    data class Entry<V> (
        val value: V,
        val lifetime: Long,
        val expires: Long,
        val job: Job
    )

    private val map = mutableMapOf<K, Entry<V>>()

    fun add(key: K, value: V, lifetime: Long): Boolean {
        if (lifetime > 0) {
            map[key]?.job?.cancel(message = "Replaced")

            map[key] = Entry(
                value = value,
                lifetime = lifetime,
                expires = clock.millis() + lifetime,
                job = coroutineScope.launch {
                    delay(lifetime)
                    map.remove(key)
                    if (DEBUG) {
                        println("Expired: $key")
                    }
                }
            )

            return true
        }
        return false
    }

    fun get(key: K): V? {
        map[key]?.let {
            if (it.expires > clock.millis()) return it.value
        }
        return null
    }

    fun remove(key: K): V? {
        map.remove(key)?.let {
            it.job.cancel(message = "Removed")
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
    }
}

fun main() = runBlocking {
    //testExpire()
//    testRemove()
    testBulk()
//    testGet()
}

suspend fun testExpire() {
    val cache = TimedCache<Int, String>()

    cache.add(1, "Red", 3_000)
    cache.add(2, "Blue", 2_000)
    cache.add(3, "Green", 1_000)

    cache.print()
    delay(1_200)
    cache.print()
    delay(1_200)
    cache.print()
    delay(1_200)
    cache.print()

    cache.destroy()
}

suspend fun testRemove() {
    val cache = TimedCache<Int, String>()
//    val cache = TimedCache<Int, String>(coroutineScope = CoroutineScope(coroutineContext))

    cache.add(1, "Red", 5_000)
    cache.add(2, "Blue", 5_000)
    cache.add(3, "Green", 5_000)

    cache.print()
    cache.remove(2)
    cache.print()
    delay(6_000)
    cache.print()

    cache.destroy()
}

suspend fun testBulk() {
    val cache = TimedCache<Int, String>()
//    val cache = TimedCache<Int, String>(coroutineScope = CoroutineScope(coroutineContext))

    val lifetimes = listOf<Long>(0, 1, 100, 1000, 2000, 5000)
    for (i in 1 .. 5000) {
        cache.add(
            key = i,
            value = Random.nextInt().toString(),
            lifetime = lifetimes[Random.nextInt(lifetimes.size)]
        )
    }
    delay(6000)
    cache.print()

    cache.destroy()
}

suspend fun testGet() {
    val cache = TimedCache<Int, String>()
    cache.add(1, "Red", 3_000)
    cache.add(2, "Blue", 2_000)

    delay(1_200)
    println("${cache.get(1)}, ${cache.get(2)}")
    delay(1_200)
    println("${cache.get(1)}, ${cache.get(2)}")
    delay(1_200)
    println("${cache.get(1)}, ${cache.get(2)}")

    cache.destroy()
}