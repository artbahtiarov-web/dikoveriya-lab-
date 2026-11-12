package ru.dikoveriya.lab

/**
 * Deterministic route stack. Each Android Back removes precisely one destination;
 * opening a destination never discards its ancestors. Empty stack = current root tab.
 *
 * The UI stores this list with rememberSaveable. Screen content lives in a
 * SaveableStateHolder keyed by destination, retaining forms and LazyListState.
 */
object Lab07Navigation {
    const val COLLECTION = "collection"

    fun variety(plantId: Long): String = "variety:$plantId"
    fun container(cultureId: Long): String = "container:$cultureId"
    fun growth(cultureId: Long): String = "growth:$cultureId"
    fun medium(cultureId: Long): String = "medium:$cultureId"
    fun operations(cultureId: Long): String = "operations:$cultureId"
    fun history(cultureId: Long): String = "history:$cultureId"

    fun enter(stack: List<String>, next: String): List<String> {
        require(isKnown(next)) { "Unknown screen: $next" }
        if (stack.lastOrNull() == next) return stack
        return stack + next
    }

    fun back(stack: List<String>): List<String> = if(stack.isEmpty()) emptyList() else stack.dropLast(1)
    fun current(stack: List<String>): String? = stack.lastOrNull()

    fun isKnown(route: String): Boolean {
        if(route == COLLECTION) return true
        val group=route.substringBefore(':')
        val id=route.substringAfter(':',"").toLongOrNull()
        return group in setOf("variety","container","growth","medium","operations","history") &&
            id != null && id > 0
    }

    fun parentFor(route: String, plantId: Long?): String? = when(route.substringBefore(':')) {
        "variety" -> COLLECTION
        "container" -> plantId?.let { variety(it) }
        "growth","medium","operations","history" -> route.substringAfter(':').toLongOrNull()?.let { container(it) }
        else -> null
    }
}
