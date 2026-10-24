package com.narkochi.cuanly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

private val Lilac = Color(0xFF7859A5)
private val PaleLilac = Color(0xFFF2EBFC)
private val SugarPink = Color(0xFFF4DCE9)
private val Cream = Color(0xFFFFF8F1)
private val Mint = Color(0xFFA8D7BD)
private val Plum = Color(0xFF493B48)

class MainActivity : ComponentActivity() {
    private val vm by viewModels<CuanlyViewModel> {
        CuanlyViewModel.Factory(CuanlyDatabase.get(applicationContext).dao())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Lilac,
                    secondary = Color(0xFF9A82C6),
                    background = Cream,
                    surface = Color.White,
                    onPrimary = Color.White,
                    onSurface = Plum
                )
            ) {
                CuanlyApp(vm)
            }
        }
    }
}

class CuanlyViewModel(private val dao: CuanlyDao) : ViewModel() {
    val products = dao.observeProducts().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val orders = dao.observeOrders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val transactions = dao.observeTransactions().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val income = dao.observeIncome().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val expenses = dao.observeExpenses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
    val activeOrders = dao.observeActiveOrders().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val lowStock = dao.observeLowStockCount().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    fun addProduct(name: String, sell: Long, cost: Long, stock: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            dao.addProduct(Product(name = name.trim(), sellPrice = sell, costPrice = cost, stock = stock))
            onDone()
        }
    }

    fun addOrder(customer: String, product: Product, qty: Int, onDone: () -> Unit) {
        viewModelScope.launch {
            if (qty > 0 && product.stock >= qty) {
                dao.addOrder(BusinessOrder(
                    customer = customer.trim(),
                    productName = product.name,
                    quantity = qty,
                    total = product.sellPrice * qty
                ))
                dao.updateStock(product.id, product.stock - qty)
                onDone()
            }
        }
    }

    fun updateOrderStatus(order: BusinessOrder, status: String) {
        viewModelScope.launch {
            dao.updateOrderStatus(order.id, status)
        }
    }

    fun addTransaction(title: String, amount: Long, type: String, onDone: () -> Unit) {
        viewModelScope.launch {
            dao.addTransaction(BusinessTransaction(title = title.trim(), amount = amount, type = type))
            onDone()
        }
    }

    class Factory(private val dao: CuanlyDao) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = CuanlyViewModel(dao) as T
    }
}

private enum class Screen(val label: String) {
    HOME("Home"), ORDERS("Pesanan"), FINANCE("Keuangan"), PRODUCTS("Produk"), REPORTS("Laporan")
}

