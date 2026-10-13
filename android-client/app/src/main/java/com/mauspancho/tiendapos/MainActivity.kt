package com.mauspancho.tiendapos

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.*
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.mauspancho.tiendapos.data.api.*
import com.mauspancho.tiendapos.data.repository.*
import com.mauspancho.tiendapos.navigation.Destination
import com.mauspancho.tiendapos.ui.*
import com.mauspancho.tiendapos.ui.auth.*
import com.mauspancho.tiendapos.ui.server.*
import com.mauspancho.tiendapos.ui.dashboard.*
import com.mauspancho.tiendapos.ui.products.*
import com.mauspancho.tiendapos.ui.pos.*
import com.mauspancho.tiendapos.ui.cash.*
import com.mauspancho.tiendapos.ui.sales.*
import com.mauspancho.tiendapos.ui.settings.*
import com.mauspancho.tiendapos.ui.components.*
import com.mauspancho.tiendapos.ui.credits.*
import com.mauspancho.tiendapos.data.storage.EncryptedPendingOperationStore
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { TiendaRoot(application as TiendaApplication) }
    }
}
@Composable inline fun <reified T : ViewModel> scopedVm(key: String, noinline create: (SavedStateHandle) -> T): T =
    viewModel(key = key, factory = viewModelFactory { initializer { create(createSavedStateHandle()) } })

