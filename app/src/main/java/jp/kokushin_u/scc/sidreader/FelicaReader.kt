package jp.kokushin_u.scc.sidreader

import android.content.Context
import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.NfcF
import android.util.Log
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.lang.StringBuilder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

class FelicaReader {
	val REG=0x0e
	val RC=0x80
	val MAC=0x81
	val ID=0x82
	val D_ID=0x83
	val SER_C=0x84
	val SYS_C=0x85
	val CKV=0x86
	val CK=0x87
	val MC=0x88
	val WCNT=0x90
	val MAC_A=0x91
	val STATE=0x92
	val CRC_CHECK=0xa0
	data class readResult(val status:String,val data:studentIDdata?)
	data class studentIDdata(val idm:String,val issueVersion:Int,val dataFormatVersion:Int?,val studentNo: String?, val name:String?,val isNameCut:Boolean?, val isNameJA:Boolean?,val furigana:String?,val isFuriganaCut:Boolean?,val romaji:String?,val isRomajiCut:Boolean?,val birthday:Date?,val enterdate:Date?,val expdate:Date?,val issuedate:Date?,val issueplace:String?,val issuecount: Int?,val prepaidbalance: Int?, val ckv:Int?)
	val ERR_NOT_SUI_ID = "NOT_SUI_ID"
	val ERR_TAG_DISCONNECT = "TAG_DISCONNECT"
	val ERR_NOT_SUPPORTED_VERSION = "NOT_SUPPORTED_VER"
	val STATUS_OK = "OK"
	val STATUS_OMIT_OVERFLOW = "OMIT_OVERFLOW"
	fun read(tag: Tag, context: Context): readResult{
		val nfc = NfcF.get(tag)
		var idm:ByteArray? = null
		nfc.connect()
//		Toast.makeText(context,"pollres:"+toHex(pollres),Toast.LENGTH_SHORT).show()
		val polling = polling(0x88b4)
		try {
			val pollres = nfc.transceive(polling)
			idm = Arrays.copyOfRange(pollres,2,10)
//			Toast.makeText(context,"pollres:"+toHex(pollres),Toast.LENGTH_LONG).show()
//			Toast.makeText(context,"IDm:"+toHex(idm),Toast.LENGTH_LONG).show()
		} catch (e: TagLostException){
		}
		if(idm != null){
			try {
				val id=readNfcLiteBlock(nfc,idm,ID)
				val cardtype=id.copyOfRange(10,16)
//				Toast.makeText(context,"id:"+toHex(id),Toast.LENGTH_LONG).show()
				if(cardtype.toString(Charsets.US_ASCII) == "SUIsi\u0001"){
//					Toast.makeText(context,"OK",Toast.LENGTH_LONG).show()
					val studentNum = readNfcLiteBlock(nfc,idm,0).toString(Charsets.US_ASCII).trim()
//					Toast.makeText(context,"num:"+studentNum,Toast.LENGTH_LONG).show()
					val datares = getStudentIDdata(nfc,idm)
					nfc.close()
					return  readResult(datares.status,datares.data)
				}else{
//					Toast.makeText(context,R.string.this_is_not_sui_id_card,Toast.LENGTH_LONG).show()
					nfc.close()
					return readResult(ERR_NOT_SUI_ID,null)
				}
			} catch (e: TagLostException){
//				Toast.makeText(context,R.string.tag_disconnected,Toast.LENGTH_LONG).show()
				nfc.close()
				return readResult(ERR_TAG_DISCONNECT,null)
			}
		}else{
//			Toast.makeText(context,R.string.this_is_not_sui_id_card,Toast.LENGTH_LONG).show()
			nfc.close()
			return readResult(ERR_NOT_SUI_ID,null)
		}



		nfc.close()
	}
	private fun getStudentIDdata(nfc:NfcF,idm:ByteArray):readResult{
		var status = STATUS_OK
		val issueFormatVersion = readNfcLiteBlock(nfc,idm,ID).get(15).toInt()
		// ckv
		val ckv = LEbytes2int(readNfcLiteBlock(nfc,idm,CKV).copyOfRange(0,2))
		// spad0
		val spad0 = readNfcLiteBlock(nfc,idm,0)
		val dataformatversion = LEbytes2int(spad0.copyOfRange(14,16))
		if(dataformatversion!=0&&dataformatversion!=1){
			return readResult(ERR_NOT_SUPPORTED_VERSION,null)
		}
		val studentNum = spad0.copyOfRange(0,14).toString(Charsets.US_ASCII).trim()
		// spad1
		val spad1 = readNfcLiteBlock(nfc,idm,1)
		val enterdate = bcd2date(spad1.copyOfRange(0,4))
		val expdate = bcd2date(spad1.copyOfRange(4,8))
		val issuedate = bcd2date(spad1.copyOfRange(8,12))
		val issueplace = spad1.copyOfRange(12,15).toString(Charsets.US_ASCII)
		val issuecount = spad1.copyOfRange(15,16).get(0).toInt()
		//spad5
		val spad5 = readNfcLiteBlock(nfc,idm,5)
		val birthday = bcd2date(spad5.copyOfRange(0,4))
		// overflow
		val overflowbytes = spad5.copyOfRange(4,16)
		val zeroindices = mutableListOf<Int>()
		for ((i,b) in overflowbytes.withIndex()){
			if(b.toInt()==0 && !(zeroindices.size==0&&i%2==1)){
				zeroindices.add(i)
			}
		}
		var nameOverflow = ByteArray(0)
		var furiganaOverflow = ByteArray(0)
		var romajiOverflow = ByteArray(0)
		var isNameCut = false
		var isFuriganaCut = false
		var isRomajiCut = false
		var isNameJA = true
		if(dataformatversion==0) {
			// 学生証フォーマットの欠陥により溢れ分の読み込みは氏名部にヌルバイトが無くて溢れ分バイトの末尾２バイトがヌルバイトの場合のみ

			if (toHex(overflowbytes.copyOfRange(10, 12)) == "0000" && zeroindices.size >= 4) {
				val should0bytes = overflowbytes.copyOfRange(zeroindices[2], 12)
				if (should0bytes.all { byte -> byte.toInt() == 0 }) {
					nameOverflow = overflowbytes.copyOfRange(0, zeroindices[0])
					furiganaOverflow = overflowbytes.copyOfRange(zeroindices[0], zeroindices[1])
					romajiOverflow = overflowbytes.copyOfRange(zeroindices[1], zeroindices[2])

				} else {
					status = STATUS_OMIT_OVERFLOW
				}
			} else {
				status = STATUS_OMIT_OVERFLOW
			}
//		if(zeroindices.size>0){
//			nameOverflow = overflowbytes.copyOfRange(0,zeroindices[0])
//		}else{
//			nameOverflow = overflowbytes.copyOfRange(0,16)
//		}
//		if(zeroindices.size>1){
//			furiganaOverflow = overflowbytes.copyOfRange(zeroindices[0],zeroindices[1])
//		}else if (zeroindices.size == 1){
//			furiganaOverflow = overflowbytes.copyOfRange(zeroindices[0],16)
//		}
//		if(zeroindices.size>2){
//			furiganaOverflow = overflowbytes.copyOfRange(zeroindices[1],zeroindices[2])
//		}else if (zeroindices.size == 2){
//			romajiOverflow = overflowbytes.copyOfRange(zeroindices[1],16)
//		}
		}else if(dataformatversion==1){
			val ofbyte0 = overflowbytes[0].toInt() and 0xff
			val nameOFlen = ofbyte0 shr 4
			val furiganaOFlen = ofbyte0 and 0x0f
			val ofbyte1 = overflowbytes[1].toInt() and 0xff
			val romajiOFlen = ofbyte1 shr 4
			isNameCut = ofbyte1 and 8 > 0
			isFuriganaCut = ofbyte1 and 4 > 0
			isRomajiCut = ofbyte1 and 2 > 0
			isNameJA = ofbyte1 and 1 > 0
			val ofbytes10 = overflowbytes.copyOfRange(2,12)
			nameOverflow = ofbytes10.copyOfRange(0,nameOFlen)
			furiganaOverflow = ofbytes10.copyOfRange(nameOFlen,nameOFlen+furiganaOFlen)
			romajiOverflow = ofbytes10.copyOfRange(nameOFlen+furiganaOFlen,nameOFlen+furiganaOFlen+romajiOFlen)
		}
		//spad2-4
		val spad2 = readNfcLiteBlock(nfc,idm,2)
		val name = (bytearrayTrim(spad2)+nameOverflow).toString(Charsets.UTF_16LE)
		val spad3 = readNfcLiteBlock(nfc,idm,3)
		val furigana =Normalizer.normalize((bytearrayTrim(spad3)+furiganaOverflow).toString(Charset.forName("SJIS")),Normalizer.Form.NFKC)
		val spad4 = readNfcLiteBlock(nfc,idm,4)
		val romaji = (bytearrayTrim(spad4)+romajiOverflow).toString(Charsets.US_ASCII)

		//spad6
		val spad6 = readNfcLiteBlock(nfc,idm,6)
		val ksbalance = LEbytes2int(spad6.copyOfRange(0,4))

		return readResult(status,
			studentIDdata(toHex(idm),issueFormatVersion,dataformatversion,studentNum,name,isNameCut,isNameJA,furigana,isFuriganaCut,romaji,isRomajiCut,birthday,enterdate,expdate,issuedate,issueplace,issuecount,ksbalance,ckv))


	}
	private fun bcd2date(bcd: ByteArray):Date{
		val ymdformat = SimpleDateFormat("yyyyMMdd", Locale.US)
		val dateStr = toHex(bcd)
		return ymdformat.parse(dateStr)
	}
	private fun polling(systemCode: Int): ByteArray {
		val bout = ByteArrayOutputStream(100)

		bout.write(0x00)           //　ダミー
		bout.write(0x00)           // コマンドコード
		bout.write((systemCode and 0xff00) ushr 8)  // systemCode
		bout.write(systemCode and 0xff)  // systemCode
		bout.write(0x01)           // リクエストコード
		bout.write(0x0f)           // タイムスロット

		val msg = bout.toByteArray()
		msg[0] = msg.size.toByte()
//		for (a in msg.indices) {
//			Log.d("tag", msg[a].toString())
//		} //logdで確認
		return msg
	}
	private fun readNfcLiteBlock(nfc: NfcF,idm:ByteArray,block:Int):ByteArray{
		val req = readLiteBlock(idm,block)
		val res = nfc.transceive(req)
		return parse(res)
	}
	private fun readLiteBlock(idm: ByteArray,block:Int):ByteArray{
		return readWithoutEncryption(idm,0x000B, intArrayOf(block))
	}
	private fun readWithoutEncryption(idm: ByteArray, serviceCode: Int, blockList: IntArray): ByteArray {
		val bout = ByteArrayOutputStream(100)

		bout.write(0)              // データ長バイトのダミー
		bout.write(0x06)           // コマンドコード
		bout.write(idm)            // IDm 8byte
		bout.write(1)


		bout.write(serviceCode and 0xff) // サービスコード下位バイト
		bout.write((serviceCode and 0xff00) ushr 8) // サービスコード上位バイト
		bout.write(blockList.size)           // ブロック数


		for (i in 0 until blockList.size) {
			bout.write(0x80)
			bout.write(blockList[i])          // ブロック番号
		}

		val msg = bout.toByteArray()
		msg[0] = msg.size.toByte()
		return msg
	}
	private fun parse(res: ByteArray): ByteArray {
		if (res[10].toInt() != 0x00)
			throw RuntimeException("this code is " + res[10])

		// res[12] 応答ブロック数
		//　13からreturnで返す
		val data: ByteArray = ByteArray(res.size-13)
		for(a in 0..res.size - 14) {
			data[a] = res[a + 13]
		}
		return data
	}
	private fun toHex(ba: ByteArray): String{
		val sbuf = StringBuilder()
		for (i in ba.indices) {
			var hex = "0" + Integer.toString(ba[i].toInt() and 0x0ff, 16)
			if (hex.length > 2)
				hex = hex.substring(1, 3)
			sbuf.append("$hex")
		}
		return sbuf.toString()
	}
	private fun bytearrayTrim(ba: ByteArray): ByteArray{
		var lastpos = ba.size-1
		for(i in (ba.size-1) downTo 0){
			if (ba[i].toInt() !=0){
				lastpos = i
				break
			}
		}
		return ba.copyOfRange(0,lastpos+1)
	}
	private fun LEbytes2int(ba:ByteArray):Int{
		var res = 0
		for(i in 0..(ba.size-1)){
			res += (ba[i].toInt() and 0xff) shl i*8
		}
		return res
	}
}