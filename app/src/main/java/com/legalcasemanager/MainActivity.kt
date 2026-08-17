package com.legalcasemanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                LegalCaseManagerApp()
            }
        }
    }
}

@Composable
fun LegalCaseManagerApp() {
    val context = LocalContext.current
    val db = remember { CaseDatabase(context.applicationContext) }
    var screen by remember { mutableStateOf("home") }
    var selectedCaseId by remember { mutableLongStateOf(0L) }
    var refresh by remember { mutableIntStateOf(0) }

    fun reload() { refresh++ }

    when (screen) {
        "home" -> HomeScreen(
            db = db,
            refresh = refresh,
            onCases = { screen = "cases" },
            onAdd = { selectedCaseId = 0; screen = "edit" }
        )
        "cases" -> CaseListScreen(
            db = db,
            refresh = refresh,
            onBack = { screen = "home" },
            onAdd = { selectedCaseId = 0; screen = "edit" },
            onOpen = { selectedCaseId = it; screen = "detail" }
        )
        "edit" -> CaseEditScreen(
            db = db,
            caseId = selectedCaseId,
            onBack = { screen = if (selectedCaseId == 0L) "cases" else "detail" },
            onSaved = { id ->
                selectedCaseId = id
                screen = "detail"
                reload()
            }
        )
        "detail" -> CaseDetailScreen(
            db = db,
            caseId = selectedCaseId,
            onBack = { screen = "cases" },
            onEdit = { screen = "edit" },
            onDeleted = { screen = "cases"; reload() }
        )
    }
}

