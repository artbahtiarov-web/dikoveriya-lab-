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
            if("Субстраты" !in db.categories11("Материалы"))
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

    @Test fun weightedStockPriceIsFrozenAndFailedConsumptionRollsBack(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            if("Субстраты" !in db.categories11("Материалы"))
                db.addCategory11("Материалы","Субстраты")
            val materialName="QA weighted "+UUID.randomUUID()
            db.addMaterial(materialName,"л",0.0,0.0,0.0,"Субстраты")
            val materialId=db.materials().first{it.name==materialName}.id
            val item=db.addPlantItem11(plant(db),"Горшечное")
            val p1=db.purchase11(materialName,"Субстраты",10000,
                System.currentTimeMillis(),materialId=materialId,quantity=1.0,unit="л")
            val first=db.performItemAction11(item,"Пересадка",
                System.currentTimeMillis(),newSubstrate=materialName,
                materialId=materialId,materialQty=0.2)
            assertEquals(2000L,db.directCost11("item",item))
            val p2=db.purchase11(materialName,"Субстраты",30000,
                System.currentTimeMillis(),materialId=materialId,quantity=1.0,unit="л")
            db.performItemAction11(item,"Пересадка",System.currentTimeMillis(),
                materialId=materialId,materialQty=0.5)
            val used=db.readableDatabase.rawQuery("""
                SELECT total_minor FROM stock_usage WHERE entity_kind='item'
                    AND entity_id=? ORDER BY id""",
                arrayOf(item.toString())).use{c->buildList{
                while(c.moveToNext())add(c.getLong(0))
            }}
            assertEquals(2000L,used[0])
            assertEquals(10556L,used[1])
            assertEquals(12556L,db.directCost11("item",item))
            val beforeQty=db.materials().first{it.id==materialId}.quantity
            val beforeCosts=db.directCost11("item",item)
            var failed=false
            try{db.performItemAction11(item,"Пересадка",
                System.currentTimeMillis(),materialId=materialId,materialQty=99.0)}
            catch(_:Exception){failed=true}
            assertTrue("Insufficient stock must abort the whole operation",failed)
            assertEquals(beforeQty,db.materials().first{it.id==materialId}.quantity,1e-9)
            assertEquals(beforeCosts,db.directCost11("item",item))
            assertEquals(1,db.cashRows11().count{it.source=="purchase" && it.sourceId==p1})
            assertEquals(1,db.cashRows11().count{it.source=="purchase" && it.sourceId==p2})
            assertEquals(2,db.itemEvents11(item).count{it.kind=="Пересадка"})
        }finally{db.close()}
    }

    @Test fun scheduledReminderNeverUsesArbitraryPastTime(){
        val zone=java.time.ZoneId.of("Europe/Moscow")
        val date=java.time.LocalDate.of(2026,12,5)
        val due=date.atTime(23,59).atZone(zone).toInstant().toEpochMilli()
        val expected=date.atTime(9,0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected,Lab11Reminders.dueReminderAt(due,zone))
    }

    @Test fun equipmentServiceDoesNotRebookPurchaseAndTariffChangesDoNotOverlap(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val now=System.currentTimeMillis()
            val asset=db.addAsset11("QA lamp "+UUID.randomUUID(),
                "Лампы","Тест",250000L,now,measuredWatts=70.0)
            val purchasePayment=db.cashRows11().first{
                it.source=="purchase" && it.note.startsWith("QA lamp")
            }
            assertEquals(250000L,purchasePayment.amount)
            val service=db.recordAssetService11(asset,"Ремонт",now,
                "Заменён драйвер",35000L)
            assertEquals(1,db.cashRows11().count{
                it.source=="asset_service" && it.sourceId==service
            })
            assertEquals(1,db.cashRows11().count{it.id==purchasePayment.id})
            assertEquals(250000L,db.assets11().first{it.id==asset}.cost)
            db.addLight11(asset,null,70.0,16.0,500,now)
            db.addLight11(asset,null,65.0,16.0,600,now+86400000L)
            val rows=db.lightProfiles11().filter{it.assetId==asset}
            assertEquals(2,rows.size)
            assertEquals(now+86400000L,rows.first{it.from==now}.to)
            assertEquals(null,rows.first{it.from==now+86400000L}.to)
            var refused=false
            try{db.addLight11(asset,null,65.0,16.0,900,now)}
            catch(_:Exception){refused=true}
            assertTrue(refused)
            assertEquals(2,db.lightProfiles11().count{it.assetId==asset})
        }finally{db.close()}
    }

    @Test fun customTemplateTypedFieldsAreValidatedAndDoNotBypassStock(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val item=db.addPlantItem11(plant(db),"Горшечное")
            val name="QA уход "+UUID.randomUUID()
            val definition=templateFieldsJson11(listOf(
                Lab11TemplateField("Объём","unit",true,"мл"),
                Lab11TemplateField("Сезон","choice",true,
                    choices=listOf("Лето","Зима")),
                Lab11TemplateField("Дата осмотра","date",false)))
            db.addTemplate11(name,"Горшечное",definition)
            val before=db.itemEvents11(item).size
            var rejected=false
            try{db.performItemAction11(item,name,System.currentTimeMillis(),
                customFields=mapOf("Объём" to "не число","Сезон" to "Лето"))}
            catch(_:Exception){rejected=true}
            assertTrue(rejected)
            assertEquals(before,db.itemEvents11(item).size)
            rejected=false
            try{db.performItemAction11(item,name,System.currentTimeMillis(),
                materialQty=3.0,
                customFields=mapOf("Объём" to "100","Сезон" to "Лето"))}
            catch(_:Exception){rejected=true}
            assertTrue("No unlinked inventory movement",rejected)
            assertEquals(before,db.itemEvents11(item).size)
            val event=db.performItemAction11(item,name,System.currentTimeMillis(),
                customFields=mapOf("Объём" to "100.5","Сезон" to "Лето",
                    "Дата осмотра" to "05.12.2026"))
            assertTrue(event>0)
            val saved=db.itemEvents11(item).first{it.id==event}
            assertTrue(saved.details.contains("Объём"))
            assertTrue(saved.details.contains("Сезон"))
            val legacy=parseTemplateFields11("""["Отметка"]""")
            assertEquals("text",legacy.single().kind)
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
