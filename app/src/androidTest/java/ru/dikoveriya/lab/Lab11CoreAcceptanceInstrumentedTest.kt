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

    @Test fun twentyThousandPhysicalPlantsStaySqlPagedWithoutLoadingEntireRegistry(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        val suffix=UUID.randomUUID().toString().take(8)
        val prefix="QA-STRESS-"+suffix+"-"
        try{
            val plantId=plant(db)
            val raw=db.writableDatabase
            val now=System.currentTimeMillis()
            raw.beginTransaction()
            try{
                val stmt=raw.compileStatement("""INSERT INTO plant_items(
                    code,plant_id,kind,stage,status,acquired_at,created_at,updated_at)
                    VALUES (?,?,'Горшечное','В работе','Активен',?,?,?)""")
                try{
                    repeat(20_000){i->
                        stmt.clearBindings()
                        stmt.bindString(1,prefix+i)
                        stmt.bindLong(2,plantId)
                        stmt.bindLong(3,now)
                        stmt.bindLong(4,now)
                        stmt.bindLong(5,now)
                        stmt.executeInsert()
                    }
                }finally{stmt.close()}
                raw.setTransactionSuccessful()
            }finally{raw.endTransaction()}
            assertEquals(20_000,db.itemsCount11(query=prefix))
            val last=db.plantItems11(query=prefix,limit=40,offset=19960)
            assertEquals(40,last.size)
            assertTrue(last.all{it.code.startsWith(prefix)})
            assertEquals(40,db.plantItems11(query=prefix,limit=40).size)
            assertTrue(db.item11(last.last().id)!=null)
        }finally{
            db.writableDatabase.delete("plant_items","code LIKE ?",arrayOf(prefix+"%"))
            db.close()
        }
    }

    @Test fun transplantWithTwoMaterialsAndPotIsAtomicAndFullyReversible(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val item=db.addPlantItem11(plant(db),"Горшечное",substrate="Исходный грунт")
            if("Субстраты" !in db.categories11("Материалы"))
                db.addCategory11("Материалы","Субстраты")
            val substrate="QA mixture "+UUID.randomUUID()
            val pot="QA pot "+UUID.randomUUID()
            db.addMaterial(substrate,"л",0.0,0.0,0.0,"Субстраты")
            db.addMaterial(pot,"шт",0.0,0.0,0.0,"Субстраты")
            val materialA=db.materials().first{it.name==substrate}.id
            val materialB=db.materials().first{it.name==pot}.id
            db.purchase11(substrate,"Субстраты",10000L,System.currentTimeMillis(),
                materialId=materialA,quantity=1.0,unit="л")
            db.purchase11(pot,"Субстраты",5000L,System.currentTimeMillis(),
                materialId=materialB,quantity=1.0,unit="шт")
            val before=db.itemEvents11(item).size
            var rejected=false
            try{
                db.performItemAction11(item,"Пересадка",System.currentTimeMillis(),
                    materialId=materialA,materialQty=.2,
                    extraMaterials=listOf(materialB to 99.0),
                    newPotMl=500,newSubstrate=substrate)
            }catch(_:Exception){rejected=true}
            assertTrue(rejected)
            assertEquals(before,db.itemEvents11(item).size)
            assertEquals(1.0,db.materials().first{it.id==materialA}.quantity,1e-7)
            assertEquals(1.0,db.materials().first{it.id==materialB}.quantity,1e-7)
            val event=db.performItemAction11(item,"Пересадка",
                System.currentTimeMillis(),newSubstrate=substrate,newPotMl=500,
                materialId=materialA,materialQty=.2,
                extraMaterials=listOf(materialB to 1.0))
            assertEquals(500,db.item11(item)!!.potMl)
            assertEquals(substrate,db.item11(item)!!.substrate)
            assertEquals(7000L,db.directCost11("item",item))
            assertEquals(2,org.json.JSONObject(db.itemEvents11(item)
                .first{it.id==event}.details).getJSONArray("usage_ids").length())
            db.cancelItemEvent11(event,"Указан неверный горшок")
            assertNull(db.item11(item)!!.potMl)
            assertEquals("Исходный грунт",db.item11(item)!!.substrate)
            assertEquals(0L,db.directCost11("item",item))
            assertEquals(1.0,db.materials().first{it.id==materialA}.quantity,1e-7)
            assertEquals(1.0,db.materials().first{it.id==materialB}.quantity,1e-7)
        }finally{db.close()}
    }

    @Test fun soldCultureCostIsFrozenAfterFurtherCloning(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val plantId=plant(db)
            val source=db.createCulturesBulk11(plantId,listOf(10),"Растения",0,
                LabTerms.READY,null,null,null,"",System.currentTimeMillis(),
                "Проверка себестоимости").single()
            if("Субстраты" !in db.categories11("Материалы"))
                db.addCategory11("Материалы","Субстраты")
            val name="QA COGS "+UUID.randomUUID()
            db.addMaterial(name,"л",0.0,0.0,0.0,"Субстраты")
            val materialId=db.materials().first{it.name==name}.id
            db.purchase11(name,"Субстраты",10000L,System.currentTimeMillis(),
                materialId=materialId,quantity=1.0,unit="л")
            db.consumeMaterial11(materialId,1.0,"culture",source)
            assertEquals(10000L,db.cultureCostBasis11(source))
            db.sale(source,2,100.0,"Ранняя продажа")
            val firstSale=db.readableDatabase.rawQuery(
                "SELECT id FROM sales WHERE culture_id=? ORDER BY id LIMIT 1",
                arrayOf(source.toString())).use{c->
                assertTrue(c.moveToFirst());c.getLong(0)
            }
            fun recordedCost():Long=db.readableDatabase.rawQuery(
                "SELECT cogs_minor FROM sale_details WHERE sale_id=?",
                arrayOf(firstSale.toString())).use{c->
                assertTrue(c.moveToFirst());c.getLong(0)
            }
            assertEquals(2000L,recordedCost())
            val before=db.cultureCostBasis11(source)
            assertEquals(8000L,before)
            db.splitCulture(source,listOf(
                TransferDestination(4,4,LabTerms.READY,null,null,"Растения")
            ),0,"Позднее размножение")
            assertEquals(4000L,db.cultureCostBasis11(source))
            assertEquals(2000L,recordedCost())
            val count=db.readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM culture_cost_transfers WHERE parent_culture_id=? AND reversed=0",
                arrayOf(source.toString())).use{c->c.moveToFirst();c.getInt(0)}
            assertTrue(count>=2)
        }finally{db.close()}
    }

    @Test fun cancellingShelfMoveRestoresActualOccupancyAndKeepsAudit(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val plantId=plant(db)
            val rack=db.createRack11("QA moving "+UUID.randomUUID().toString().take(6),
                "Стеллаж",1,2)
            val slots=db.racks11().first{it.id==rack}.shelves.first().slots
            val item=db.addPlantItem11(plantId,"Горшечное")
            db.moveItem11(item,slots[0].id,reason="Первая позиция")
            assertEquals(slots[0].id,db.item11(item)!!.locationId)
            db.moveItem11(item,slots[1].id,reason="Вторая позиция")
            assertEquals(slots[1].id,db.item11(item)!!.locationId)
            val latest=db.itemEvents11(item).first{it.kind=="Перемещение"}
            db.cancelItemEvent11(latest.id,"Ошибочно выбрана вторая полка")
            assertEquals(slots[0].id,db.item11(item)!!.locationId)
            assertEquals(slots[0].id,
                db.findLocationForCode11(db.item11(item)!!.code))
            assertTrue(db.slotOccupants11(slots[0].id).any{it.id==item})
            assertFalse(db.slotOccupants11(slots[1].id).any{it.id==item})
            assertTrue(db.itemEvents11(item).first{it.kind=="Отмена"}
                .note.contains("Ошибочно"))
            val first=db.itemEvents11(item).first{it.kind=="Перемещение" &&
                !it.cancelled}
            db.cancelItemEvent11(first.id,"Первое место тоже указано неверно")
            assertNull(db.item11(item)!!.locationId)
            assertNull(db.findLocationForCode11(db.item11(item)!!.code))
        }finally{db.close()}
    }

    @Test fun unusedCatalogCanBeDeletedButReferencedPriceHistoryIsProtected(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val name="QA category "+UUID.randomUUID()
            db.addCategory11("Материалы",name)
            assertTrue(name in db.categories11("Материалы"))
            assertTrue(db.deleteOrArchiveCategory11("Материалы",name))
            assertFalse(name in db.categories11("Материалы"))
            db.addCategory11("Материалы",name)
            val materialName="QA protected "+UUID.randomUUID()
            db.addMaterial(materialName,"л",1.0,0.0,20.0,name)
            assertFalse(db.deleteOrArchiveCategory11("Материалы",name))
            assertFalse(name in db.categories11("Материалы"))
            val category=db.materials().first{it.name==materialName}
            assertEquals(name,category.category)
            assertEquals(20.0,category.costPerUnit,0.0001)
        }finally{db.close()}
    }

    @Test fun equipmentSaleBooksOneReceiptAndNoDuplicateAcquisition(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val db=LabDatabase(context)
        try{
            val asset=db.addAsset11("QA used lamp "+UUID.randomUUID(),
                "Лампы","QA",180000L,System.currentTimeMillis())
            val expensesBefore=db.cashRows11(2000).count{
                it.direction=="Расход" && it.source=="purchase" &&
                    it.note.startsWith("QA used lamp")
            }
            assertEquals(1,expensesBefore)
            db.retireAsset11(asset,"Продано","Продажа бывшей в употреблении лампы",70000L)
            val rows=db.cashRows11(2000)
            assertEquals(1,rows.count{it.source=="asset_sale" &&
                it.direction=="Доход" && it.amount==70000L})
            assertEquals(1,rows.count{it.direction=="Расход" &&
                it.source=="purchase" && it.note.startsWith("QA used lamp")})
            assertEquals("Продано",db.assets11().first{it.id==asset}.status)
            var rejected=false
            try{db.retireAsset11(asset,"Продано","Повторная продажа",70000L)}
            catch(_:Exception){rejected=true}
            assertTrue("Duplicate sale must be rejected",rejected)
            assertEquals(1,db.cashRows11(2000).count{it.source=="asset_sale" &&
                it.amount==70000L})
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
