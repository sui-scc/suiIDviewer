package jp.kokushin_u.scc.sidreader

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import android.provider.Settings
import android.text.format.DateUtils
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity() {

    private val nfcAdapter: NfcAdapter? by lazy { NfcAdapter.getDefaultAdapter(this) }
    private var pendingIntent: PendingIntent? = null
    private var intentFilters: Array<IntentFilter>? = null
    private val techLists = arrayOf((arrayOf(NfcF::class.java.name)))

    override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

        pendingIntent = PendingIntent.getActivity(this,0,Intent(this,javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),0)
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        ndef.addDataType("*/*")
        intentFilters = arrayOf(ndef)

        if(nfcAdapter == null){
            val builder = AlertDialog.Builder(this)
            builder.setMessage(R.string.not_support_nfc)
            builder.setPositiveButton(R.string.cancel, null)
        }else if(!nfcAdapter!!.isEnabled){
            val builder = AlertDialog.Builder(this)
            builder.setTitle(R.string.nfc_disabled)
            builder.setMessage(R.string.please_enable_nfc)
            builder.setPositiveButton(R.string.setting) { _, _ -> startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) }
            builder.setNegativeButton(R.string.cancel, null)

            val myDialog = builder.create()
            //ダイアログ画面外をタッチされても消えないようにする。
            myDialog.setCanceledOnTouchOutside(false)
            //ダイアログ表示
            myDialog.show()
        }
        getTag(intent)
	}
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        getTag(intent)
    }
    override fun onResume() {
        super.onResume()

        // NFCの読み込みを有効化
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFilters, techLists)
    }
    override fun onPause() {
        if (this.isFinishing) {
            nfcAdapter?.disableForegroundDispatch(this)
        }
        super.onPause()
    }
    private fun getTag(intent: Intent) {
        // IntentにTagの基本データが入ってくるので取得。
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
        val felicaReader = FelicaReader()
        val readRes = felicaReader.read(tag, applicationContext)
        if (readRes.data!=null){
            if(readRes.status==felicaReader.STATUS_OMIT_OVERFLOW){
                findViewById<TextView>(R.id.status_text).text=getText(R.string.overflow_omitted)
            }else if(readRes.status==felicaReader.STATUS_OK){
                findViewById<TextView>(R.id.status_text).text=getText(R.string.read_done)
            }
            val resdata = readRes.data
            findViewById<TextView>(R.id.idm).text = resdata.idm.toUpperCase()
            if(resdata.studentNo!=null){
                findViewById<TextView>(R.id.student_no).text = resdata.studentNo
                val facultyNum = Integer.parseInt(resdata.studentNo.substring(0,1))
                val departmentNum = Integer.parseInt(resdata.studentNo.substring(1,2))
                val facdep = getDepartment(facultyNum,departmentNum)
                findViewById<TextView>(R.id.faculty).text = facdep[0]
                findViewById<TextView>(R.id.department).text = facdep[1]
            }
            if(resdata.name!=null){
                findViewById<TextView>(R.id.name).text = resdata.name
            }
            if(resdata.furigana!=null){
                findViewById<TextView>(R.id.furigana).text = resdata.furigana
            }
            if(resdata.romaji!=null){
                findViewById<TextView>(R.id.romaji).text = resdata.romaji
            }
            if(resdata.birthday!=null){
                findViewById<TextView>(R.id.birthday).text = DateUtils.formatDateTime(this,resdata.birthday.time,DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL)
            }
            if(resdata.enterdate!=null){
                findViewById<TextView>(R.id.enter_date).text = DateUtils.formatDateTime(this,resdata.enterdate.time,DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL)
            }
            if(resdata.expdate!=null){
                findViewById<TextView>(R.id.exp_date).text = DateUtils.formatDateTime(this,resdata.expdate.time,DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL)
            }
            if(resdata.issuedate!=null){
                findViewById<TextView>(R.id.issue_date).text = DateUtils.formatDateTime(this,resdata.issuedate.time,DateUtils.FORMAT_SHOW_YEAR or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL)
            }
            if(resdata.issueplace!=null){
                findViewById<TextView>(R.id.issue_place).text = resdata.issueplace
            }
            if(resdata.issuecount!=null){
                findViewById<TextView>(R.id.issue_count).text = resdata.issuecount.toString()
            }
            if(resdata.prepaidbalance!=null){
                findViewById<TextView>(R.id.ksmart_balance).text =
                    getText(R.string.value_yen).toString().format(resdata.prepaidbalance)
            }
            if(resdata.ckv!=null){
                findViewById<TextView>(R.id.ckv).text = resdata.ckv.toString()
            }

            findViewById<TextView>(R.id.issuever).text = resdata.issueVersion.toString()

            if(resdata.dataFormatVersion!=null){
                findViewById<TextView>(R.id.dataver).text = resdata.dataFormatVersion.toString()
            }
            Snackbar.make(findViewById(R.id.topLayout),R.string.read_done,Snackbar.LENGTH_LONG).show()
        }else if(readRes.status == felicaReader.ERR_NOT_SUI_ID){
            Snackbar.make(findViewById(R.id.topLayout),R.string.this_is_not_sui_id_card,Snackbar.LENGTH_SHORT).show()
        }else if(readRes.status == felicaReader.ERR_TAG_DISCONNECT){
            Snackbar.make(findViewById(R.id.topLayout),R.string.tag_disconnected,Snackbar.LENGTH_SHORT).show()
        }else if(readRes.status == felicaReader.ERR_NOT_SUPPORTED_VERSION){
            Snackbar.make(findViewById(R.id.topLayout),R.string.not_supported_format_ver,Snackbar.LENGTH_SHORT).show()
        }
    }
    private fun getDepartment(faculty:Int,department:Int):List<String>{
        var facultyStr = ""
        var departmentStr = ""
        if(faculty==1){
            facultyStr=getString(R.string.faculty_intlcomm)
            if(department==1)departmentStr=getString(R.string.department_intlculture)
            else if(department==2)departmentStr=getString(R.string.department_french)
            else if(department==3)departmentStr=getString(R.string.department_intlhistlitera)
        }
        else if(faculty==2){
            facultyStr=getString(R.string.faculty_law)
            if(department==1)departmentStr=getString(R.string.department_frenchlaw)
        }
        else if(faculty==3){
            facultyStr=getString(R.string.faculty_science)
            if(department==1)departmentStr=getString(R.string.department_electromecha)
            else if(department==2)departmentStr=getString(R.string.department_frenchbiology)
            else if(department==3)departmentStr=getString(R.string.department_eurmolecbiology)
        }
        else if(faculty==4){
            facultyStr=getString(R.string.faculty_intltourism)
            if(department==1)departmentStr=getString(R.string.department_sfrancetourism)
            else if(department==2)departmentStr=getString(R.string.department_oceaniatourism)
            else if(department==3)departmentStr=getString(R.string.department_gsid)
            else if(department==4)departmentStr=getString(R.string.department_tourismmgnt)
            else if(department==5)departmentStr=getString(R.string.department_alpsmttourism)
            else if(department==6)departmentStr=getString(R.string.department_samericahist)
            else if(department==7)departmentStr=getString(R.string.department_portugaltourism)
            else if(department==8)departmentStr=getString(R.string.department_japantourism)
            else if(department==9)departmentStr=getString(R.string.department_tourismgeo)

        }
        return listOf(facultyStr,departmentStr)
    }
}
