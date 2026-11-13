package ru.dikoveriya.lab

import android.graphics.Bitmap
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CancellationException

data class NiimJobProgress(
    val sent:Int,val confirmed:Int,val total:Int,
    val message:String,val uncertain:Boolean=false
)

/**
 * One-page jobs are deliberate: when a model disconnects during a multi-page
 * job we can report exactly which individual container has unknown status.
 * No automatic retransmission of a possibly printed label.
 */
class NiimbotPrintSession(
    private val connection:NiimbotBle,
    private val model:NiimModel
) {
    private suspend fun ack(op:Int,payload:ByteArray,reply:Int,timeout:Long=5500){
        connection.exchange(op,payload,reply,timeout)
    }

    private suspend fun handshake(){
        if(model.task!="b1")return
        // For b1 printer-firmware the extended handshake arms image printing.
        // The model/status part is already queried at connect; repeat idempotently.
        connection.exchange(0xa5,byteArrayOf(1),0xb5,5000)
        for(key in listOf(8,11,13,10,7,3,12,9)){
            connection.exchange(0x40,byteArrayOf(key.toByte()),0x40+key,5000)
        }
        connection.exchange(0xdc,byteArrayOf(4),0xd9,5000)
    }

    suspend fun print(
        jobs:List<Pair<String,Bitmap>>,
        density:Int=3,
        flipFeed:Boolean=false,
        progress:(NiimJobProgress)->Unit
    ){
        require(jobs.isNotEmpty()&&jobs.size<=200)
        require(jobs.map{it.first}.distinct().size==jobs.size){
            "В очереди есть повторяющиеся коды"
        }
        require(density in 1..5)
        check(connection.connected){"Принтер не подключён"}
        var confirmed=0
        var sent=0
        try{
            handshake()
            for((code,bitmap) in jobs){
                // Render/validate ALL row data before starting each print job.
                val rows=NiimRaster.rowFrames(bitmap,model,flipFeed)
                progress(NiimJobProgress(sent,confirmed,jobs.size,
                    "Подготовка этикетки $code"))
                val rasterWidth=model.width
                val length=bitmap.width
                ack(0x21,byteArrayOf(density.toByte()),0x31)
                ack(0x23,byteArrayOf(1),0x33)
                val start=if(model.task=="v4")byteArrayOf(
                    0,1,0,0,0,0,0,3,0
                )else byteArrayOf(0,1,0,0,0,0,0)
                ack(0x01,start,0x02)
                if(model.task=="v4"){
                    connection.write(NiimCodec.packet(0xa3,byteArrayOf(1)))
                    delay(30)
                }else{
                    ack(0x03,byteArrayOf(1),0x04)
                }
                val size=NiimCodec.ushort(length)+NiimCodec.ushort(rasterWidth)+
                    byteArrayOf(0,1)
                val page=if(model.task=="v4")size+ByteArray(7) else size
                ack(0x13,page,0x14)
                // Row messages are one-way and MUST be paced on BLE.
                rows.forEach{connection.write(it)}
                sent++
                progress(NiimJobProgress(sent,confirmed,jobs.size,
                    "Этикетка $code отправлена. Жду счётчик напечатанных."))
                ack(0xe3,byteArrayOf(1),0xe4,12000)
                var printed=false
                withTimeout(35000){
                    while(!printed){
                        val status=connection.exchange(0xa3,byteArrayOf(1),
                            0xb3,5500)
                        val payload=status.payload
                        if(payload.size>=2){
                            // Page counter, NOT the progress percentage.
                            printed=NiimCodec.uint16(payload[0],payload[1])>=1
                        }
                        if(!printed)delay(450)
                    }
                }
                ack(0xf3,byteArrayOf(1),0xf4,12000)
                confirmed++
                progress(NiimJobProgress(sent,confirmed,jobs.size,
                    "Принтер подтвердил страницу для $code"))
            }
            progress(NiimJobProgress(sent,confirmed,jobs.size,
                "Устройство подтвердило "+confirmed+" из "+jobs.size+" этикеток"))
        }catch(e:Exception){
            progress(NiimJobProgress(sent,confirmed,jobs.size,
                "Печать остановлена: "+(e.message?:"разрыв связи")+
                    ". У неподтверждённых этикеток результат неизвестен. Не повторяй автоматически.",
                uncertain=true))
            throw e
        }
    }
}
