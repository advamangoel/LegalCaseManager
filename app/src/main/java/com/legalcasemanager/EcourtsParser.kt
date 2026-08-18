package com.legalcasemanager

/**
 * Lightweight parser for the text rendered by the official eCourts page.
 *
 * It deliberately parses only the information visible in the page DOM after
 * the user completes any site-required challenge (such as CAPTCHA).
 */
data class EcourtsCaseData(
    val cnrNumber: String = "",
    val caseNumber: String = "",
    val court: String = "",
    val state: String = "",
    val district: String = "",
    val caseType: String = "",
    val petitioner: String = "",
    val respondent: String = "",
    val advocate: String = "",
    val status: String = "",
    val filingDate: String = "",
    val registrationDate: String = "",
    val nextHearingDate: String = ""
)

object EcourtsParser {
    fun parse(text: String, fallbackCnr: String = ""): EcourtsCaseData {
        val normalized = text
            .replace('\u00A0', ' ')
            .replace("\r", "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        fun valueFor(vararg labels: String): String {
            for (i in normalized.indices) {
                val line = normalized[i]
                for (label in labels) {
                    if (line.equals(label, ignoreCase = true) && i + 1 < normalized.size) {
                        return normalized[i + 1].trim()
                    }
                    val m = Regex(
                        "^${Regex.escape(label)}\\s*[:\\-]\\s*(.+)$",
                        RegexOption.IGNORE_CASE
                    ).find(line)
                    if (m != null) return m.groupValues[1].trim()
                }
            }
            return ""
        }

        fun firstMatching(regexes: List<Regex>): String {
            normalized.forEach { line ->
                regexes.forEach { r ->
                    val m = r.find(line)
                    if (m != null && m.groupValues.size > 1) return m.groupValues[1].trim()
                }
            }
            return ""
        }

        val cnr = firstMatching(
            listOf(
                Regex("""\b([A-Z]{4}\d{12})\b""", RegexOption.IGNORE_CASE),
                Regex("""CNR\s*(?:Number|No\.?)?\s*[:\-]?\s*([A-Z0-9]{16})""", RegexOption.IGNORE_CASE)
            )
        ).ifBlank { fallbackCnr }

        return EcourtsCaseData(
            cnrNumber = cnr,
            caseNumber = valueFor("Case Number", "Case No.", "Registration Number"),
            court = valueFor("Court Name", "Court", "Name of Court"),
            state = valueFor("State"),
            district = valueFor("District"),
            caseType = valueFor("Case Type"),
            petitioner = valueFor("Petitioner", "Petitioner Name", "Complainant"),
            respondent = valueFor("Respondent", "Respondent Name", "Opposite Party"),
            advocate = valueFor("Advocate", "Advocate Name"),
            status = valueFor("Case Status", "Status"),
            filingDate = valueFor("Filing Date", "Date of Filing"),
            registrationDate = valueFor("Registration Date", "Date of Registration"),
            nextHearingDate = valueFor(
                "Next Hearing Date",
                "Next Date",
                "Date of Next Hearing"
            )
        )
    }
}
