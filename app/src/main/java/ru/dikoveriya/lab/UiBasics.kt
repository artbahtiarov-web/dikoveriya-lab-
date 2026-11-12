package ru.dikoveriya.lab

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatDate(time: Long): String = SimpleDateFormat("dd.MM.yyyy HH:mm",Locale("ru")).format(Date(time))
fun money(amount: Double): String = String.format(Locale("ru"),"%,.2f ₽",amount)
@Composable fun SectionTitle(s: String) {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Surface(Modifier.size(width=3.dp,height=21.dp),shape=RoundedCornerShape(4.dp),color=MaterialTheme.colorScheme.tertiary){}
        Text(s,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
    }
}
@Composable fun HelpIcon(title: String, description: String) {
    var show by remember { mutableStateOf(false) }
    IconButton(onClick={show=true},modifier=Modifier.size(32.dp)) { Icon(Icons.Outlined.HelpOutline,contentDescription="Объяснение: $title",tint=MaterialTheme.colorScheme.onSurfaceVariant) }
    if(show) AlertDialog(onDismissRequest={show=false},title={Text(title)},text={Text(description)},confirmButton={TextButton(onClick={show=false}){Text("Понятно")}})
}
@Composable fun Field(label: String, value: String, onChange: (String)->Unit, numeric: Boolean=false, single: Boolean=true) {
    OutlinedTextField(value=value,onValueChange=onChange,label={Text(label)},singleLine=single,
        keyboardOptions=if(numeric)KeyboardOptions(keyboardType=KeyboardType.Decimal) else KeyboardOptions.Default,
        modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(12.dp))
}
@Composable fun PickOne(label: String, values: List<String>, selected: String, change: (String)->Unit, help: String?=null) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment=Alignment.CenterVertically) {
        Text(label,fontWeight=FontWeight.Medium)
        if(help!=null) HelpIcon(label,help)
    }
    Box {
        OutlinedButton(onClick={expanded=true},
            modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
            Text(selected.ifEmpty{"Выбрать"},modifier=Modifier.weight(1f),maxLines=2)
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Outlined.ExpandMore,contentDescription="Открыть выбор: $label")
        }
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            values.forEach{v->DropdownMenuItem(text={Text(v)},onClick={change(v);expanded=false})}
        }
    }
}
@Composable fun DialogForm(title: String, close: ()->Unit, saveText: String="Сохранить", onSave: (() -> Unit)?=null, content: @Composable ColumnScope.()->Unit) {
    AlertDialog(onDismissRequest=close,title={Text(title)},text={
        Column(modifier=Modifier.fillMaxWidth().heightIn(max=480.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp),content=content)
    },confirmButton={if(onSave!=null) TextButton(onClick=onSave){Text(saveText)}},dismissButton={TextButton(onClick=close){Text("Закрыть")}})
}
@Composable fun SmallCard(title: String, caption: String, onClick: ()->Unit) {
    OutlinedCard(onClick=onClick,modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),
        colors=CardDefaults.outlinedCardColors(containerColor=MaterialTheme.colorScheme.surface),
        border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Text(title,fontWeight=FontWeight.SemiBold)
            Text(caption,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)
        }
    }
}
@Composable fun PillButton(title: String, onClick: () -> Unit) { OutlinedButton(onClick=onClick) { Text(title) } }
@Composable fun TextEntryRow(text: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).padding(vertical=13.dp),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically) {
        Text(text,fontWeight=FontWeight.Medium)
        Text("›",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
