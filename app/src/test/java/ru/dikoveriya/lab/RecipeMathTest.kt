package ru.dikoveriya.lab

import org.junit.Assert.*
import org.junit.Test

class RecipeMathTest {
    @Test fun gramScale() {
        val p=RecipePart(1,1,"Основа",4.4,"г/л",true,true,0.0)
        val res=RecipeCalculator.calculate(listOf(p),500)
        assertEquals(2.2,res[0].amount,1e-12)
        assertEquals("г",res[0].unit)
    }
    @Test fun stockSolution() {
        val p=RecipePart(1,1,"Запас",1.0,"мг/л из раствора",false,false,2.0)
        assertEquals(0.25,RecipeCalculator.calculate(listOf(p),500)[0].amount,1e-12)
    }
    @Test(expected=IllegalArgumentException::class) fun doubleAgarBlocked() {
        val p=RecipePart(1,1,"Агар",7.0,"г/л",false,true,0.0)
        RecipeCalculator.calculate(listOf(p,p.copy(id=2)),1000)
    }
    @Test fun csvQuotedAndComma() {
        val row=RecipeCsv.formatRow(listOf("","Пример; среда","Размножение","","Агар","7,5","г/л","0","1","0","1","Черновик"))
        val parsed=RecipeCsv.parse(RecipeCsv.HEADER+"\n"+row+"\n")
        assertEquals("Пример; среда",parsed[0].name)
        assertEquals(7.5,parsed[0].amount,1e-12)
    }
}
