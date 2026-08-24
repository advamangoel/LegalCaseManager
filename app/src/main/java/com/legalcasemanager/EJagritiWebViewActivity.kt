package com.legalcasemanager

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast

class EJagritiWebViewActivity : Activity() {

    companion object {
        const val EXTRA_CASE_ID = "case_id"
        const val EXTRA_REFERENCE = "ejagriti_reference"

        private const val URL =
            "https://e-jagriti.gov.in/case-history-case-status"

        private const val POLL_INTERVAL_MS = 1500L
        private const val MAX_POLL_ATTEMPTS = 40
    }

    private lateinit var webView: WebView
    private lateinit var db: CaseDatabase

    private var caseId: Long = 0L
    private var referenceNumber = ""
    private var pollAttempts = 0

    private val handler = Handler(Looper.getMainLooper())

    private val resultPoller = object : Runnable {
        override fun run() {
            if (isFinishing || isDestroyed) return

            inspectPage()

            if (pollAttempts < MAX_POLL_ATTEMPTS) {
                pollAttempts++
                handler.postDelayed(this, POLL_INTERVAL_MS)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        caseId = intent.getLongExtra(EXTRA_CASE_ID, 0L)
        referenceNumber =
            intent.getStringExtra(EXTRA_REFERENCE)?.trim().orEmpty()

        db = CaseDatabase(applicationContext)

        webView = WebView(this)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            cacheMode = WebSettings.LOAD_DEFAULT
            loadsImagesAutomatically = true
            javaScriptCanOpenWindowsAutomatically = true
        }

        webView.webChromeClient = WebChromeClient()

        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(
                view: WebView?,
                url: String?
            ) {
                super.onPageFinished(view, url)

                // Fill the reference number automatically.
                // CAPTCHA is intentionally left for the user.
                fillReferenceNumber()

                // The e-Jagriti page may populate its result dynamically,
                // so inspect it repeatedly for a limited period.
                pollAttempts = 0
                handler.removeCallbacks(resultPoller)
                handler.post(resultPoller)
            }
        }

        setContentView(webView)
        webView.loadUrl(URL)
    }

    private fun fillReferenceNumber() {
        if (referenceNumber.isBlank()) return

        val escaped = referenceNumber
            .replace("\\", "\\\\")
            .replace("'", "\\'")

        val js = """
            (function() {
                const value = '$escaped';
                const inputs = Array.from(document.querySelectorAll('input'));

                for (const input of inputs) {
                    const text = (
                        (input.getAttribute('placeholder') || '') + ' ' +
                        (input.getAttribute('name') || '') + ' ' +
                        (input.getAttribute('id') || '') + ' ' +
                        (input.getAttribute('aria-label') || '')
                    ).toLowerCase();

                    if (
                        text.includes('case number') ||
                        text.includes('reference number') ||
                        text.includes('filing reference') ||
                        text.includes('e-daakhil') ||
                        text.includes('reference')
                    ) {
                        const descriptor =
                            Object.getOwnPropertyDescriptor(
                                HTMLInputElement.prototype,
                                'value'
                            );

                        if (descriptor && descriptor.set) {
                            descriptor.set.call(input, value);
                        } else {
                            input.value = value;
                        }

                        input.dispatchEvent(
                            new Event('input', { bubbles: true })
                        );
                        input.dispatchEvent(
                            new Event('change', { bubbles: true })
                        );

                        return true;
                    }
                }

                return false;
            })();
        """.trimIndent()

        webView.evaluateJavascript(js, null)
    }

    private fun inspectPage() {
        webView.evaluateJavascript(
            """
            (function() {
                return document.body
                    ? document.body.innerText
                    : '';
            })();
            """.trimIndent()
        ) { result ->

            if (result.isNullOrBlank()) return@evaluateJavascript

            val text = decodeJavascriptString(result)

            // Do not attempt to bypass CAPTCHA.
            // We only parse information once the result page/content
            // is actually available.
            val looksLikeCaseResult =
                text.contains("Case History", ignoreCase = true) ||
                    text.contains("Case Status", ignoreCase = true) ||
                    text.contains("Case Number", ignoreCase = true) &&
                    (
                        text.contains("Complainant", ignoreCase = true) ||
                            text.contains("Opposite Party", ignoreCase = true) ||
                            text.contains("Petitioner", ignoreCase = true)
                    )

            if (looksLikeCaseResult) {
                updateLocalCase(
                    EJagritiParser.parse(
                        text = text,
                        referenceNumber = referenceNumber
                    )
                )

                handler.removeCallbacks(resultPoller)
            }
        }
    }

    private fun decodeJavascriptString(value: String): String {
        var text = value

        if (text.length >= 2 &&
            text.first() == '"' &&
            text.last() == '"'
        ) {
            text = text.substring(1, text.length - 1)
        }

        return text
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private fun updateLocalCase(data: EJagritiData) {
        val existing = db.getCase(caseId) ?: return

        val hasUsefulData =
            data.caseNumber.isNotBlank() ||
                data.court.isNotBlank() ||
                data.state.isNotBlank() ||
                data.district.isNotBlank() ||
                data.caseType.isNotBlank() ||
                data.petitioner.isNotBlank() ||
                data.respondent.isNotBlank() ||
                data.advocate.isNotBlank() ||
                data.status.isNotBlank() ||
                data.filingDate.isNotBlank() ||
                data.registrationDate.isNotBlank() ||
                data.nextHearingDate.isNotBlank()

        if (!hasUsefulData) return

        val updated = existing.copy(
            eJagritiReference = data.referenceNumber
                .ifBlank { existing.eJagritiReference },

            currentCaseNumber = data.caseNumber
                .ifBlank { existing.currentCaseNumber },

            court = data.court
                .ifBlank { existing.court },

            state = data.state
                .ifBlank { existing.state },

            district = data.district
                .ifBlank { existing.district },

            caseType = data.caseType
                .ifBlank { existing.caseType },

            petitioner = data.petitioner
                .ifBlank { existing.petitioner },

            respondent = data.respondent
                .ifBlank { existing.respondent },

            advocate = data.advocate
                .ifBlank { existing.advocate },

            status = data.status
                .ifBlank { existing.status },

            filingDate = data.filingDate
                .ifBlank { existing.filingDate },

            registrationDate = data.registrationDate
                .ifBlank { existing.registrationDate },

            nextHearingDate = data.nextHearingDate
                .ifBlank { existing.nextHearingDate },

            source = "e-Jagriti"
        )

        db.updateCase(updated)

        Toast.makeText(
            this,
            "Case information updated from e-Jagriti",
            Toast.LENGTH_LONG
        ).show()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
