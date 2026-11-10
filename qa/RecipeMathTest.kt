import ru.dikoveriya.lab.RecipeCalculator
import ru.dikoveriya.lab.RecipePart
import kotlin.math.abs

fun near(a:Double,b:Double)=check(abs(a-b)<1e-8){"Expected $b, got $a"}
fun main() {
    val parts=listOf(
        RecipePart(1,1,"Смесь",4.4,"г/л",true,true,0.0),
        RecipePart(2,1,"Раствор регулятора",1.0,"мг/л из раствора",false,false,1.0)
    )
    val res=RecipeCalculator.calculate(parts,500)
    near(res[0].amount,2.2)
    near(res[1].amount,0.5)
    check(res[0].unit=="г" && res[1].unit=="мл")
    check(runCatching{RecipeCalculator.calculate(parts,0)}.isFailure)
    check(runCatching{RecipeCalculator.calculate(parts,100001)}.isFailure)
    check(runCatching{RecipeCalculator.calculate(parts+parts.first().copy(id=3,name="Дубль сахара"),500)}.isFailure)
    val tiny=RecipeCalculator.calculate(listOf(RecipePart(1,1,"Микро",0.1,"мг/л",false,false,0.0)),500)
    check(tiny.first().warning.contains("весов"))
    println("PASS: 6 laboratory formula checks")
}