@Composable
private fun CuanlyApp(vm: CuanlyViewModel) {
    var screen by remember { mutableStateOf(Screen.HOME) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    val products by vm.products.collectAsStateWithLifecycle()
    val orders by vm.orders.collectAsStateWithLifecycle()
    val transactions by vm.transactions.collectAsStateWithLifecycle()
    val income by vm.income.collectAsStateWithLifecycle()
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    val activeOrders by vm.activeOrders.collectAsStateWithLifecycle()
    val lowStock by vm.lowStock.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Cream,
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                val entries = listOf(
                    Screen.HOME to Icons.Default.Home,
                    Screen.ORDERS to Icons.Default.ShoppingBag,
                    Screen.FINANCE to Icons.Default.AccountBalanceWallet,
                    Screen.PRODUCTS to Icons.Default.Inventory2,
                    Screen.REPORTS to Icons.Default.BarChart
                )
                entries.forEach { (item, icon) ->
                    NavigationBarItem(
                        selected = screen == item,
                        onClick = { screen = item; notice = null },
                        icon = { Icon(icon, contentDescription = item.label) },
                        label = { Text(item.label) },
                        colors = NavigationBarItemDefaults.colors(selectedIconColor = Lilac, indicatorColor = PaleLilac)
                    )
                }
            }
        },
        floatingActionButton = {
            if (screen == Screen.ORDERS || screen == Screen.PRODUCTS || screen == Screen.FINANCE) {
                FloatingActionButton(
                    onClick = {
                        dialog = when (screen) {
                            Screen.ORDERS -> "order"
                            Screen.PRODUCTS -> "product"
                            else -> "transaction"
                        }
                    },
                    containerColor = Lilac,
                    contentColor = Color.White
                ) { Icon(Icons.Default.Add, contentDescription = "Tambah") }
            }
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cuanly", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Lilac)
                    Text("Catat. Pantau. Cuan.", style = MaterialTheme.typography.bodyMedium, color = Plum)
                }
                Surface(color = PaleLilac, shape = RoundedCornerShape(16.dp)) {
                    Icon(Icons.Default.AccountBalanceWallet, null, tint = Lilac, modifier = Modifier.padding(12.dp).size(28.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            notice?.let {
                Text(it, color = Lilac, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
            }
            when (screen) {
                Screen.HOME -> HomeScreen(income, expenses, activeOrders, lowStock, products.size, orders.size)
                Screen.ORDERS -> OrdersScreen(orders, onStatus = { order ->
                    val next = when (order.status) {
                        "Diproses" -> "Selesai"
                        "Selesai" -> "Diproses"
                        else -> "Selesai"
                    }
                    vm.updateOrderStatus(order, next)
                    notice = "Status pesanan diperbarui (MVP)."
                })
                Screen.FINANCE -> FinanceScreen(income, expenses, transactions)
                Screen.PRODUCTS -> ProductsScreen(products)
                Screen.REPORTS -> ReportsScreen(income, expenses, orders.size, products.size)
            }
        }
    }

    if (dialog == "product") ProductDialog(
        onDismiss = { dialog = null },
        onSave = { name, sell, cost, stock ->
            vm.addProduct(name, sell, cost, stock) { dialog = null; notice = "Produk berhasil disimpan." }
        }
    )
    if (dialog == "order") OrderDialog(
        products = products,
        onDismiss = { dialog = null },
        onSave = { customer, product, qty ->
            vm.addOrder(customer, product, qty) { dialog = null; notice = "Pesanan disimpan dan stok dikurangi." }
        }
    )
    if (dialog == "transaction") TransactionDialog(
        onDismiss = { dialog = null },
        onSave = { title, amount, type ->
            vm.addTransaction(title, amount, type) { dialog = null; notice = "Transaksi berhasil disimpan." }
        }
    )
}

@Composable
private fun HomeScreen(income: Long, expenses: Long, active: Int, lowStock: Int, productCount: Int, orderCount: Int) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Halo, Bos Q ✨", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Ringkasan usaha kamu hari ini.", color = Color.Gray)
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Lilac), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Total pemasukan tercatat", color = Color.White)
                    Text(rupiah(income), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(Modifier.height(8.dp))
                    Text("Pengeluaran: ${rupiah(expenses)}", color = Color.White)
                    Text("Selisih sederhana: ${rupiah(income - expenses)}", color = Color.White)
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Pesanan aktif", active.toString(), Icons.Default.ShoppingBag, Modifier.weight(1f))
                MetricCard("Stok menipis", lowStock.toString(), Icons.Default.Inventory2, Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Produk", productCount.toString(), Icons.Default.Category, Modifier.weight(1f))
                MetricCard("Total pesanan", orderCount.toString(), Icons.Default.ReceiptLong, Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Mulai gunakan CUANLY", fontWeight = FontWeight.Bold, color = Plum)
                    Text("Gunakan menu bawah untuk menambahkan produk, pesanan, dan transaksi.", color = Color.Gray)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp)) {
            Icon(icon, null, tint = Lilac)
            Spacer(Modifier.height(8.dp))
            Text(title, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = Plum)
        }
    }
}