@Composable
fun HomeScreen(db: CaseDatabase, refresh: Int, onCases: () -> Unit, onAdd: () -> Unit) {
    val total = remember(refresh) { db.count() }
    val pending = remember(refresh) { db.count("Pending") }
    val disposed = remember(refresh) { db.count("Disposed") }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Legal Case Manager") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add case") }
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            Text("Your local litigation workspace", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Total", total, Modifier.weight(1f))
                StatCard("Pending", pending, Modifier.weight(1f))
                StatCard("Disposed", disposed, Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onCases, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Folder, null)
                Spacer(Modifier.width(8.dp))
                Text("Open My Cases")
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Phase 1 is fully local. Case data and metadata are stored on this device.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "CNR validation checks the 16-character alphanumeric format offline. It does not confirm that a CNR exists in eCourts until Phase 2.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun StatCard(label: String, value: Int, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
fun CaseListScreen(
    db: CaseDatabase,
    refresh: Int,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val cases = remember(query, refresh) {
        if (query.isBlank()) db.getAllCases() else db.searchCases(query)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Cases") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = onAdd) { Icon(Icons.Default.Add, "Add") }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(12.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search case no., CNR, party, advocate, court...") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                singleLine = true
            )
            Spacer(Modifier.height(10.dp))
            if (cases.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No cases found.")
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(cases, key = { it.id }) { c ->
                        Card(onClick = { onOpen(c.id) }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(c.title, fontWeight = FontWeight.Bold)
                                if (c.currentCaseNumber.isNotBlank()) Text(c.currentCaseNumber)
                                if (c.cnrNumber.isNotBlank()) Text("CNR: ${c.cnrNumber}", style = MaterialTheme.typography.bodySmall)
                                Text("${c.status} • ${c.court.ifBlank { "Court not specified" }}", style = MaterialTheme.typography.bodySmall)
                                if (c.nextHearingDate.isNotBlank()) Text("Next hearing: ${c.nextHearingDate}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CaseEditScreen(
    db: CaseDatabase,
    caseId: Long,
    onBack: () -> Unit,
    onSaved: (Long) -> Unit
) {
    val existing = remember(caseId) { if (caseId == 0L) null else db.getCase(caseId) }
    var title by remember { mutableStateOf(existing?.title ?: "") }
    var currentNo by remember { mutableStateOf(existing?.currentCaseNumber ?: "") }
    var oldNumbers by remember { mutableStateOf(existing?.oldCaseNumbers?.joinToString("\n") ?: "") }
    var cnr by remember { mutableStateOf(existing?.cnrNumber ?: "") }
    var ref by remember { mutableStateOf(existing?.eJagritiReference ?: "") }
    var source by remember { mutableStateOf(existing?.source ?: "Manual") }
    var court by remember { mutableStateOf(existing?.court ?: "") }
    var state by remember { mutableStateOf(existing?.state ?: "") }
    var district by remember { mutableStateOf(existing?.district ?: "") }
    var type by remember { mutableStateOf(existing?.caseType ?: "") }
    var petitioner by remember { mutableStateOf(existing?.petitioner ?: "") }
    var respondent by remember { mutableStateOf(existing?.respondent ?: "") }
    var advocate by remember { mutableStateOf(existing?.advocate ?: "") }
    var status by remember { mutableStateOf(existing?.status ?: "Pending") }
    var priority by remember { mutableStateOf(existing?.priority ?: "Normal") }
    var filingDate by remember { mutableStateOf(existing?.filingDate ?: "") }
    var registrationDate by remember { mutableStateOf(existing?.registrationDate ?: "") }
    var nextHearing by remember { mutableStateOf(existing?.nextHearingDate ?: "") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    val scroll = rememberScrollState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (caseId == 0L) "Add Case" else "Edit Case") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Field("Case title *", title) { title = it }
            Field("Current / New Case Number", currentNo) { currentNo = it }
            Field("Old / Previous Case Numbers (one per line)", oldNumbers, minLines = 2) { oldNumbers = it }
            Field("CNR Number", cnr, isError = cnr.isNotBlank() && !CnrValidator.isValid(cnr)) { cnr = it.uppercase() }
            Text(
                CnrValidator.message(cnr),
                color = if (cnr.isBlank() || CnrValidator.isValid(cnr))
                    MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
            Field("e-Jagriti Reference Number", ref) { ref = it }
            Field("Source (Manual / eCourts / e-Jagriti)", source) { source = it }
            Field("Court / Commission", court) { court = it }
            Field("State", state) { state = it }
            Field("District", district) { district = it }
            Field("Case Type", type) { type = it }
            Field("Petitioner / Complainant", petitioner) { petitioner = it }
            Field("Respondent / Opposite Party", respondent) { respondent = it }
            Field("Advocate", advocate) { advocate = it }
            Field("Status (Pending / Disposed / etc.)", status) { status = it }
            Field("Priority (Low / Normal / High)", priority) { priority = it }
            Field("Filing Date (DD-MM-YYYY)", filingDate) { filingDate = it }
            Field("Registration Date (DD-MM-YYYY)", registrationDate) { registrationDate = it }
            Field("Next Hearing Date (DD-MM-YYYY)", nextHearing) { nextHearing = it }
            Field("Notes", notes, minLines = 4) { notes = it }

            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    if (cnr.isNotBlank() && !CnrValidator.isValid(cnr)) return@Button
                    val record = CaseRecord(
                        id = caseId,
                        title = title.trim(),
                        currentCaseNumber = currentNo.trim(),
                        oldCaseNumbers = oldNumbers.lines().map { it.trim() }.filter { it.isNotBlank() },
                        cnrNumber = CnrValidator.normalize(cnr),
                        eJagritiReference = ref.trim(),
                        source = source.trim().ifBlank { "Manual" },
                        court = court.trim(),
                        state = state.trim(),
                        district = district.trim(),
                        caseType = type.trim(),
                        petitioner = petitioner.trim(),
                        respondent = respondent.trim(),
                        advocate = advocate.trim(),
                        status = status.trim().ifBlank { "Pending" },
                        priority = priority.trim().ifBlank { "Normal" },
                        filingDate = filingDate.trim(),
                        registrationDate = registrationDate.trim(),
                        nextHearingDate = nextHearing.trim(),
                        notes = notes.trim()
                    )
                    val id = if (caseId == 0L) db.insertCase(record) else {
                        db.updateCase(record)
                        caseId
                    }
                    onSaved(id)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Save Case")
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
fun Field(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    minLines: Int = 1,
    isError: Boolean = false,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        minLines = minLines,
        isError = isError
    )
}

@Composable
fun CaseDetailScreen(
    db: CaseDatabase,
    caseId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    var refresh by remember { mutableIntStateOf(0) }
    val c = remember(caseId, refresh) { db.getCase(caseId) }
    val hearings = remember(caseId, refresh) { db.getHearings(caseId) }
    val tasks = remember(caseId, refresh) { db.getTasks(caseId) }
    val docs = remember(caseId, refresh) { db.getDocuments(caseId) }
    val events = remember(caseId, refresh) { db.getEvents(caseId) }

    val documentPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && c != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {}
            db.addDocument(
                DocumentRecord(
                    caseId = c.id,
                    name = uri.lastPathSegment ?: "Document",
                    uri = uri.toString()
                )
            )
            refresh++
        }
    }

    if (c == null) {
        onBack()
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(c.title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } },
                actions = {
                    IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
                    IconButton(onClick = {
                        db.deleteCase(c.id)
                        onDeleted()
                    }) { Icon(Icons.Default.Delete, "Delete") }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DetailSection("Identifiers") {
                DetailRow("Current case number", c.currentCaseNumber)
                DetailRow("Old / previous numbers", c.oldCaseNumbers.joinToString("\n"))
                DetailRow("CNR", c.cnrNumber)
                DetailRow("e-Jagriti reference", c.eJagritiReference)
                DetailRow("Source", c.source)
            }
            DetailSection("Court & Parties") {
                DetailRow("Court / Commission", c.court)
                DetailRow("State / District", "${c.state} / ${c.district}")
                DetailRow("Case type", c.caseType)
                DetailRow("Petitioner / Complainant", c.petitioner)
                DetailRow("Respondent / Opposite Party", c.respondent)
                DetailRow("Advocate", c.advocate)
                DetailRow("Status", c.status)
                DetailRow("Priority", c.priority)
            }
            DetailSection("Dates") {
                DetailRow("Filing date", c.filingDate)
                DetailRow("Registration date", c.registrationDate)
                DetailRow("Next hearing", c.nextHearingDate)
            }
            DetailSection("Notes") { Text(c.notes.ifBlank { "No notes." }) }

            DetailSection("Hearings (${hearings.size})") {
                hearings.forEach { h ->
                    Text("${h.date} • ${h.purpose}", fontWeight = FontWeight.SemiBold)
                    if (h.remarks.isNotBlank()) Text(h.remarks, style = MaterialTheme.typography.bodySmall)
                    HorizontalDivider()
                }
                Button(onClick = {
                    val date = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date())
                    db.addHearing(HearingRecord(caseId = c.id, date = date, purpose = "Recorded locally"))
                    refresh++
                }) { Text("Add current date as hearing") }
            }

            DetailSection("Documents (${docs.size})") {
                Button(onClick = { documentPicker.launch(arrayOf("application/pdf", "image/*", "text/plain")) }) {
                    Icon(Icons.Default.AttachFile, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Attach Document")
                }
                docs.forEach { d ->
                    TextButton(onClick = {
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(Uri.parse(d.uri), "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        try { context.startActivity(intent) } catch (_: Exception) {}
                    }) { Text(d.name) }
                }
            }

            DetailSection("Tasks (${tasks.size})") {
                if (tasks.isEmpty()) Text("No tasks.")
                tasks.forEach { t ->
                    Text("${if (t.completed) "✓" else "○"} ${t.title} ${t.dueDate}")
                }
                Button(onClick = {
                    db.addTask(TaskRecord(caseId = c.id, title = "New local task"))
                    refresh++
                }) { Text("Add task") }
            }

            DetailSection("Timeline (${events.size})") {
                if (events.isEmpty()) Text("No timeline events recorded yet.")
                events.forEach { e ->
                    Text("${e.date} • ${e.type}", fontWeight = FontWeight.SemiBold)
                    Text(e.description)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    if (value.isNotBlank()) {
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
