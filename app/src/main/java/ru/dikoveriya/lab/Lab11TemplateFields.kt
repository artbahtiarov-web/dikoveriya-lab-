package ru.dikoveriya.lab

import org.json.JSONArray
import org.json.JSONObject

/** Versioned custom operation fields; old string-only templates remain readable. */
internal data class Lab11TemplateField(
    val name:String,val kind:String="text",val required:Boolean=false,
    val unit:String="",val choices:List<String> = emptyList()
)

internal fun parseTemplateFields11(json:String):List<Lab11TemplateField>{
    val array=JSONArray(json)
    require(array.length()<=30){"Не более 30 полей в операции"}
    val valid=setOf("text","number","date","boolean","choice","unit","photo","url")
    val fields=(0 until array.length()).map{index->
        when(val x=array.get(index)){
            is String->Lab11TemplateField(x)
            is JSONObject->{
                val options=x.optJSONArray("choices")?:JSONArray()
                Lab11TemplateField(
                    x.optString("name"),x.optString("kind","text"),
                    x.optBoolean("required",false),x.optString("unit"),
                    (0 until options.length()).map{options.getString(it)}
                )
            }
            else->error("Неподдерживаемое поле шаблона")
        }
    }
    require(fields.all{it.name.trim().length in 2..100 && it.kind in valid &&
        it.unit.length<=30 && it.choices.size<=100 &&
        it.choices.all{choice->choice.length in 1..100}}){"Некорректные поля шаблона"}
    require(fields.map{it.name.lowercase()}.distinct().size==fields.size){
        "В шаблоне повторяются названия полей"
    }
    require(fields.all{it.kind!="choice" || it.choices.isNotEmpty()}){
        "Для поля-списка укажи значения"
    }
    return fields
}

internal fun templateFieldsJson11(fields:List<Lab11TemplateField>):String{
    require(fields.size<=30)
    val json=JSONArray()
    fields.forEach{f->
        json.put(JSONObject().put("name",f.name).put("kind",f.kind)
            .put("required",f.required).put("unit",f.unit)
            .put("choices",JSONArray(f.choices)))
    }
    val result=json.toString()
    parseTemplateFields11(result)
    return result
}

/** Metadata never creates inventory movements; actual stock use is verified separately. */
internal fun validateTemplateAnswers11(
    fields:List<Lab11TemplateField>,answers:Map<String,String>
){
    require(answers.keys.all{key->fields.any{it.name==key}}){
        "Неизвестное пользовательское поле"
    }
    fields.forEach{f->
        val v=answers[f.name]?.trim().orEmpty()
        require(!f.required || v.isNotBlank()){"Заполни поле «"+f.name+"»"}
        if(v.isNotBlank())when(f.kind){
            "number","unit"->require(v.replace(',','.').toDoubleOrNull()?.isFinite()==true){
                "Поле «"+f.name+"» должно содержать число"
            }
            "date"->require(validDate11(v)!=null){"Неверная дата в «"+f.name+"»"}
            "boolean"->require(v in listOf("true","false")){"Поле «"+f.name+"»: да или нет"}
            "choice"->require(v in f.choices){"Недопустимое значение «"+f.name+"»"}
            "url"->require(v.startsWith("https://")||v.startsWith("http://")){
                "Укажи адрес ссылки в «"+f.name+"»"
            }
            "photo"->require(Regex("[a-zA-Z0-9_-]+\\.(jpg|png|webp|heic)").matches(v)){
                "Сначала добавь фотографию"
            }
        }
    }
}
