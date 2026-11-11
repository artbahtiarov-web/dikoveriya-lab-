package ru.dikoveriya.lab

/** Deterministic count validations used by both the database and local unit tests. */
object LabOperationRules {
    fun remainingAfterSplit(available: Int, inputs: List<Int>, outputs: List<Int>, losses: Int): Int {
        require(available in 1..1000000) { "Недопустимое исходное количество" }
        require(inputs.isNotEmpty() && inputs.size==outputs.size && inputs.size<=25) { "Укажи от 1 до 25 новых контейнеров" }
        require(inputs.all{it in 1..1000000} && outputs.all{it in 1..1000000}) { "Для каждого контейнера укажи положительное количество" }
        require(losses>=0) { "Потери не могут быть отрицательными" }
        val spent=inputs.sumOf{it.toLong()}+losses.toLong()
        require(spent<=available.toLong()) { "Нельзя использовать $spent объектов, в исходном контейнере только $available" }
        return available-spent.toInt()
    }
    fun remainingAfterMerge(available: List<Int>, used: List<Int>, produced: Int): List<Int> {
        require(available.size in 2..25 && available.size==used.size) { "Для объединения нужно 2–25 источников" }
        require(produced in 1..1000000) { "Проверь количество в новом контейнере" }
        require(used.all{it>0} && available.all{it in 1..1000000}) { "Количество должно быть положительным" }
        return available.indices.map{i->
            require(used[i]<=available[i]) { "В источнике ${i+1} недостаточно материала" }
            available[i]-used[i]
        }
    }
    fun remainingAfterLoss(available: Int, lost: Int): Int {
        require(available in 1..1000000 && lost in 1..available) { "Потери должны быть больше нуля и не превышать имеющееся количество" }
        return available-lost
    }
}
