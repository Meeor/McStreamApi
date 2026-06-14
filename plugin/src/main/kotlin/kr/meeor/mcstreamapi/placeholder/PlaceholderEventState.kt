package kr.meeor.mcstreamapi.placeholder

class PlaceholderEventState {
    private val randomCache = mutableMapOf<String, RandomSelection>()
    private val customItemAmountCache = mutableMapOf<String, Int>()

    fun cachedRandom(key: String, resolver: () -> String?): String? {
        return cachedRandomSelection(key) {
            resolver()?.let { RandomEntry(value = it) }
        }?.value
    }

    fun cachedRandomSelection(key: String, resolver: () -> RandomEntry?): RandomSelection? {
        return randomCache.getOrPut(key) {
            (resolver() ?: return null).toSelection()
        }
    }

    fun cachedCustomItemAmount(key: String, resolver: () -> Int?): Int? {
        return customItemAmountCache.getOrPut(key) {
            resolver() ?: return null
        }
    }
}

data class RandomSelection(
    val entry: RandomEntry,
    val resolvedAmount: Int?,
) {
    val value: String = entry.value
    val display: String? = entry.display
}

fun RandomEntry.toSelection(): RandomSelection {
    return RandomSelection(
        entry = this,
        resolvedAmount = amount?.resolve(),
    )
}
