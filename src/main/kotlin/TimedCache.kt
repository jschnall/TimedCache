import kotlinx.coroutines.*
import java.time.Clock
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

class Entry<V> (
    val value: V,
    val expires: Long,
    val job: Job
)

class TimedCache<K,V>(
    val clock: Clock = Clock.system(ZoneId.systemDefault()),
    val context: CoroutineContext
) {
    private val map = mutableMapOf<K, Entry<V>>()

    fun add(key: K, value: V, lifetime: Long) {
        if (lifetime > 0) {
            map[key]?.job?.cancel(message = "Replaced")

            val expires = clock.millis() + lifetime
            val job = CoroutineScope(context).launch {
                delay(lifetime)
                map.remove(key)
                if (DEBUG) {
                    println("Expired: $key")
                }
            }
            map[key] = Entry(value, expires, job)
        }
    }

    fun get(key: K): V? {
        return map[key]?.value
    }

    fun remove(key: K): V? {
        map[key]?.job?.cancel(message = "Removed")
        return map.remove(key)?.value
    }

    fun print() {
        print("${clock.millis()}: (")
        for (entry in map.values) {
            print("${entry.value},")
        }
        println(")")
    }

    companion object {
        const val DEBUG = true
    }
}

fun main() = runBlocking {
    // test1()
    test2()
}

suspend fun test1() {
    val cache = TimedCache<Int, String>(context = coroutineContext)
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
}

suspend fun test2() {
    val cache = TimedCache<Int, String>(context = coroutineContext)
    val lifetimes = listOf<Long>(0, 1, 100, 1000, 2000, 5000)
    for (i in 1 .. 1000) {
        cache.add(
            key = i,
            value = Random.nextInt().toString(),
            lifetime = lifetimes[Random.nextInt(lifetimes.size)]
        )
    }
    delay(6000)
    cache.print()
}
