package com.example

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.Expense
import com.example.ui.BiometricHelper
import com.example.ui.components.MonthlySummaryChart
import com.example.ui.components.SignaturePad
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.MahardikaViewModel
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MahardikaViewModel = viewModel()
            val isDark by viewModel.isDarkMode.collectAsState()

            MyApplicationTheme(darkTheme = isDark) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppContent(viewModel = viewModel, activity = this@MainActivity)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppContent(viewModel: MahardikaViewModel, activity: FragmentActivity) {
    val isBiometricsEnabled by viewModel.isBiometricsEnabled.collectAsState()
    val isUnlocked by viewModel.isUnlocked.collectAsState()
    val context = LocalContext.current

    // Trigger biometric on start if enabled
    LaunchedEffect(isBiometricsEnabled) {
        if (isBiometricsEnabled && !isUnlocked) {
            BiometricHelper.authenticate(
                activity = activity,
                onSuccess = {
                    viewModel.isUnlocked.value = true
                },
                onError = { err ->
                    Toast.makeText(context, "Autentikasi Gagal: $err", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    if (isBiometricsEnabled && !isUnlocked) {
        // Biometric Lock Screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .safeDrawingPadding()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Lock,
                    contentDescription = "Lock",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Aplikasi Terkunci",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Autentikasi biometrik diaktifkan untuk melindungi data CV Mahardika.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(40.dp))
                Button(
                    onClick = {
                        BiometricHelper.authenticate(
                            activity = activity,
                            onSuccess = {
                                viewModel.isUnlocked.value = true
                            },
                            onError = { err ->
                                Toast.makeText(context, "Gagal: $err", Toast.LENGTH_SHORT).show()
                            }
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Filled.Fingerprint, contentDescription = "Fingerprint")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("BUKA DENGAN SIDIK JARI / PIN")
                }
            }
        }
    } else {
        // Main Application View
        var currentTab by remember { mutableStateOf(0) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = when (currentTab) {
                                0 -> "Pemasukan Harian CV Mahardika"
                                1 -> "Pengeluaran"
                                2 -> "Dasbor Visual"
                                else -> "Pengaturan"
                            },
                            fontWeight = FontWeight.Bold
                        )
                    },
                    actions = {
                        // Quick toggle dark theme
                        val isDark by viewModel.isDarkMode.collectAsState()
                        IconButton(onClick = { viewModel.toggleDarkMode(!isDark) }) {
                            Icon(
                                imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                                contentDescription = "Toggle Theme"
                            )
                        }

                        // Sync button
                        val isSyncAvail by viewModel.isCloudSyncAvailable.collectAsState()
                        if (isSyncAvail) {
                            val syncStatus by viewModel.syncStatusText.collectAsState()
                            IconButton(onClick = {
                                viewModel.triggerSync()
                                Toast.makeText(context, syncStatus, Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = Icons.Filled.CloudSync,
                                    contentDescription = "Sync Cloud"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Income") },
                        label = { Text("Income") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Filled.ReceiptLong, contentDescription = "Expenses") },
                        label = { Text("Pengeluaran") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Filled.BarChart, contentDescription = "Dashboard") },
                        label = { Text("Dasbor") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                        label = { Text("Pengaturan") }
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentTab) {
                    0 -> IncomeScreen(viewModel = viewModel)
                    1 -> ExpenseScreen(viewModel = viewModel)
                    2 -> DashboardScreen(viewModel = viewModel)
                    else -> SettingsScreen(viewModel = viewModel)
                }
            }
        }
    }
}

// Sub-Screens

@Composable
fun IncomeScreen(viewModel: MahardikaViewModel) {
    val context = LocalContext.current
    val selectedDate by viewModel.selectedDate.collectAsState()
    val leaderName by viewModel.leaderName.collectAsState()
    val signaturePoints by viewModel.signaturePoints.collectAsState()

    var activeShift by remember { mutableStateOf(1) } // 1, 2, or 3

    val shift1Data by viewModel.shift1Qty.collectAsState()
    val shift2Data by viewModel.shift2Qty.collectAsState()
    val shift3Data by viewModel.shift3Qty.collectAsState()

    val currentShiftData = when (activeShift) {
        1 -> shift1Data
        2 -> shift2Data
        else -> shift3Data
    }

    val denominations = listOf(100000, 50000, 20000, 10000, 5000, 2000, 1000)

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("in", "ID")).apply {
            maximumFractionDigits = 0
        }
    }

    // Date picker state
    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val dateStr = String.format("%d-%02d-%02d", year, month + 1, dayOfMonth)
            viewModel.selectDate(dateStr)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Date Selector Block
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Tanggal: $selectedDate",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Button(onClick = { datePickerDialog.show() }) {
                        Icon(imageVector = Icons.Default.CalendarMonth, contentDescription = "Calendar")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("PILIH TANGGAL")
                    }
                }
            }
        }

        // 2. Leader Name Field
        item {
            OutlinedTextField(
                value = leaderName,
                onValueChange = { viewModel.updateLeaderName(it) },
                label = { Text("Nama Leader") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "Leader") }
            )
        }

        // 3. Shift Segment Switcher
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                for (sh in 1..3) {
                    FilterChip(
                        selected = activeShift == sh,
                        onClick = { activeShift = sh },
                        label = { Text("Shift $sh", fontWeight = FontWeight.Bold) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 4. Denominations Input List
        items(denominations) { denom ->
            val qty = currentShiftData[denom] ?: ""
            val qtyLong = qty.toLongOrNull() ?: 0L
            val lineTotal = denom * qtyLong

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = currencyFormat.format(denom),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(90.dp)
                    )

                    OutlinedTextField(
                        value = qty,
                        onValueChange = { viewModel.updateShiftQty(activeShift, denom, it) },
                        placeholder = { Text("Qty") },
                        modifier = Modifier
                            .width(100.dp)
                            .height(55.dp),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    Text(
                        text = currencyFormat.format(lineTotal),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 5. Total Shift Display
        item {
            val totalShift = remember(currentShiftData) {
                currentShiftData.entries.sumOf { (denom, qty) ->
                    denom.toLong() * (qty.toLongOrNull() ?: 0L)
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "TOTAL SHIFT $activeShift:",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = currencyFormat.format(totalShift),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // 6. Signature Pad Block
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Tanda Tangan :",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    SignaturePad(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp),
                        pointsString = signaturePoints,
                        onSignatureChanged = { viewModel.updateSignature(it) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { viewModel.clearSignature() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Clear")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("HAPUS TTD")
                        }

                        Button(
                            onClick = {
                                viewModel.exportPdf(context) { file ->
                                    if (file != null) {
                                        shareFile(context, file, "application/pdf")
                                    } else {
                                        Toast.makeText(context, "Gagal mencetak PDF", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0, 150, 136)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF")
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CETAK PDF")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ExpenseScreen(viewModel: MahardikaViewModel) {
    val amount by viewModel.expenseAmount.collectAsState()
    val category by viewModel.expenseCategory.collectAsState()
    val description by viewModel.expenseDescription.collectAsState()
    val dateVal by viewModel.expenseDate.collectAsState()
    val expensesList by viewModel.allExpenses.collectAsState()

    val context = LocalContext.current
    val categories = listOf("Operasional", "ATK/Supplies", "Gaji Karyawan", "Listrik/Air/Wifi", "Lain-lain")

    val currencyFormat = remember {
        NumberFormat.getCurrencyInstance(Locale("in", "ID")).apply {
            maximumFractionDigits = 0
        }
    }

    val calendar = Calendar.getInstance()
    val datePickerDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val dateStr = String.format("%d-%02d-%02d", year, month + 1, dayOfMonth)
            viewModel.expenseDate.value = dateStr
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Tambah Catatan Pengeluaran",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = amount,
                        onValueChange = { viewModel.expenseAmount.value = it },
                        label = { Text("Jumlah (Rp)") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )

                    // Dropdown/Selector Kategori
                    var expanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = category,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Kategori") },
                            trailingIcon = {
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    "Drop",
                                    Modifier.clickable { expanded = !expanded }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                        )
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            categories.forEach { cat ->
                                DropdownMenuItem(
                                    text = { Text(cat) },
                                    onClick = {
                                        viewModel.expenseCategory.value = cat
                                        expanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Date selector
                    OutlinedTextField(
                        value = dateVal,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Tanggal Pengeluaran") },
                        trailingIcon = {
                            Icon(
                                Icons.Default.CalendarToday,
                                "Calendar",
                                Modifier.clickable { datePickerDialog.show() }
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { datePickerDialog.show() }
                    )

                    OutlinedTextField(
                        value = description,
                        onValueChange = { viewModel.expenseDescription.value = it },
                        label = { Text("Keterangan/Deskripsi") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = false,
                        maxLines = 2
                    )

                    Button(
                        onClick = { viewModel.addExpense() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add")
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("TAMBAH PENGELUARAN")
                    }
                }
            }
        }

        item {
            Text(
                text = "Riwayat Pengeluaran",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 10.dp)
            )
        }

        if (expensesList.isEmpty()) {
            item {
                Text(
                    text = "Belum ada catatan pengeluaran harian.",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(expensesList) { expense ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = expense.category,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = expense.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Tanggal: ${expense.date}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.Gray
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currencyFormat.format(expense.amount),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            IconButton(onClick = { viewModel.deleteExpenseItem(expense) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Hapus",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(viewModel: MahardikaViewModel) {
    val reports by viewModel.allIncomeReports.collectAsState()
    val expenses by viewModel.allExpenses.collectAsState()

    // Default to current year-month
    var activeYearMonth by remember {
        mutableStateOf(SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date()))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Bulan Grafik: $activeYearMonth",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        // Monthly custom canvas bar chart
        MonthlySummaryChart(
            modifier = Modifier.fillMaxWidth(),
            reports = reports,
            expenses = expenses,
            selectedMonth = activeYearMonth
        )
    }
}

@Composable
fun SettingsScreen(viewModel: MahardikaViewModel) {
    val context = LocalContext.current
    val isBiometric by viewModel.isBiometricsEnabled.collectAsState()
    val isReminder by viewModel.isReminderEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Preferensi & Keamanan",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )

        // 1. Biometric toggle row
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Autentikasi Biometrik", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Lindungi privasi laporan keuangan dengan sidik jari/sandi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = isBiometric,
                    onCheckedChange = { viewModel.toggleBiometrics(it) }
                )
            }
        }

        // 2. Reminder toggle row
        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Pengingat Harian", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Notifikasi harian pukul 20.00 untuk mencatat pemasukan harian.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                Switch(
                    checked = isReminder,
                    onCheckedChange = { viewModel.toggleReminder(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Tindakan Ekspor & Data",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )

        // 3. Export to Excel Button
        Button(
            onClick = {
                viewModel.exportExcel(context) { file ->
                    if (file != null) {
                        shareFile(context, file, "text/csv")
                    } else {
                        Toast.makeText(context, "Gagal mengekspor CSV", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Icon(Icons.Filled.GridOn, contentDescription = "Excel")
            Spacer(modifier = Modifier.width(8.dp))
            Text("EKSPOR EXCEL (CSV)")
        }

        // 4. Reset Button
        Button(
            onClick = {
                viewModel.resetDailyReport()
                Toast.makeText(context, "Laporan hari ini telah di-reset.", Toast.LENGTH_SHORT).show()
            },
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = "Reset")
            Spacer(modifier = Modifier.width(8.dp))
            Text("RESET DATA LAPORAN HARI INI")
        }
    }
}

// Global Sharing Intent Helper

fun shareFile(context: Context, file: File, mimeType: String) {
    try {
        val uri = FileProvider.getUriForFile(
            context,
            "com.aistudio.incomereport.mahardika.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Kirim Laporan Mahardika"))
    } catch (e: Exception) {
        Toast.makeText(context, "Gagal membagikan berkas: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
