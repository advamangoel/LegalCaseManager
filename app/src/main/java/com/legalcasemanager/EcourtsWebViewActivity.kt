package com.legalcasemanager

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class EcourtsWebViewActivity : ComponentActivity() {
    private val ecourtsUrl = "https://services.ecourts.gov.in/ecourtindia_v6/"
    private var webView: WebView? = null
    private var capturedText by mutableStateOf("")
    private var parsed by mutableStateOf(EcourtsCaseData())
    private var pageReady by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val caseId = intent.getLongExtra(EXTRA_CASE_ID, 0L)
        val cnr = intent.getStringExtra(EXTRA_CNR).orEmpty()

        setContent {
            MaterialTheme {
                EcourtsScreen(
                    caseId = caseId,
                    cnr = cnr,
                    pageReady = pageReady,
                    capturedText = capturedText,
                    parsed = parsed,
                    onBack = { finish() },
                    onAutoFill = { autoFillCnr(cnr) },
                    onReadPage = { readPage(cnr) },
                    onSave = { saveToLocal(caseId, parsed) }
                )
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        return WebView(this).apply {
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageReady = true
                    Toast.makeText(
                        this@EcourtsWebViewActivity,
                        "eCourts loaded. CNR can be auto-filled; complete any site challenge if shown.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
            webChromeClient = WebChromeClient()
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = WebSettings.getDefaultUserAgent(this@EcourtsWebViewActivity)
            addJavascriptInterface(PageBridge(), "LegalCaseManager")
            loadUrl(ecourtsUrl)
        }
    }

    private fun autoFillCnr(cnr: String) {
        if (cnr.isBlank()) {
            Toast.makeText(this, "This case has no CNR number.", Toast.LENGTH_SHORT).show()
            return
        }
        webView?.evaluateJavascript(
            EcourtsJs.autoFillCnr(cnr)
        ) { result ->
            Toast.makeText(
                this,
                if (result?.contains("true") == true)
                    "CNR auto-filled. Please complete the website's CAPTCHA if requested."
                else
                    "CNR field was not detected. Use the eCourts page normally.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun readPage(cnr: String) {
        webView?.evaluateJavascript(
            "(function(){return document.body ? document.body.innerText : '';})()"
        ) { json ->
            val text = android.util.JsonReader(java.io.StringReader(json)).let { reader ->
                // evaluateJavascript returns a JSON string; simple unescape is sufficient here.
                try {
                    val s = org.json.JSONTokener(json).nextValue()
                    s?.toString().orEmpty()
                } catch (_: Exception) {
                    json.orEmpty().trim('"')
                        .replace("\\n", "\n")
                        .replace("\\\"", "\"")
                }
            }
            capturedText = text
            parsed = EcourtsParser.parse(text, cnr)
            Toast.makeText(this, "Page data captured for review.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveToLocal(caseId: Long, data: EcourtsCaseData) {
        if (caseId == 0L) {
            Toast.makeText(this, "Invalid local case.", Toast.LENGTH_SHORT).show()
            return
        }
        val db = CaseDatabase(applicationContext)
        val old = db.getCase(caseId)
        if (old == null) {
            Toast.makeText(this, "Local case not found.", Toast.LENGTH_SHORT).show()
            return
        }
        val merged = old.copy(
            currentCaseNumber = data.caseNumber.ifBlank { old.currentCaseNumber },
            cnrNumber = data.cnrNumber.ifBlank { old.cnrNumber },
            source = "eCourts",
            court = data.court.ifBlank { old.court },
            state = data.state.ifBlank { old.state },
            district = data.district.ifBlank { old.district },
            caseType = data.caseType.ifBlank { old.caseType },
            petitioner = data.petitioner.ifBlank { old.petitioner },
            respondent = data.respondent.ifBlank { old.respondent },
            advocate = data.advocate.ifBlank { old.advocate },
            status = data.status.ifBlank { old.status },
            filingDate = data.filingDate.ifBlank { old.filingDate },
            registrationDate = data.registrationDate.ifBlank { old.registrationDate },
            nextHearingDate = data.nextHearingDate.ifBlank { old.nextHearingDate },
            notes = old.notes
        )
        db.updateCase(merged)
        Toast.makeText(this, "eCourts details saved to local case.", Toast.LENGTH_LONG).show()
    }

    private inner class PageBridge {
        @JavascriptInterface
        fun onPageText(text: String) {
            runOnUiThread {
                capturedText = text
            }
        }
    }

    @Composable
    private fun EcourtsScreen(
        caseId: Long,
        cnr: String,
        pageReady: Boolean,
        capturedText: String,
        parsed: EcourtsCaseData,
        onBack: () -> Unit,
        onAutoFill: () -> Unit,
        onReadPage: () -> Unit,
        onSave: () -> Unit
    ) {
        Column(Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text("eCourts Update") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, "Reload")
                    }
                }
            )
            Text(
                "CNR: ${cnr.ifBlank { "Not available" }}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onAutoFill, enabled = pageReady && cnr.isNotBlank()) {
                    Text("Auto-fill CNR")
                }
                OutlinedButton(onClick = onReadPage, enabled = pageReady) {
                    Text("Read Result")
                }
            }
            Spacer(Modifier.height(8.dp))
            if (parsed != EcourtsCaseData()) {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("Detected eCourts Details", style = MaterialTheme.typography.titleMedium)
                        Text("Case No.: ${parsed.caseNumber.ifBlank { "—" }}")
                        Text("Court: ${parsed.court.ifBlank { "—" }}")
                        Text("Status: ${parsed.status.ifBlank { "—" }}")
                        Text("Next hearing: ${parsed.nextHearingDate.ifBlank { "—" }}")
                        Spacer(Modifier.height(8.dp))
                        Button(onClick = onSave) {
                            Icon(Icons.Default.Save, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Save to Local Case")
                        }
                    }
                }
            }
            AndroidView(
                factory = {
                    createWebView().also { webView = it }
                },
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
            if (capturedText.isNotBlank()) {
                Text(
                    "Page captured. Review the detected fields above before saving.",
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }

    override fun onDestroy() {
        webView?.apply {
            removeJavascriptInterface("LegalCaseManager")
            stopLoading()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CASE_ID = "case_id"
        const val EXTRA_CNR = "cnr"
    }
}

private object EcourtsJs {
    fun autoFillCnr(cnr: String): String {
        val safe = org.json.JSONObject.quote(cnr)
        return """
            (function(){
              var value=$safe, inputs=[].slice.call(document.querySelectorAll('input'));
              var hit=null;
              inputs.forEach(function(i){
                var meta=((i.name||'')+' '+(i.id||'')+' '+(i.placeholder||'')+' '+(i.getAttribute('aria-label')||'')).toLowerCase();
                if(!hit && meta.indexOf('cnr')>=0) hit=i;
              });
              if(!hit){
                hit=inputs.find(function(i){
                  var t=(i.type||'').toLowerCase();
                  return t==='text' || t==='';
                });
              }
              if(hit){
                var setter=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value').set;
                setter.call(hit,value);
                hit.dispatchEvent(new Event('input',{bubbles:true}));
                hit.dispatchEvent(new Event('change',{bubbles:true}));
                hit.focus();
                return true;
              }
              return false;
            })()
        """.trimIndent()
    }
}
