package com.example.toxicbase.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.NavigateBefore
import androidx.compose.material.icons.automirrored.filled.NavigateNext
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.toxicbase.data.CollectionIndexEntity
import com.example.toxicbase.sdk.ToxicDocument
import com.example.toxicbase.sdk.ToxicQueryPage
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.ToxicGreen

@Composable
fun DatabaseExplorerScreen(
    collections: List<String>,
    selectedCollection: String,
    queryPage: ToxicQueryPage?,
    selectedDocument: ToxicDocument?,
    indexes: List<CollectionIndexEntity>,
    onSelectCollection: (String) -> Unit,
    onCreateDocument: (String, String?, String) -> Unit,
    onSelectDocument: (String, String) -> Unit,
    onUpdateDocument: (String, String, String, Boolean) -> Unit,
    onDeleteDocument: (String, String) -> Unit,
    onRunQuery: (String?, String, String?, String?, String, Int, Int) -> Unit,
    onCreateIndex: (String, String, String) -> Unit,
    onDeleteIndex: (String) -> Unit
) {
    var dbModeTab by remember { mutableIntStateOf(0) } // 0 = Browse & Query, 1 = Create Doc, 2 = Indexes

    // Create Document state
    var newDocCollection by remember(selectedCollection) { mutableStateOf(selectedCollection) }
    var newDocId by remember { mutableStateOf("") }
    var newDocJson by remember {
        mutableStateOf(
            """
            {
              "title": "Edge Computing Node",
              "status": "ONLINE",
              "latencyMs": 14,
              "region": "us-east-1"
            }
            """.trimIndent()
        )
    }

    // Query & Pagination state
    var filterField by remember { mutableStateOf("") }
    var filterOp by remember { mutableStateOf("==") }
    var filterValue by remember { mutableStateOf("") }
    var sortByField by remember { mutableStateOf("") }
    var sortOrder by remember { mutableStateOf("DESC") }
    var currentPage by remember { mutableIntStateOf(1) }
    val pageLimit = 5
    val filterOps = listOf("==", "!=", ">", ">=", "<", "<=", "contains")

    // Edit Selected Document state
    var editDocJson by remember(selectedDocument?.id, selectedDocument?.version) {
        mutableStateOf(selectedDocument?.rawJson ?: "{}")
    }
    var mergeOnUpdate by remember { mutableStateOf(false) }

    // Create Index state
    var idxFieldPath by remember { mutableStateOf("status") }
    var idxSortOrder by remember { mutableStateOf("ASC") }

    val availableCollections = remember(collections, selectedCollection) {
        (collections + listOf(selectedCollection, "users_profile", "orders", "telemetry")).distinct()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "COLLECTIONS (PER-PROJECT ISOLATED)",
                style = MaterialTheme.typography.labelLarge,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                availableCollections.forEach { col ->
                    val isSelected = col == selectedCollection
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            currentPage = 1
                            onSelectCollection(col)
                        },
                        label = { Text(col) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ToxicGreen.copy(alpha = 0.2f),
                            selectedLabelColor = ToxicGreen
                        )
                    )
                }
            }
        }

        // Sub-navigation for Browse/Query, Create Document, and Indexes
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = dbModeTab == 0,
                    onClick = { dbModeTab = 0 },
                    label = { Text("Documents & Query") },
                    leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("db_tab_browse")
                )
                FilterChip(
                    selected = dbModeTab == 1,
                    onClick = { dbModeTab = 1 },
                    label = { Text("Create Document") },
                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("db_tab_create")
                )
                FilterChip(
                    selected = dbModeTab == 2,
                    onClick = { dbModeTab = 2 },
                    label = { Text("Indexes (${indexes.size})") },
                    leadingIcon = { Icon(Icons.Default.Bolt, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("db_tab_indexes")
                )
            }
        }

        // TAB 1: CREATE DOCUMENT OR COLLECTION
        if (dbModeTab == 1) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "CREATE COLLECTION / JSON DOCUMENT",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Calls ToxicBase.database.collection(name).create() -> POST /database/:collection",
                            style = MaterialTheme.typography.bodySmall,
                            color = CyberCyan
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = newDocCollection,
                                onValueChange = { newDocCollection = it },
                                label = { Text("Collection Name") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("create_doc_collection_input")
                            )
                            OutlinedTextField(
                                value = newDocId,
                                onValueChange = { newDocId = it },
                                label = { Text("Doc ID (Optional)") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("create_doc_id_input")
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedTextField(
                            value = newDocJson,
                            onValueChange = { newDocJson = it },
                            label = { Text("Document JSON Payload") },
                            minLines = 5,
                            maxLines = 10,
                            textStyle = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_doc_json_input")
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                onCreateDocument(
                                    newDocCollection,
                                    newDocId.takeIf { it.isNotBlank() },
                                    newDocJson
                                )
                                dbModeTab = 0
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ToxicGreen),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("create_doc_submit_button")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("ToxicBase.database.create()", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // TAB 2: DATABASE INDEXES
        if (dbModeTab == 2) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "CREATE FIELD INDEX ON '$selectedCollection'",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = idxFieldPath,
                                onValueChange = { idxFieldPath = it },
                                label = { Text("JSON Field Path (e.g. role, status)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            FilterChip(
                                selected = idxSortOrder == "ASC",
                                onClick = { idxSortOrder = if (idxSortOrder == "ASC") "DESC" else "ASC" },
                                label = { Text(idxSortOrder) }
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onCreateIndex(selectedCollection, idxFieldPath, idxSortOrder) },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Create Database Index", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            items(indexes, key = { it.id }) { idx ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(12.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "${idx.collectionName}.${idx.fieldPath} (${idx.sortOrder})",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Index ID: ${idx.id} • Status: ${idx.status}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ToxicGreen
                            )
                        }
                        IconButton(onClick = { onDeleteIndex(idx.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Drop index", tint = CrimsonError)
                        }
                    }
                }
            }
        }

        // TAB 0: QUERY BAR, PAGINATION, DOCUMENT LIST & LIVE DOCUMENT EDITOR
        if (dbModeTab == 0) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ObsidianBorder, RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.FilterList, contentDescription = null, tint = CyberCyan)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "QUERY & PAGINATION ENGINE",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                            }
                            IconButton(
                                onClick = {
                                    onRunQuery(
                                        filterField.takeIf { it.isNotBlank() },
                                        filterOp,
                                        filterValue.takeIf { it.isNotBlank() },
                                        sortByField.takeIf { it.isNotBlank() },
                                        sortOrder,
                                        currentPage,
                                        pageLimit
                                    )
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh query", tint = ToxicGreen)
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = filterField,
                                onValueChange = { filterField = it },
                                label = { Text("Field (e.g. role)") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = filterValue,
                                onValueChange = { filterValue = it },
                                label = { Text("Value") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            filterOps.forEach { op ->
                                FilterChip(
                                    selected = filterOp == op,
                                    onClick = { filterOp = op },
                                    label = { Text(op) }
                                )
                            }
                            FilterChip(
                                selected = sortOrder == "DESC",
                                onClick = { sortOrder = if (sortOrder == "DESC") "ASC" else "DESC" },
                                label = { Text("Sort: $sortOrder") }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    currentPage = 1
                                    onRunQuery(
                                        filterField.takeIf { it.isNotBlank() },
                                        filterOp,
                                        filterValue.takeIf { it.isNotBlank() },
                                        sortByField.takeIf { it.isNotBlank() },
                                        sortOrder,
                                        1,
                                        pageLimit
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Run Query", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            OutlinedButton(
                                onClick = {
                                    filterField = ""
                                    filterValue = ""
                                    currentPage = 1
                                    onRunQuery(null, "==", null, null, "DESC", 1, pageLimit)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Reset Filter")
                            }
                        }
                    }
                }
            }

            // Pagination Bar
            val docs = queryPage?.documents ?: emptyList()
            val totalPages = queryPage?.totalPages ?: 1
            val totalDocs = queryPage?.totalDocuments ?: 0

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "'$selectedCollection' ($totalDocs docs • Page $currentPage/$totalPages)",
                        style = MaterialTheme.typography.labelLarge,
                        color = ToxicGreen
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            enabled = currentPage > 1,
                            onClick = {
                                currentPage -= 1
                                onRunQuery(
                                    filterField.takeIf { it.isNotBlank() },
                                    filterOp,
                                    filterValue.takeIf { it.isNotBlank() },
                                    sortByField.takeIf { it.isNotBlank() },
                                    sortOrder,
                                    currentPage,
                                    pageLimit
                                )
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateBefore, contentDescription = "Previous page")
                        }
                        IconButton(
                            enabled = currentPage < totalPages,
                            onClick = {
                                currentPage += 1
                                onRunQuery(
                                    filterField.takeIf { it.isNotBlank() },
                                    filterOp,
                                    filterValue.takeIf { it.isNotBlank() },
                                    sortByField.takeIf { it.isNotBlank() },
                                    sortOrder,
                                    currentPage,
                                    pageLimit
                                )
                            }
                        ) {
                            Icon(Icons.AutoMirrored.Filled.NavigateNext, contentDescription = "Next page")
                        }
                    }
                }
            }

            // Selected Document Inspector & Live Editor
            if (selectedDocument != null) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0E2231)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.5.dp, CyberCyan, RoundedCornerShape(16.dp))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "DOCUMENT INSPECTOR: ${selectedDocument.id}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = CyberCyan
                                    )
                                    Text(
                                        text = "Version v${selectedDocument.version} • Owner: ${selectedDocument.ownerUserId ?: "system"} • ${selectedDocument.sizeBytes} B",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteDocument(selectedDocument.collection, selectedDocument.id) },
                                    modifier = Modifier.testTag("delete_selected_doc_button")
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete document", tint = CrimsonError)
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = editDocJson,
                                onValueChange = { editDocJson = it },
                                label = { Text("Edit Document JSON (PUT /database/${selectedDocument.collection}/${selectedDocument.id})") },
                                minLines = 4,
                                maxLines = 10,
                                textStyle = MaterialTheme.typography.bodySmall,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("edit_doc_json_input")
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Switch(
                                        checked = mergeOnUpdate,
                                        onCheckedChange = { mergeOnUpdate = it }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Merge fields", style = MaterialTheme.typography.bodySmall, color = Color.White)
                                }
                                Button(
                                    onClick = {
                                        onUpdateDocument(
                                            selectedDocument.collection,
                                            selectedDocument.id,
                                            editDocJson,
                                            mergeOnUpdate
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = ToxicGreen),
                                    modifier = Modifier.testTag("update_doc_submit_button")
                                ) {
                                    Icon(Icons.Default.Edit, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("ToxicBase.database.update()", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            // Document List Items
            if (docs.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianSurface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, ObsidianBorder, RoundedCornerShape(14.dp))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No documents match in '$selectedCollection'", color = Color.White)
                            Spacer(modifier = Modifier.height(6.dp))
                            Button(
                                onClick = { dbModeTab = 1 },
                                colors = ButtonDefaults.buttonColors(containerColor = ToxicGreen)
                            ) {
                                Text("Create First Document", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                items(docs, key = { it.id }) { doc ->
                    val isSelected = selectedDocument?.id == doc.id
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = ObsidianCard),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isSelected) CyberCyan else ObsidianBorder,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelectDocument(doc.collection, doc.id) }
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = doc.id,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = ToxicGreen
                                )
                                StatusPill(label = "v${doc.version} • ${doc.sizeBytes}B", color = CyberCyan)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(ObsidianSurface)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = doc.rawJson,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Updated: ${formatTimestamp(doc.updatedAt)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextMuted
                                )
                                Text(
                                    text = "Tap to inspect / edit",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = CyberCyan
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(28.dp)) }
    }
}
