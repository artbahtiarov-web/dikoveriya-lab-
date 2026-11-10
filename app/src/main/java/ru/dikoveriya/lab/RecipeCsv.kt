package ru.dikoveriya.lab

/** Simple quoted UTF-8 CSV, separator ';' for Russian Excel. Import always creates a draft version. */
data class RecipeCsvLine(
    val family:String,val name:String,val purpose:String,val plant:String,
    val ingredient:String,val amount:Double,val unit:String,
    val sugar:Boolean,val agar:Boolean,val stock:Double
)
object RecipeCsv {
    const val HEADER="family;name;purpose;plant;ingredient;dose_per_l;unit;contains_sugar;contains_agar;stock_mg_ml;version;status"
    private fun splitCsv(content:String):List<List<String>> {
        val rows=mutableListOf<List<String>>()
        val cells=mutableListOf<String>()
        val cur=StringBuilder()
        var quote=false
        var i=0
        fun cell(){cells.add(cur.toString().trim());cur.setLength(0)}
        fun row(){cell();if(cells.any{it.isNotEmpty()})rows.add(cells.toList());cells.clear()}
        while(i<content.length){
            val c=content[i]
            when{
                c=='"' && quote && i+1<content.length && content[i+1]=='"'->{cur.append('"');i++}
                c=='"' ->quote=!quote
                !quote && c==';' ->cell()
                !quote && c=='\n' ->row()
                !quote && c=='\r' ->{}
                else->cur.append(c)
            }
            i++
        }
        require(!quote){"Незакрытая кавычка в CSV"}
        if(cur.isNotEmpty()||cells.isNotEmpty())row()
        return rows
    }
    private fun b(text:String)=text.trim().lowercase() in setOf("1","true","да","yes","y")
    private fun number(s:String)=s.trim().replace(',','.').toDoubleOrNull()?:error("Некорректное число: $s")
    fun parse(csv:String):List<RecipeCsvLine> {
        val clean=csv.removePrefix("\uFEFF")
        require(clean.toByteArray(Charsets.UTF_8).size<=2_000_000){"Файл CSV слишком большой"}
        val data=splitCsv(clean)
        require(data.isNotEmpty()){"Пустой CSV"}
        val header=data.first().map{it.lowercase()}
        val expected=HEADER.split(';')
        require(header.take(expected.size)==expected){"Заголовки CSV должны соответствовать шаблону приложения"}
        require(data.size in 2..2001){"Ожидается от 1 до 2000 строк компонентов"}
        return data.drop(1).mapIndexed { index,row->
            require(row.size>=expected.size){"Строка ${index+2}: отсутствуют поля"}
            val v=RecipeCsvLine(row[0],row[1],row[2],row[3],row[4],number(row[5]),row[6],b(row[7]),b(row[8]),if(row[9].isBlank())0.0 else number(row[9]))
            require(v.name.isNotBlank() && v.name.length<=120 && v.ingredient.isNotBlank() && v.ingredient.length<=120){"Проверь название в строке ${index+2}"}
            require(v.purpose in LabTerms.purposes){"Неизвестное назначение в строке ${index+2}"}
            require(v.amount.isFinite() && v.amount>0 && v.stock.isFinite() && v.stock>=0){"Некорректная дозировка в строке ${index+2}"}
            require(v.unit in listOf("г/л","мг/л","мл/л","мг/л из раствора")){"Неизвестная единица в строке ${index+2}"}
            require(v.unit!="мг/л из раствора" || v.stock>0){"Не указана концентрация маточного раствора в строке ${index+2}"}
            v
        }
    }
    fun esc(text:String):String=if(text.any{it==';'||it=='"'||it=='\n'})"\"${text.replace("\"","\"\"")}\"" else text
    fun formatRow(row:List<String>):String=row.joinToString(";"){esc(it)}
}
