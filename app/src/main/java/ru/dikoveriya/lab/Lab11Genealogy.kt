package ru.dikoveriya.lab

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountTree
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Grass
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal data class Lab11FamilyNode(
    val id:Long,val code:String,val kind:String,val depth:Int,
    val parentItemId:Long?,val parentCultureId:Long?
)

/** Traverse parents to family root, then list descendants with SQLite's recursive CTE. */
internal fun LabDatabase.familyTree11(itemId:Long,limit:Int=150):List<Lab11FamilyNode>{
    require(limit in 1..500)
    val db=readableDatabase
    var root=itemId
    val visited=mutableSetOf<Long>()
    for(i in 0 until 50){
        require(visited.add(root)){"Обнаружена циклическая связь происхождения"}
        val parent=db.rawQuery("SELECT parent_item_id FROM plant_items WHERE id=?",
            arrayOf(root.toString())).use{c->
                require(c.moveToFirst()){"Растение не найдено"}
                if(c.isNull(0))null else c.getLong(0)
            }
        if(parent==null)break
        root=parent
    }
    return db.rawQuery("""WITH RECURSIVE tree(id,parent_item_id,parent_culture_id,
        code,kind,depth,path) AS(
        SELECT id,parent_item_id,parent_culture_id,code,kind,0,
          printf('%010d',id) FROM plant_items WHERE id=?
        UNION ALL
        SELECT c.id,c.parent_item_id,c.parent_culture_id,c.code,c.kind,
          t.depth+1,t.path||'/'||printf('%010d',c.id)
          FROM plant_items c JOIN tree t ON c.parent_item_id=t.id WHERE t.depth<50
        )
        SELECT id,code,kind,depth,parent_item_id,parent_culture_id
        FROM tree ORDER BY path LIMIT """+limit,arrayOf(root.toString())).use{c->buildList{
        while(c.moveToNext())add(Lab11FamilyNode(c.getLong(0),c.getString(1),
            c.getString(2),c.getInt(3),
            if(c.isNull(4))null else c.getLong(4),
            if(c.isNull(5))null else c.getLong(5)))
    }}
}

internal fun LabDatabase.cultureCode11(id:Long):String?=
    readableDatabase.rawQuery("SELECT code FROM cultures WHERE id=?",
        arrayOf(id.toString())).use{c->if(c.moveToFirst())c.getString(0) else null}

@Composable
internal fun Lab11GenealogyView(
    db:LabDatabase,itemId:Long,revision:Int,
    onOpenItem:(Long)->Unit,onOpenCulture:(Long)->Unit
){
    val nodes=remember(itemId,revision){db.familyTree11(itemId,200)}
    var all by rememberSaveable(itemId){mutableStateOf(false)}
    val shown=if(all)nodes else nodes.take(16)
    Column(verticalArrangement=Arrangement.spacedBy(7.dp)){
        Row(verticalAlignment=Alignment.CenterVertically,
            horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Icon(Icons.Outlined.AccountTree,contentDescription=null,
                tint=Lab07Palette.Brass)
            Text("Дерево происхождения",
                style=MaterialTheme.typography.titleMedium)
        }
        nodes.firstOrNull()?.parentCultureId?.let{source->
            val code=remember(source){db.cultureCode11(source)}
            if(code!=null){
                OutlinedCard(onClick={onOpenCulture(source)},
                    modifier=Modifier.fillMaxWidth()){
                    Row(Modifier.padding(9.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Outlined.AccountTree,contentDescription=null)
                        Text(" Исходная культура: "+code,
                            modifier=Modifier.weight(1f))
                        Icon(Icons.Outlined.ChevronRight,"Открыть")
                    }
                }
            }
        }
        shown.forEach{node->
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
                Spacer(Modifier.width((node.depth*13).coerceAtMost(100).dp))
                OutlinedCard(onClick={onOpenItem(node.id)},
                    modifier=Modifier.weight(1f),
                    shape=RoundedCornerShape(12.dp),
                    colors=CardDefaults.outlinedCardColors(
                        containerColor=if(node.id==itemId)
                            Lab07Palette.Champagne else MaterialTheme.colorScheme.surface)){
                    Row(Modifier.padding(9.dp),verticalAlignment=Alignment.CenterVertically){
                        Icon(Icons.Outlined.Grass,contentDescription=null,
                            tint=if(node.id==itemId)
                                Lab07Palette.Forest else MaterialTheme.colorScheme.primary,
                            modifier=Modifier.size(18.dp))
                        Column(Modifier.weight(1f).padding(start=8.dp)){
                            Text(node.code,style=MaterialTheme.typography.labelMedium,
                                color=if(node.id==itemId)Lab07Palette.Ink
                                    else MaterialTheme.colorScheme.onSurface)
                            Text(node.kind,style=MaterialTheme.typography.labelSmall,
                                color=if(node.id==itemId)Lab07Palette.Ink
                                    else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Outlined.ChevronRight,"Открыть растение",
                            tint=if(node.id==itemId)Lab07Palette.Ink
                                else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        if(nodes.size>16 && !all)
            TextButton(onClick={all=true}){
                Text("Показать остальные "+(nodes.size-16)+" экземпляров")
            }
        if(nodes.size>=200)Text("Большая линия размножения. На схеме показаны первые 200 связанных растений.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
