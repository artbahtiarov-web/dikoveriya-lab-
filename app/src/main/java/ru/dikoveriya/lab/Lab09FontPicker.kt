package ru.dikoveriya.lab

import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp

/** Preview uses the Android Typeface from the thermal PNG renderer. */
@Composable
fun Lab09FontPicker(selected:String,onSelect:(String)->Unit,onDismiss:()->Unit){
    var query by remember{mutableStateOf("")}
    var group by remember{mutableStateOf("Все")}
    val fonts=remember(query,group){
        LabelFontCatalog.options.filter{(group=="Все"||it.category==group)
            && (query.isBlank()||it.title.contains(query,true)||it.id.contains(query,true))}
    }
    AlertDialog(
        onDismissRequest=onDismiss,
        title={Text("Выбрать шрифт")},
        text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)){
                OutlinedTextField(query,{query=it},label={Text("Найти шрифт")},
                    singleLine=true,modifier=Modifier.fillMaxWidth())
                PickOne("Категория",LabelFontCatalog.categories,group,{group=it})
                Text(fonts.size.toString()+" вариантов · «Алоказия, Ж, Ё»",
                    style=MaterialTheme.typography.bodySmall)
                LazyColumn(Modifier.heightIn(max=355.dp)){
                    items(fonts,key={it.id}){option->
                        Surface(onClick={onSelect(option.id);onDismiss()},
                            color=if(selected==option.id)
                                MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                            modifier=Modifier.fillMaxWidth()){
                            Row(Modifier.padding(vertical=9.dp,horizontal=8.dp)){
                                Column(Modifier.weight(1f)){
                                    Text(option.title,
                                        style=MaterialTheme.typography.labelMedium)
                                    AndroidView(
                                        factory={ctx->TextView(ctx).apply{
                                            text="Алоказия · Ж Щ Ё Ы я"
                                            textSize=18f
                                        }},
                                        update={view->
                                            view.typeface=LabelFontCatalog.typeface(option.id,false,false)
                                            view.setTextColor(android.graphics.Color.BLACK)
                                        },
                                        modifier=Modifier.fillMaxWidth().heightIn(min=28.dp)
                                    )
                                }
                                if(selected==option.id)Text("✓")
                            }
                        }
                        HorizontalDivider()
                    }
                }
                Text("Встроенные шрифты Android: наличие отдельных начертаний зависит от устройства. Предпросмотр и PNG используют одну систему.",
                    style=MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton={TextButton(onClick=onDismiss){Text("Готово")}}
    )
}
