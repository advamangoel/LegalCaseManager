package com.legalcasemanager

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

class CaseDatabase(context: Context) :
    SQLiteOpenHelper(context, "legal_cases.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE cases (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                current_case_number TEXT,
                old_case_numbers TEXT,
                cnr_number TEXT,
                ejagriti_reference TEXT,
                source TEXT,
                court TEXT,
                state TEXT,
                district TEXT,
                case_type TEXT,
                petitioner TEXT,
                respondent TEXT,
                advocate TEXT,
                status TEXT,
                priority TEXT,
                filing_date TEXT,
                registration_date TEXT,
                next_hearing_date TEXT,
                notes TEXT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE hearings (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                case_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                purpose TEXT,
                remarks TEXT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE tasks (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                case_id INTEGER NOT NULL,
                title TEXT NOT NULL,
                due_date TEXT,
                priority TEXT,
                completed INTEGER DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE documents (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                case_id INTEGER NOT NULL,
                name TEXT NOT NULL,
                uri TEXT NOT NULL,
                document_type TEXT
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                case_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                type TEXT NOT NULL,
                description TEXT NOT NULL
            )
        """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Phase 1 schema. Future versions will use explicit migrations.
    }

    fun insertCase(c: CaseRecord): Long {
        val values = caseValues(c)
        return writableDatabase.insert("cases", null, values)
    }

    fun updateCase(c: CaseRecord): Int {
        return writableDatabase.update(
            "cases", caseValues(c), "id=?", arrayOf(c.id.toString())
        )
    }

    fun deleteCase(id: Long) {
        writableDatabase.delete("cases", "id=?", arrayOf(id.toString()))
        writableDatabase.delete("hearings", "case_id=?", arrayOf(id.toString()))
        writableDatabase.delete("tasks", "case_id=?", arrayOf(id.toString()))
        writableDatabase.delete("documents", "case_id=?", arrayOf(id.toString()))
        writableDatabase.delete("events", "case_id=?", arrayOf(id.toString()))
    }

    fun getAllCases(): List<CaseRecord> {
        val list = mutableListOf<CaseRecord>()
        readableDatabase.query("cases", null, null, null, null, null, "id DESC").use { c ->
            while (c.moveToNext()) list += cursorToCase(c)
        }
        return list
    }

    fun searchCases(query: String): List<CaseRecord> {
        val q = "%${query.trim()}%"
        val sql = """
            SELECT * FROM cases
            WHERE title LIKE ?
               OR current_case_number LIKE ?
               OR old_case_numbers LIKE ?
               OR cnr_number LIKE ?
               OR ejagriti_reference LIKE ?
               OR petitioner LIKE ?
               OR respondent LIKE ?
               OR advocate LIKE ?
               OR court LIKE ?
               OR district LIKE ?
            ORDER BY id DESC
        """.trimIndent()
        val args = Array(10) { q }
        val list = mutableListOf<CaseRecord>()
        readableDatabase.rawQuery(sql, args).use { c ->
            while (c.moveToNext()) list += cursorToCase(c)
        }
        return list
    }

    fun getCase(id: Long): CaseRecord? {
        readableDatabase.query(
            "cases", null, "id=?", arrayOf(id.toString()), null, null, null
        ).use { c ->
            return if (c.moveToFirst()) cursorToCase(c) else null
        }
    }

    fun count(status: String? = null): Int {
        val cursor = if (status == null) {
            readableDatabase.rawQuery("SELECT COUNT(*) FROM cases", null)
        } else {
            readableDatabase.rawQuery(
                "SELECT COUNT(*) FROM cases WHERE status=?",
                arrayOf(status)
            )
        }
        cursor.use {
            return if (it.moveToFirst()) it.getInt(0) else 0
        }
    }

    private fun caseValues(c: CaseRecord) = ContentValues().apply {
        put("title", c.title)
        put("current_case_number", c.currentCaseNumber)
        put("old_case_numbers", c.oldCaseNumbers.joinToString("\n"))
        put("cnr_number", CnrValidator.normalize(c.cnrNumber))
        put("ejagriti_reference", c.eJagritiReference)
        put("source", c.source)
        put("court", c.court)
        put("state", c.state)
        put("district", c.district)
        put("case_type", c.caseType)
        put("petitioner", c.petitioner)
        put("respondent", c.respondent)
        put("advocate", c.advocate)
        put("status", c.status)
        put("priority", c.priority)
        put("filing_date", c.filingDate)
        put("registration_date", c.registrationDate)
        put("next_hearing_date", c.nextHearingDate)
        put("notes", c.notes)
    }

    private fun cursorToCase(c: android.database.Cursor): CaseRecord {
        fun s(name: String) = c.getString(c.getColumnIndexOrThrow(name)) ?: ""
        return CaseRecord(
            id = c.getLong(c.getColumnIndexOrThrow("id")),
            title = s("title"),
            currentCaseNumber = s("current_case_number"),
            oldCaseNumbers = s("old_case_numbers").split("\n").filter { it.isNotBlank() },
            cnrNumber = s("cnr_number"),
            eJagritiReference = s("ejagriti_reference"),
            source = s("source"),
            court = s("court"),
            state = s("state"),
            district = s("district"),
            caseType = s("case_type"),
            petitioner = s("petitioner"),
            respondent = s("respondent"),
            advocate = s("advocate"),
            status = s("status"),
            priority = s("priority"),
            filingDate = s("filing_date"),
            registrationDate = s("registration_date"),
            nextHearingDate = s("next_hearing_date"),
            notes = s("notes")
        )
    }

    fun addHearing(h: HearingRecord) {
        writableDatabase.insert("hearings", null, ContentValues().apply {
            put("case_id", h.caseId)
            put("date", h.date)
            put("purpose", h.purpose)
            put("remarks", h.remarks)
        })
    }

    fun getHearings(caseId: Long): List<HearingRecord> {
        val list = mutableListOf<HearingRecord>()
        readableDatabase.query("hearings", null, "case_id=?", arrayOf(caseId.toString()),
            null, null, "date ASC").use { c ->
            while (c.moveToNext()) {
                list += HearingRecord(
                    c.getLong(c.getColumnIndexOrThrow("id")),
                    caseId,
                    c.getString(c.getColumnIndexOrThrow("date")) ?: "",
                    c.getString(c.getColumnIndexOrThrow("purpose")) ?: "",
                    c.getString(c.getColumnIndexOrThrow("remarks")) ?: ""
                )
            }
        }
        return list
    }

    fun addTask(t: TaskRecord) {
        writableDatabase.insert("tasks", null, ContentValues().apply {
            put("case_id", t.caseId)
            put("title", t.title)
            put("due_date", t.dueDate)
            put("priority", t.priority)
            put("completed", if (t.completed) 1 else 0)
        })
    }

    fun getTasks(caseId: Long): List<TaskRecord> {
        val list = mutableListOf<TaskRecord>()
        readableDatabase.query("tasks", null, "case_id=?", arrayOf(caseId.toString()),
            null, null, "completed ASC, due_date ASC").use { c ->
            while (c.moveToNext()) {
                list += TaskRecord(
                    c.getLong(c.getColumnIndexOrThrow("id")),
                    caseId,
                    c.getString(c.getColumnIndexOrThrow("title")) ?: "",
                    c.getString(c.getColumnIndexOrThrow("due_date")) ?: "",
                    c.getString(c.getColumnIndexOrThrow("priority")) ?: "Normal",
                    c.getInt(c.getColumnIndexOrThrow("completed")) == 1
                )
            }
        }
        return list
    }

    fun addDocument(d: DocumentRecord) {
        writableDatabase.insert("documents", null, ContentValues().apply {
            put("case_id", d.caseId)
            put("name", d.name)
            put("uri", d.uri)
            put("document_type", d.documentType)
        })
    }

    fun getDocuments(caseId: Long): List<DocumentRecord> {
        val list = mutableListOf<DocumentRecord>()
        readableDatabase.query("documents", null, "case_id=?", arrayOf(caseId.toString()),
            null, null, "id DESC").use { c ->
            while (c.moveToNext()) {
                list += DocumentRecord(
                    c.getLong(c.getColumnIndexOrThrow("id")),
                    caseId,
                    c.getString(c.getColumnIndexOrThrow("name")) ?: "",
                    c.getString(c.getColumnIndexOrThrow("uri")) ?: "",
                    c.getString(c.getColumnIndexOrThrow("document_type")) ?: "Other"
                )
            }
        }
        return list
    }

    fun addEvent(e: CaseEventRecord) {
        writableDatabase.insert("events", null, ContentValues().apply {
            put("case_id", e.caseId)
            put("date", e.date)
            put("type", e.type)
            put("description", e.description)
        })
    }

    fun getEvents(caseId: Long): List<CaseEventRecord> {
        val list = mutableListOf<CaseEventRecord>()
        readableDatabase.query("events", null, "case_id=?", arrayOf(caseId.toString()),
            null, null, "date DESC").use { c ->
            while (c.moveToNext()) {
                list += CaseEventRecord(
                    c.getLong(c.getColumnIndexOrThrow("id")),
                    caseId,
                    c.getString(c.getColumnIndexOrThrow("date")) ?: "",
                    c.getString(c.getColumnIndexOrThrow("type")) ?: "",
                    c.getString(c.getColumnIndexOrThrow("description")) ?: ""
                )
            }
        }
        return list
    }

    fun exportJson(): String {
        val root = JSONObject()
        val cases = JSONArray()
        getAllCases().forEach { c ->
            cases.put(JSONObject().apply {
                put("id", c.id)
                put("title", c.title)
                put("currentCaseNumber", c.currentCaseNumber)
                put("oldCaseNumbers", JSONArray(c.oldCaseNumbers))
                put("cnrNumber", c.cnrNumber)
                put("eJagritiReference", c.eJagritiReference)
                put("source", c.source)
                put("court", c.court)
                put("state", c.state)
                put("district", c.district)
                put("caseType", c.caseType)
                put("petitioner", c.petitioner)
                put("respondent", c.respondent)
                put("advocate", c.advocate)
                put("status", c.status)
                put("priority", c.priority)
                put("filingDate", c.filingDate)
                put("registrationDate", c.registrationDate)
                put("nextHearingDate", c.nextHearingDate)
                put("notes", c.notes)
            })
        }
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())
        root.put("cases", cases)
        return root.toString(2)
    }
}
