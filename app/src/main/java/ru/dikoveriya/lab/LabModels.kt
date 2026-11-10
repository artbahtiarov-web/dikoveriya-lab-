package ru.dikoveriya.lab

import java.math.BigDecimal

object LabTerms {
    const val IN_CULTURE = "В культуре"
    const val CONTROL = "На контроле"
    const val QUARANTINE = "Карантин"
    const val TRANSFERRED = "Пересажен"
    const val DISPOSED = "Утилизирован"
    const val SOLD = "Продан"
    const val IN_VITRO = "В пробирке"
    const val ADAPTATION = "Адаптация"
    const val GROWING = "Доращивание"
    const val MOTHER = "Маточник"
    const val READY = "Готово к продаже"
    val statuses = listOf(IN_CULTURE, CONTROL, QUARANTINE, DISPOSED)
    val stages = listOf(IN_VITRO, ADAPTATION, GROWING, MOTHER, READY)
    val purposes = listOf("Введение", "Размножение", "Укоренение", "Другое")
}

data class PlantRecord(val id: Long, val name: String, val description: String = "")
data class MotherRecord(val id: Long, val code: String, val plantId: Long, val plantName: String, val origin: String, val status: String)
data class CultureRecord(
    val id: Long, val code: String, val plantId: Long, val plantName: String,
    val quantity: Int, val status: String, val passage: Int, val stage: String,
    val location: String, val batchId: Long?, val motherId: Long?, val createdAt: Long,
    val notes: String
)
data class CulturePhoto(val id: Long, val cultureId: Long, val filename: String, val caption: String, val addedAt: Long)
data class LabEvent(val id: Long, val cultureId: Long, val kind: String, val note: String, val time: Long, val before: Int?, val after: Int?)
data class RecipeRecord(val id: Long, val family: String, val version: Int, val name: String, val purpose: String, val plantId: Long?, val status: String, val notes: String)
data class RecipePart(val id: Long, val recipeId: Long, val name: String, val perLiter: Double, val unit: String, val includesSugar: Boolean, val includesAgar: Boolean, val stockMgPerMl: Double)
data class BatchRecord(val id: Long, val code: String, val recipeId: Long, val recipeName: String, val version: Int, val volumeMl: Int, val preparedAt: Long, val status: String, val note: String)
data class TaskRecord(val id: Long, val cultureId: Long?, val title: String, val dueAt: Long, val done: Boolean)
data class MaterialRecord(val id: Long, val name: String, val unit: String, val quantity: Double, val minQuantity: Double, val costPerUnit: Double)
data class EquipmentRecord(val id: Long, val name: String, val location: String, val status: String, val nextCheck: Long?)
data class IncidentRecord(val id: Long, val title: String, val note: String, val time: Long, val status: String)
data class ExperimentRecord(val id: Long, val title: String, val hypothesis: String, val startedAt: Long, val status: String)
data class SaleRecord(val id: Long, val cultureCode: String, val quantity: Int, val price: Double, val time: Long)
data class RecipeResult(val title: String, val amount: Double, val unit: String, val warning: String = "")

/** No laboratory values are hardcoded: calculations use only user-confirmed recipe parts. */
object RecipeCalculator {
    fun calculate(parts: List<RecipePart>, volumeMl: Int, scaleResolutionGram: Double = 0.1): List<RecipeResult> {
        require(volumeMl in 1..100000) { "Укажи объём от 1 до 100 000 мл" }
        require(scaleResolutionGram > 0) { "Точность весов должна быть положительной" }
        require(parts.isNotEmpty()) { "Добавь компоненты рецептуры" }
        if (parts.count { it.includesSugar } > 1 || parts.count { it.includesAgar } > 1) {
            throw IllegalArgumentException("Отмечено повторное включение сахара или агара — проверь состав")
        }
        return parts.map { part ->
            require(part.name.isNotBlank() && part.perLiter > 0 && part.perLiter.isFinite()) { "Неверный компонент: ${part.name}" }
            val target = part.perLiter * volumeMl / 1000.0
            when (part.unit) {
                "г/л" -> RecipeResult(part.name, target, "г", if (target < scaleResolutionGram) "Масса меньше шага весов — используй проверенный маточный раствор" else "")
                "мг/л" -> RecipeResult(part.name, target, "мг", if (target < scaleResolutionGram * 1000) "Масса меньше шага весов — используй проверенный маточный раствор" else "")
                "мл/л" -> RecipeResult(part.name, target, "мл")
                "мг/л из раствора" -> {
                    require(part.stockMgPerMl > 0 && part.stockMgPerMl.isFinite()) { "Укажи концентрацию маточного раствора: ${part.name}" }
                    RecipeResult(part.name, target / part.stockMgPerMl, "мл", "Концентрация маточного раствора: ${formatAmount(part.stockMgPerMl)} мг/мл")
                }
                else -> throw IllegalArgumentException("Недопустимая единица ${part.unit}")
            }
        }
    }
    fun formatAmount(x: Double): String = if(x.isFinite()) BigDecimal.valueOf(x).stripTrailingZeros().toPlainString() else "—"
}
