package ru.dikoveriya.lab

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * 1.1.0 acceptance tests: each uses unique source data in the emulator,
 * never assumes user collection names and never runs on user devices.
 */
@RunWith(AndroidJUnit4::class)
class Lab11CoreAcceptanceInstrumentedTest {
    private fun plant(db:LabDatabase):Long {
        val name="QA-110-"+UUID.randomUUID()
        db.addPlant(name)
        return db.plants().first{it.name==name}.id
    }

    @Test fun purchaseThenUseThenSaleHasNoDoubleCashOutflow(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val p=plant(db)
            val item=db.addPlantItem11(p,"Горшечное",note="QA")
            val name="QA substrate "+UUID.randomUUID()
            // Custom material categories are validated: prepare the test fixture explicitly.
            db.addCategory11("Материалы","Субстраты")
            db.addMaterial(name,"л",0.0,0.0,0.0,"Субстраты")
            val material=db.materials().first{it.name==name}
            val from=System.currentTimeMillis()-1000
            val purchase=db.purchase11(name,"Субстраты",10000L,
                System.currentTimeMillis(),materialId=material.id,quantity=1.0,unit="л")
            assertTrue(purchase>0)
            assertEquals(1.0,db.materials().first{it.id==material.id}.quantity,1e-7)
            assertEquals(10000L,db.cashRows11().first{
                it.source=="purchase" && it.sourceId==purchase
            }.amount)
            db.performItemAction11(item,"Пересадка",System.currentTimeMillis(),
                note="0,2 л",newSubstrate=name,materialId=material.id,
                materialQty=.2)
            assertEquals(.8,db.materials().first{it.id==material.id}.quantity,1e-7)
            assertEquals(2000L,db.directCost11("item",item))
            db.performItemAction11(item,"Пересадка",System.currentTimeMillis(),
                note="ещё 0,5 л",materialId=material.id,materialQty=.5)
            assertEquals(.3,db.materials().first{it.id==material.id}.quantity,1e-7)
            assertEquals(7000L,db.directCost11("item",item))
            val sourcePayments=db.cashRows11().count{
                it.source=="purchase" && it.sourceId==purchase
            }
            assertEquals(1,sourcePayments)
            val sale=db.sellItem11(item,35000L,"Авито","","QA")
            assertTrue(sale>0)
            assertEquals("Продан",db.item11(item)!!.status)
            assertEquals(7000L,db.sales11().first{it.plantItemId==item}.cogs)
            val performance=db.performance11(from,System.currentTimeMillis()+1000)
            assertTrue(performance.salesRevenue>=35000L)
            // The substrate was purchased once, not paid for again on transplant.
            assertEquals(1,db.cashRows11().count{
                it.source=="purchase" && it.sourceId==purchase
            })
            assertEquals(1,db.cashRows11().count{
                it.source=="item_sale" && it.sourceId==sale
            })
        }finally{db.close()}
    }

    @Test fun cuttingRetainsIdentityDuringRootingAndReversibleTransplant(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val id=db.addPlantItem11(plant(db),"Горшечное",note="Материнское")
            val child=db.takeCutting11(id,System.currentTimeMillis(),
                "Вода","IBA","Подоконник","QA")
            val code=db.item11(child)!!.code
            assertTrue(code.startsWith("PLT-"))
            assertEquals(code,LabQr.getCode(code))
            assertEquals(id,db.item11(child)!!.parentItemId)
            assertTrue(db.itemLineage11(child).any{it.contains("PLT-")})
            val event=db.performItemAction11(child,"Пересадка",
                System.currentTimeMillis(),note="в горшок",
                newStage="В горшке",newKind="Горшечное",
                newSubstrate="Смесь QA")
            assertEquals("Горшечное",db.item11(child)!!.kind)
            db.cancelItemEvent11(event,"Ошибка записи субстрата")
            assertEquals("Черенок",db.item11(child)!!.kind)
            assertEquals("Укоренение",db.item11(child)!!.stage)
            assertEquals(id,db.item11(child)!!.parentItemId)
            assertTrue(db.itemEvents11(child).any{it.kind=="Отмена"})
        }finally{db.close()}
    }

    @Test fun rackAndMixedBatchPlacementIsAuditedAndRejectsUnknownCode(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val p=plant(db)
            val rack=db.createRack11("QA room "+UUID.randomUUID().toString().take(6),
                "QA стеллаж",2,3)
            val shelf=db.racks11().first{it.id==rack}.shelves.first()
            val first=shelf.slots[0].id
            val second=shelf.slots[1].id
            val item=db.addPlantItem11(p,"Горшечное")
            val created=db.createCulturesBulk11(p,listOf(1,1),"Побеги",0,
                LabTerms.IN_VITRO,null,null,null,"",
                System.currentTimeMillis(),"QA групповое перемещение")
            val code=db.cultureById(created.first())!!.code
            val itemCode=db.item11(item)!!.code
            val counts=db.moveSelected11(listOf(code,itemCode),first,"QA размещение")
            assertEquals(1,counts.first)
            assertEquals(1,counts.second)
            assertEquals(first,db.findLocationForCode11(code))
            assertEquals(first,db.findLocationForCode11(itemCode))
            assertEquals(2,db.slotOccupants11(first).size)
            val groupId=db.readableDatabase.rawQuery(
                "SELECT group_id FROM culture_groups WHERE culture_id=?",
                arrayOf(created.first().toString())
            ).use{c->assertTrue(c.moveToFirst());c.getLong(0)}
            val groupCode=db.readableDatabase.rawQuery(
                "SELECT code FROM production_groups WHERE id=?",
                arrayOf(groupId.toString())
            ).use{c->assertTrue(c.moveToFirst());c.getString(0)}
            val moved=db.moveSelected11(listOf(groupCode),second,"QA перенос партии")
            assertEquals(2,moved.first)
            created.forEach{child->
                assertEquals(second,
                    db.findLocationForCode11(db.cultureById(child)!!.code))
            }
            assertEquals(first,db.findLocationForCode11(itemCode))
            var rejected=false
            try{db.moveSelected11(listOf(code,"LAB-999999"),first,"Недопустимо")}
            catch(_:Exception){rejected=true}
            assertTrue(rejected)
            assertEquals(second,db.findLocationForCode11(code))
            assertEquals("LOC-%06d".format(java.util.Locale.US,first),
                LabQr.getCode("LOC-%06d".format(java.util.Locale.US,first)))
        }finally{db.close()}
    }

    @Test fun massCultureCreationHasIndependentIdentityAndCommonBatch(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val p=plant(db)
            val ids=db.createCulturesBulk11(p,listOf(1,1,2,1,3),"Побеги",1,
                LabTerms.IN_VITRO,null,null,null,"",
                System.currentTimeMillis(),"Проверка массовой партии")
            assertEquals(5,ids.size)
            assertEquals(5,ids.toSet().size)
            val codes=ids.map{db.cultureById(it)!!.code}
            assertEquals(5,codes.toSet().size)
            assertTrue(codes.all{it.startsWith("LAB-")})
            val groups=ids.map{child->
                db.readableDatabase.rawQuery(
                    "SELECT group_id FROM culture_groups WHERE culture_id=?",
                    arrayOf(child.toString())).use{c->
                    assertTrue(c.moveToFirst());c.getLong(0)
                }
            }
            assertEquals(1,groups.toSet().size)
        }finally{db.close()}
    }
}
