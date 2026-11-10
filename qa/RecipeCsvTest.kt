import ru.dikoveriya.lab.RecipeCsv
fun main() {
    val input=RecipeCsv.HEADER+"\n"+RecipeCsv.formatRow(listOf("","MS; рецепт","Размножение","Alocasia Black Velvet Aurea","Агар; формула","7,5","г/л","0","1","0","1","Черновик"))+"\n"
    val rows=RecipeCsv.parse(input)
    check(rows.size==1)
    check(rows[0].name=="MS; рецепт")
    check(rows[0].ingredient=="Агар; формула")
    check(rows[0].amount==7.5)
    check(rows[0].agar)
    check(runCatching{RecipeCsv.parse("bad;header\nx;y")}.isFailure)
    println("PASS: CSV quoted fields, decimal comma, header validation")
}
