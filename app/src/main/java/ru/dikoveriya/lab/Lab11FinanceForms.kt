package ru.dikoveriya.lab

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun Lab11ExpenseDialog(db:LabDatabase,onClose:()->Unit,
    onSaved:()->Unit,onError:(String)->Unit){
    var date by rememberSaveable{mutableStateOf(dateText11())}
    var category by rememberSaveable{mutableStateOf("Электричество")}
    var amount by rememberSaveable{mutableStateOf("")}
    var note by rememberSaveable{mutableStateOf("")}
    val categories=remember{db.categories11("Материалы")}
    AlertDialog(onDismissRequest=onClose,title={Text("Общие расходы проекта")},
        text={Column(Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(12.dp)){
            Lab11DateField("Дата оплаты",date,{date=it})
            PickOne("Статья",listOf("Электричество","Вода","Обслуживание","Ремонт",
                "Аренда","Доставка","Комиссии","Прочее")+categories,
                category,{category=it})
            Field("Сумма, ₽",amount,{amount=it})
            Field("Назначение / примечание",note,{note=it},single=false)
            Text("Выплата учитывается на уровне проекта. Не включается в себестоимость отдельного растения.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }},
        confirmButton={Button(enabled=atDate11(date)!=null &&
                runCatching{rubles11(amount)}.isSuccess,
            onClick={
                try{
                    db.projectExpense11(category,rubles11(amount),
                        requireNotNull(atDate11(date)),note)
                    onSaved()
                }catch(e:Exception){onError(e.message?:"Не удалось записать расход")}
            }){Text("Записать")}},
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}})
}

@Composable
internal fun Lab11PurchaseDialog(db:LabDatabase,onClose:()->Unit,
    onSaved:()->Unit,onError:(String)->Unit){
    var name by rememberSaveable{mutableStateOf("")}
    var date by rememberSaveable{mutableStateOf(dateText11())}
    var category by rememberSaveable{mutableStateOf("Материалы")}
    var supplier by rememberSaveable{mutableStateOf("")}
    var url by rememberSaveable{mutableStateOf("")}
    var method by rememberSaveable{mutableStateOf("Карта")}
    var amount by rememberSaveable{mutableStateOf("")}
    var qty by rememberSaveable{mutableStateOf("")}
    var materialId by rememberSaveable{mutableStateOf<Long?>(null)}
    var note by rememberSaveable{mutableStateOf("")}
    val materials=remember{db.materials()}
    val selected=materials.firstOrNull{it.id==materialId}
    AlertDialog(onDismissRequest=onClose,title={Text("Зарегистрировать покупку")},
        text={Column(Modifier.heightIn(max=520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement=Arrangement.spacedBy(11.dp)){
            Field("Наименование товара",name,{name=it})
            Lab11DateField("Дата покупки",date,{date=it})
            PickOne("Категория",
                listOf("Материалы","Субстраты","Удобрения","Препараты",
                    "Расходники","Упаковка","Инвентарь","Прочее")+
                    db.categories11("Материалы"),
                category,{category=it})
            PickOne("Увеличить остаток на складе",
                listOf("Не пополнять склад")+materials.map{it.name},
                selected?.name?:"Не пополнять склад",
                {text->materialId=materials.firstOrNull{it.name==text}?.id})
            if(selected!=null){
                Field("Количество, "+selected.unit,qty,{qty=it})
                Text("Текущий остаток: "+selected.quantity+" "+selected.unit,
                    style=MaterialTheme.typography.bodySmall)
            }
            Field("Стоимость покупки, ₽",amount,{amount=it})
            Field("Поставщик",supplier,{supplier=it})
            Field("Ссылка на товар (необязательно)",url,{url=it})
            PickOne("Оплата",listOf("Карта","Наличные","Перевод","Другое"),
                method,{method=it})
            Field("Примечание",note,{note=it},single=false)
            Text("Деньги спишутся один раз. Если выбрана складская позиция, её средневзвешенная оценка изменится пропорционально закупке.",
                style=MaterialTheme.typography.bodySmall)
        }},
        confirmButton={
            Button(enabled=name.trim().length>=2 && atDate11(date)!=null &&
                runCatching{rubles11(amount)}.isSuccess,
                onClick={
                    try{
                        val quantity=if(selected!=null)
                            qty.trim().replace(',','.').toDoubleOrNull()
                                ?:error("Укажи количество закупленного материала")
                        else 0.0
                        db.purchase11(name,category,rubles11(amount),
                            requireNotNull(atDate11(date)),supplier,url,method,note,
                            materialId,quantity,selected?.unit?:"")
                        onSaved()
                    }catch(e:Exception){onError(e.message?:"Не удалось зарегистрировать покупку")}
                }){Text("Сохранить")}
        },
        dismissButton={TextButton(onClick=onClose){Text("Отмена")}}
    )
}
