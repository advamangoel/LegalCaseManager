package com.legalcasemanager

data class CaseRecord(
    val id: Long = 0,
    val title: String,
    val currentCaseNumber: String = "",
    val oldCaseNumbers: List<String> = emptyList(),
    val cnrNumber: String = "",
    val eJagritiReference: String = "",
    val source: String = "Manual",
    val court: String = "",
    val state: String = "",
    val district: String = "",
    val caseType: String = "",
    val petitioner: String = "",
    val respondent: String = "",
    val advocate: String = "",
    val status: String = "Pending",
    val priority: String = "Normal",
    val filingDate: String = "",
    val registrationDate: String = "",
    val nextHearingDate: String = "",
    val notes: String = ""
)

data class HearingRecord(
    val id: Long = 0,
    val caseId: Long,
    val date: String,
    val purpose: String = "",
    val remarks: String = ""
)

data class TaskRecord(
    val id: Long = 0,
    val caseId: Long,
    val title: String,
    val dueDate: String = "",
    val priority: String = "Normal",
    val completed: Boolean = false
)

data class DocumentRecord(
    val id: Long = 0,
    val caseId: Long,
    val name: String,
    val uri: String,
    val documentType: String = "Other"
)

data class CaseEventRecord(
    val id: Long = 0,
    val caseId: Long,
    val date: String,
    val type: String,
    val description: String
)