@Composable
private fun OrdersScreen(orders: List<BusinessOrder>, onStatus: (BusinessOrder) -> Unit) {
    Column {
        Text("Pesanan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Tambah pesanan lewat tombol +. Pesanan mengurangi stok produk.", color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        if (orders.isEmpty()) EmptyState("Belum ada pesanan", "Tekan tombol + untuk mencatat pesanan pertama.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(orders, key = { it.id }) { order ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(order.customer, fontWeight = FontWeight.Bold)
                                Text("${order.productName} × ${order.quantity}", color = Color.Gray)
                            }
                            Text(rupiah(order.total), fontWeight = FontWeight.Bold, color = Lilac)
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Status: ${order.status}", color = Plum, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onStatus(order) }) { Text("Ubah status") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinanceScreen(income: Long, expenses: Long, transactions: List<BusinessTransaction>) {
    Column {
        Text("Keuangan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("Pemasukan", rupiah(income), Icons.Default.TrendingUp, Modifier.weight(1f))
            MetricCard("Pengeluaran", rupiah(expenses), Icons.Default.TrendingDown, Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))
        Text("Riwayat transaksi", fontWeight = FontWeight.Bold)
        if (transactions.isEmpty()) EmptyState("Belum ada transaksi", "Tekan tombol + untuk mencatat pemasukan atau pengeluaran.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(transactions, key = { it.id }) { t ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (t.type == "Pemasukan") Icons.Default.AddCircle else Icons.Default.RemoveCircle, null,
                            tint = if (t.type == "Pemasukan") Color(0xFF329B78) else Color(0xFFC66B86))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title, fontWeight = FontWeight.SemiBold)
                            Text(t.type, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(rupiah(t.amount), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductsScreen(products: List<Product>) {
    Column {
        Text("Produk & Stok", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Produk dengan stok ≤ 5 akan ditandai menipis.", color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        if (products.isEmpty()) EmptyState("Belum ada produk", "Tekan tombol + untuk menambahkan produk.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products, key = { it.id }) { p ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = PaleLilac, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.Inventory2, null, tint = Lilac, modifier = Modifier.padding(10.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold)
                            Text("Jual ${rupiah(p.sellPrice)} · Modal ${rupiah(p.costPrice)}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("Stok ${p.stock}", fontWeight = FontWeight.Bold, color = if (p.stock <= p.lowStockThreshold) Color(0xFFB66A32) else Plum)
                            if (p.stock <= p.lowStockThreshold) Text("Menipis", color = Color(0xFFB66A32), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReportsScreen(income: Long, expenses: Long, orders: Int, products: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Laporan", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Ringkasan berdasarkan semua data yang tersimpan di perangkat.", color = Color.Gray)
        Card(colors = CardDefaults.cardColors(containerColor = Lilac), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                Text("Selisih pemasukan - pengeluaran", color = Color.White)
                Text(rupiah(income - expenses), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("Ini bukan laba bersih akuntansi; HPP belum dikurangkan otomatis.", color = Color.White)
            }
        }
        MetricCard("Jumlah pesanan", orders.toString(), Icons.Default.ReceiptLong, Modifier.fillMaxWidth())
        MetricCard("Jumlah produk", products.toString(), Icons.Default.Category, Modifier.fillMaxWidth())
        Text("Laporan saat ini berupa ringkasan sederhana. Filter periode dan ekspor CSV akan ditambahkan pada tahap berikutnya.", color = Color.Gray)
    }
}

@Composable
private fun EmptyState(title: String, description: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.fillMaxWidth().padding(22.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Inbox, null, tint = Lilac, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(8.dp))
            Text(title, fontWeight = FontWeight.Bold)
            Text(description, color = Color.Gray)
        }
    }
}

@Composable
private fun ProductDialog(onDismiss: () -> Unit, onSave: (String, Long, Long, Int) -> Unit) {
    var name by remember { mutableStateOf("") }
    var sell by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var stock by remember { mutableStateOf("0") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah produk") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nama produk") }, singleLine = true)
                OutlinedTextField(sell, { sell = it }, label = { Text("Harga jual (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(cost, { cost = it }, label = { Text("Harga modal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(stock, { stock = it }, label = { Text("Stok awal") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val s = sell.toLongOrNull(); val c = cost.toLongOrNull(); val q = stock.toIntOrNull()
                if (name.isBlank() || s == null || s <= 0 || c == null || c < 0 || q == null || q < 0) error = "Periksa nama, harga, dan stok."
                else onSave(name, s, c, q)
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun OrderDialog(products: List<Product>, onDismiss: () -> Unit, onSave: (String, Product, Int) -> Unit) {
    var customer by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var selectedId by remember { mutableStateOf(products.firstOrNull()?.id) }
    var error by remember { mutableStateOf<String?>(null) }
    val selected = products.firstOrNull { it.id == selectedId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah pesanan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (products.isEmpty()) Text("Tambahkan produk terlebih dahulu.")
                else {
                    OutlinedTextField(customer, { customer = it }, label = { Text("Nama pelanggan") }, singleLine = true)
                    Text("Pilih produk")
                    products.forEach { p ->
                        Row(Modifier.fillMaxWidth().clickable { selectedId = p.id }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedId == p.id, onClick = { selectedId = p.id })
                            Column {
                                Text(p.name)
                                Text("${rupiah(p.sellPrice)} · stok ${p.stock}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    OutlinedTextField(qty, { qty = it }, label = { Text("Jumlah") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val q = qty.toIntOrNull()
                when {
                    products.isEmpty() -> error = "Buat produk terlebih dahulu."
                    customer.isBlank() -> error = "Nama pelanggan wajib diisi."
                    selected == null -> error = "Pilih produk."
                    q == null || q <= 0 -> error = "Jumlah harus lebih dari 0."
                    q > selected.stock -> error = "Stok tidak mencukupi."
                    else -> onSave(customer, selected, q)
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun TransactionDialog(onDismiss: () -> Unit, onSave: (String, Long, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Pemasukan") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tambah transaksi") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Keterangan") }, singleLine = true)
                OutlinedTextField(amount, { amount = it }, label = { Text("Nominal (Rp)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(type == "Pemasukan", { type = "Pemasukan" })
                    Text("Pemasukan")
                    Spacer(Modifier.width(8.dp))
                    RadioButton(type == "Pengeluaran", { type = "Pengeluaran" })
                    Text("Pengeluaran")
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val value = amount.toLongOrNull()
                if (title.isBlank() || value == null || value <= 0) error = "Isi keterangan dan nominal yang valid."
                else onSave(title, value, type)
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

private fun rupiah(value: Long): String =
    NumberFormat.getCurrencyInstance(Locale("id", "ID")).apply {
        maximumFractionDigits = 0
        minimumFractionDigits = 0
    }.format(value).replace("Rp", "Rp ")
