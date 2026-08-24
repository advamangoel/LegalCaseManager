package com.legalcasemanager

data class EJagritiData(
    val referenceNumber: String = "",
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
    val nextHearingDate: String = "",
    val historyText: String = ""
)

object EJagritiParser {

    fun parse(text: String, referenceNumber: String): EJagritiData {
        val lines = text
            .replace('\u00A0', ' ')
            .replace("\r", "")
            .lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }

        fun valueAfter(vararg labels: String): String {
            for (i in lines.indices) {
                val line = lines[i]

                val matchedLabel = labels.firstOrNull { label ->
                    line.equals(label, ignoreCase = true) ||
                        line.startsWith("$label:", ignoreCase = true)
                }

                if (matchedLabel != null) {
                    val sameLine = line.substringAfter(":", "").trim()
                    if (sameLine.isNotBlank()) return sameLine

                    if (i + 1 < lines.size) {
                        val nextLine = lines[i + 1].trim()
                        if (nextLine.isNotBlank()) return nextLine
                    }
                }
            }
            return ""
        }

        return EJagritiData(
            referenceNumber = referenceNumber,
            caseNumber = valueAfter(
                "Case Number",
                "Case No.",
                "Case No",
                "Case Number / Diary Number"
            ),
            court = valueAfter(
                "Commission Name",
                "Commission",
                "Court",
                "Court Name"
            ),
            state = valueAfter(
                "State",
                "State Commission"
            ),
            district = valueAfter(
                "District",
                "District Commission"
            ),
            caseType = valueAfter(
                "Case Type",
                "Type of Case",
                "Complaint Type"
            ),
            petitioner = valueAfter(
                "Complainant",
                "Petitioner",
                "Applicant"
            ),
            respondent = valueAfter(
                "Opposite Party",
                "Respondent",
                "Opposite Parties"
            ),
            advocate = valueAfter(
                "Advocate",
                "Advocate Name",
                "Counsel"
            ),
            status = valueAfter(
                "Case Status",
                "Status"
            ),
            filingDate = valueAfter(
                "Date of Filing",
                "Filing Date"
            ),
            registrationDate = valueAfter(
                "Registration Date",
                "Date of Registration"
            ),
            nextHearingDate = valueAfter(
                "Next Hearing Date",
                "Next Hearing",
                "Date of Next Hearing"
            ),
            historyText = lines.joinToString("\n")
        )
    }
}