@Composable fun TiendaRoot(app: TiendaApplication) {
    val vm = scopedVm("app") { AppViewModel(app, it) }
    val preferences by vm.preferences.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val scopeId by vm.scopeId.collectAsStateWithLifecycle()
    val operation by vm.operation.collectAsStateWithLifecycle()
    val notice by vm.notice.collectAsStateWithLifecycle()
    val dark = when (preferences.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
    MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme()) {
        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
            key(scopeId) { when (screen) {
                StartScreen.RESTORING -> Column(Modifier.padding(24.dp)) { Text("Restaurando sesión…"); if (operation.loading) Loading(); ErrorCard(operation.error, vm::restore); TextButton(vm::logout) { Text("Ir a iniciar sesión") } }
                StartScreen.SERVER -> {
                    val server = scopedVm("server.$scopeId") { ServerViewModel(app.json, RemoteServerRepository(app::probe), vm::saveServer) }
                    ServerScreen(server)
                }
                StartScreen.LOGIN -> vm.client?.let { client ->
                    val auth = scopedVm("auth.$scopeId") { AuthViewModel(app.json, RemoteAuthRepository(client), vm::signedIn) }
                    AuthScreen(auth, preferences.server.orEmpty(), notice, vm::changeServer)
                }
                StartScreen.HOME -> vm.client?.let { Home(app, vm, it, scopeId) }
            } }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Home(app: TiendaApplication, root: AppViewModel, client: ApiClient, scopeId: String) {
    val user by root.user.collectAsStateWithLifecycle()
    val roles = user?.roles.orEmpty()
    val admin = "ADMIN" in roles
    fun pendingStore(slot: String) = EncryptedPendingOperationStore(app, "${client.baseUrl}|${user?.id}|$slot")
    val destinations = Destination.entries.filter { it.allowed(roles) }
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route.orEmpty()
    val selected = Destination.entries.find { it.name == current }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val coroutine = rememberCoroutineScope()
    fun navigate(route: String) { nav.navigate(route) { launchSingleTop = true; popUpTo(nav.graph.startDestinationId) { saveState = true }; restoreState = true } }
    fun ticket(folio: String) { nav.navigate("ticket/${Uri.encode(folio)}") }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet { LazyColumn(modifier = Modifier.testTag("drawer.list"), contentPadding = PaddingValues(12.dp)) {
            item { Text("Tienda POS", Modifier.padding(16.dp), style = MaterialTheme.typography.headlineSmall); Text(user?.displayName.orEmpty(), Modifier.padding(16.dp)) }
            destinations.forEach { destination -> item {
                NavigationDrawerItem(label = { Text(destination.label) }, selected = selected == destination,
                    onClick = { navigate(destination.name); coroutine.launch { drawer.close() } })
            } }
        } }
    }) {
        BoxWithConstraints {
            val wide = maxWidth >= 600.dp
            Scaffold(topBar = { TopAppBar(title = { Text(selected?.label ?: "Tienda POS") }, navigationIcon = {
                TextButton({ coroutine.launch { drawer.open() } }) { Text("Menú") }
            }) }, bottomBar = {
                if (!wide) NavigationBar {
                    destinations.filter { it in listOf(Destination.DASHBOARD, Destination.POS, Destination.PRODUCTS) }.forEach { destination ->
                        NavigationBarItem(selected == destination, { navigate(destination.name) }, icon = { Text(when (destination) { Destination.DASHBOARD -> "⌂"; Destination.POS -> "+"; else -> "▤" }) }, label = { Text(destination.label) }, modifier = Modifier.testTag("bottom.${destination.name}"))
                    }
                }
            }) { padding -> Row(Modifier.fillMaxSize().padding(padding)) {
                if (wide) NavigationRail { destinations.filter { it in listOf(Destination.DASHBOARD, Destination.POS, Destination.PRODUCTS, Destination.SETTINGS) }.forEach { destination ->
                    NavigationRailItem(selected == destination, { navigate(destination.name) }, icon = { Text(destination.label.take(1)) }, label = { Text(destination.label) })
                } }
                NavHost(nav, if (Destination.DASHBOARD in destinations) Destination.DASHBOARD.name else Destination.SETTINGS.name, Modifier.weight(1f)) {
                    destinations.forEach { destination -> composable(destination.name) {
                        if (!destination.allowed(roles)) { Empty("No tienes permisos para realizar esta acción."); return@composable }
                        when (destination) {
                            Destination.DASHBOARD -> {
                                val vm = scopedVm("dashboard.$scopeId") { DashboardViewModel(client) }
                                DashboardScreen(vm, user?.displayName.orEmpty(), { navigate(Destination.POS.name) }, { nav.navigate("profit") })
                            }
                            Destination.POS -> {
                                val vm = scopedVm("pos.$scopeId") { PosViewModel(app.json, RemotePosRepository(client.api), RemoteProductRepository(client.api), it, RemoteCreditRepository(client.api), pendingStore("pos")) }
                                val picker = scopedVm("posCustomers.$scopeId") { CustomerPickerViewModel(app.json, RemoteCreditRepository(client.api), it) }
                                LaunchedEffect(Unit) { vm.refresh() }
                                PosScreen(vm, admin, { navigate(Destination.CASH.name) }, { code -> nav.navigate("productform?barcode=${Uri.encode(code)}") }, ::ticket, picker)
                            }
                            Destination.CREDITS -> {
                                val vm = scopedVm("credits.$scopeId") { CreditsViewModel(app.json, RemoteCreditRepository(client.api), it, pendingStore("creditPayment")) }
                                val picker = scopedVm("creditCustomers.$scopeId") { CustomerPickerViewModel(app.json, RemoteCreditRepository(client.api), it) }
                                CreditsScreen(vm, picker, admin) { id -> nav.navigate("creditpos?customerId=${id ?: 0}") }
                            }
                            Destination.PRODUCTS -> ProductRoute(app, client, scopeId, admin)
                            Destination.CASH -> {
                                val vm = scopedVm("cash.$scopeId") { CashViewModel(client) }
                                CashScreen(vm, { nav.navigate("cashhistory") }, admin)
                            }
                            Destination.SETTINGS -> {
                                val vm = scopedVm("settings.$scopeId") { SettingsViewModel(app, client) }
                                SettingsScreen(root, vm, { nav.navigate("business") }, admin)
                            }
                            else -> {
                                val vm = scopedVm("${destination.name}.$scopeId") { ModuleViewModel(ModuleRepository(client), destination.path!!, it) }
                                if (destination == Destination.FINANCES) Column(Modifier.fillMaxSize()) {
                                    Row { TextButton({ nav.navigate("capital") }) { Text("Capital") }; TextButton({ nav.navigate("daily") }) { Text("Histórico diario") }; TextButton({ nav.navigate("financeexport") }) { Text("Exportar CSV") } }
                                    Box(Modifier.weight(1f)) { ModuleScreen(vm, false, admin) }
                                } else if (destination == Destination.INVENTORY) Column(Modifier.fillMaxSize()) {
                                    TextButton({ nav.navigate("stock") }) { Text("Existencias") }
                                    Box(Modifier.weight(1f)) { ModuleScreen(vm, true, admin) }
                                } else ModuleScreen(vm, destination.paged, admin, ::ticket)
                            }
                        }
                    } }
                    if (roles.any { it == "ADMIN" || it == "CAJERO" }) composable("creditpos?customerId={customerId}", arguments = listOf(navArgument("customerId") { type = NavType.LongType; defaultValue = 0L })) { stack ->
                        val id = stack.arguments?.getLong("customerId") ?: 0L
                        val vm = scopedVm("creditpos.$scopeId") { PosViewModel(app.json, RemotePosRepository(client.api), RemoteProductRepository(client.api), it, RemoteCreditRepository(client.api), pendingStore("creditpos")) }
                        val picker = scopedVm("creditposCustomers.$scopeId") { CustomerPickerViewModel(app.json, RemoteCreditRepository(client.api), it) }
                        val restoring by vm.restoring.collectAsStateWithLifecycle()
                        LaunchedEffect(id, restoring) { if (!restoring) { vm.refresh(); vm.mode(true); if (id > 0) vm.selectCustomer(id) } }
                        Column(Modifier.fillMaxSize()) {
                            TextButton({ nav.popBackStack() }) { Text("Volver a Créditos") }
                            Box(Modifier.weight(1f)) { PosScreen(vm, admin, { navigate(Destination.CASH.name) }, { code -> nav.navigate("productform?barcode=${Uri.encode(code)}") }, ::ticket, picker) }
                        }
                    }
                    composable("ticket/{folio}", arguments = listOf(navArgument("folio") { type = NavType.StringType })) { stack ->
                        val folio = stack.arguments?.getString("folio").orEmpty()
                        val vm = scopedVm("ticket.$scopeId.$folio") { TicketViewModel(client, app.media, folio) }
                        TicketScreen(vm, app.json) { nav.popBackStack() }
                    }
                    if (roles.any { it == "ADMIN" || it == "CAJERO" }) composable("profit") {
                        val vm = scopedVm("profit.$scopeId") { ModuleViewModel(ModuleRepository(client), "dashboard/profit", it) }
                        Column(Modifier.fillMaxSize()) { TextButton({ nav.popBackStack() }) { Text("Volver") }; Box(Modifier.weight(1f)) { ModuleScreen(vm, false, admin) } }
                    }
                    if (admin) {
                        listOf("cashhistory" to "cash/sessions", "stock" to "inventory/stock", "capital" to "finances/capital", "daily" to "finances/daily", "business" to "settings").forEach { (route, path) -> composable(route) {
                            val vm = scopedVm("$route.$scopeId") { ModuleViewModel(ModuleRepository(client), path, it) }
                            Column(Modifier.fillMaxSize()) { TextButton({ nav.popBackStack() }) { Text("Volver") }; Box(Modifier.weight(1f)) { ModuleScreen(vm, route in listOf("cashhistory", "stock", "capital"), true, day = { date -> nav.navigate("day/${Uri.encode(date)}") }) } }
                        } }
                        composable("productform?barcode={barcode}", arguments = listOf(navArgument("barcode") { type = NavType.StringType; defaultValue = "" })) { stack ->
                            val vm = scopedVm("newProduct.$scopeId") { ModuleViewModel(ModuleRepository(client), "products", it) }
                            val form by vm.form.collectAsStateWithLifecycle()
                            LaunchedEffect(Unit) { vm.create(stack.arguments?.getString("barcode")) }
                            form?.let { FormDialog(vm, it) } ?: Column { Text("Producto guardado o formulario cerrado."); TextButton({ nav.popBackStack() }) { Text("Volver al punto de venta") } }
                        }
                        composable("financeexport") {
                            val vm = scopedVm("export.$scopeId") { SettingsViewModel(app, client) }
                            ExportScreen(vm) { nav.popBackStack() }
                        }
                        composable("day/{date}", arguments = listOf(navArgument("date") { type = NavType.StringType })) { stack ->
                            val date = stack.arguments?.getString("date").orEmpty()
                            val vm = scopedVm("day.$scopeId.$date") { ModuleViewModel(ModuleRepository(client), "finances/daily/detail", it).apply { filters.value = mapOf("date" to date) } }
                            Column(Modifier.fillMaxSize()) { TextButton({ nav.popBackStack() }) { Text("Volver") }; Box(Modifier.weight(1f)) { ModuleScreen(vm, false, true) } }
                        }
                    }
                }
            } }
        }
    }
}
@Composable fun ProductRoute(app: TiendaApplication, client: ApiClient, scopeId: String, admin: Boolean) {
    val context = LocalContext.current
    val vm = scopedVm("products.$scopeId") { ProductsViewModel(client, RemoteProductRepository(client.api), app.media) }
    val forms = scopedVm("productForms.$scopeId") { ModuleViewModel(ModuleRepository(client), "products", it) }
    var choose by remember { mutableStateOf(false) }
    var photo by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) vm.detail.value.value?.let { vm.image(it, uri) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { okay -> if (okay) photo?.let { uri -> vm.detail.value.value?.let { vm.image(it, uri.toUri()) } } }
    fun capture() { photo = FileProvider.getUriForFile(context, "${context.packageName}.files", app.media.cameraFile()).toString(); camera.launch(photo!!.toUri()) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed -> if (allowed) capture() else vm.operation.value = TaskState(error = Failure("Permite la cámara o selecciona una imagen de la galería.")) }
    ProductsScreen(vm, forms, client.baseUrl, admin) { choose = true }
    if (choose) AlertDialog({ choose = false }, title = { Text("Imagen del producto") }, text = { Text("Selecciona el origen de la imagen.") },
        confirmButton = { TextButton({ choose = false; picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("Galería") } },
        dismissButton = { TextButton({ choose = false; if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) capture() else permission.launch(Manifest.permission.CAMERA) }) { Text("Cámara") } })
}
fun shareFile(context: android.content.Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val intent = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Compartir"))
}
@Composable fun TicketScreen(vm: TicketViewModel, json: Json, back: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val file by vm.file.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(file.value) { file.value?.let { shareFile(context, it, "text/plain"); vm.shared() } }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { TextButton(back) { Text("Volver") }; Text("Ticket", style = MaterialTheme.typography.headlineMedium) }
        if (state.loading) item { Loading() }
        item { ErrorCard(state.error, vm::refresh); ErrorCard(file.error, vm::share) }
        state.value?.let { sale -> item { NativeDetails(json.encodeToJsonElement(sale)) }; item { Action("Compartir ticket", !file.loading, vm::share) } }
    }
}
@Composable fun SettingsScreen(root: AppViewModel, vm: SettingsViewModel, business: () -> Unit, admin: Boolean) {
    val prefs by root.preferences.collectAsStateWithLifecycle()
    val user by root.user.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    var confirm by remember { mutableStateOf<String?>(null) }
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { Text("Servidor", style = MaterialTheme.typography.titleLarge); Text(prefs.server.orEmpty()); Text("Servidor ${health.value?.serverVersion ?: prefs.backendVersion} · API ${health.value?.apiVersion ?: prefs.apiVersion}"); Text(if (health.value != null) "Servidor encontrado" else "Conexión aún no comprobada en esta sesión") }
        item { Action("PROBAR CONEXIÓN", !health.loading, vm::test); ErrorCard(health.error, vm::test); if (health.loading) Loading() }
        item { Action("CAMBIAR SERVIDOR") { confirm = "server" } }
        item { Text("Cuenta", style = MaterialTheme.typography.titleLarge); Text(user?.displayName.orEmpty()); Text(user?.username.orEmpty()); Text(user?.roles?.joinToString(" · ").orEmpty()); TextButton({ confirm = "logout" }) { Text("Cerrar sesión") } }
        item { Text("Tema", style = MaterialTheme.typography.titleLarge); listOf("system" to "Sistema", "light" to "Claro", "dark" to "Oscuro").forEach { (key, label) -> Row { RadioButton(prefs.theme == key, { root.theme(key) }); TextButton({ root.theme(key) }) { Text(label) } } } }
        if (admin) item { TextButton(business) { Text("Datos de la tienda") } }
        item { Text("Acerca de", style = MaterialTheme.typography.titleLarge); Text("Tienda POS ${BuildConfig.VERSION_NAME}"); Text("API soportada: v1") }
    }
    confirm?.let { action -> AlertDialog({ confirm = null }, title = { Text(if (action == "server") "Cambiar servidor" else "Cerrar sesión") }, text = { Text("Se cerrará la sesión y se eliminarán los datos de esta sesión en el dispositivo.") },
        confirmButton = { TextButton({ confirm = null; if (action == "server") root.changeServer() else root.logout() }) { Text("Continuar") } }, dismissButton = { TextButton({ confirm = null }) { Text("Cancelar") } }) }
}
@Composable fun ExportScreen(vm: SettingsViewModel, back: () -> Unit) {
    val state by vm.export.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var from by rememberSaveable { mutableStateOf("") }
    var to by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(state.value) { state.value?.let { shareFile(context, it, "text/csv"); vm.exported() } }
    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(back) { Text("Volver") }; Text("Finanzas diarias", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(from, { from = it }, label = { Text("Desde AAAA-MM-DD") }); OutlinedTextField(to, { to = it }, label = { Text("Hasta AAAA-MM-DD") })
        Action("Descargar y compartir CSV", !state.loading) { vm.export(buildMap { if (from.isNotBlank()) put("from", from); if (to.isNotBlank()) put("to", to) }) }
        ErrorCard(state.error); if (state.loading) Loading()
    }
}
