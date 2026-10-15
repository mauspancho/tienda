package com.mauspancho.tiendapos.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mauspancho.tiendapos.ui.components.*

@Composable fun AuthScreen(vm: AuthViewModel, server: String, notice: String?, change: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var username by rememberSaveable { mutableStateOf("") }
    // Password stays only in memory; it never enters SavedStateHandle, preferences or logs.
    var password by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text("Tienda POS", style = MaterialTheme.typography.headlineLarge)
        Text("Iniciar sesión", style = MaterialTheme.typography.titleLarge)
        Text("Acceso con usuario ADMIN o CAJERO de una tienda. La administración de plataforma se realiza en la web.", style = MaterialTheme.typography.bodySmall)
        Text(server, style = MaterialTheme.typography.bodyMedium)
        notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text("Usuario") }, singleLine = true, enabled = !state.loading)
        OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth(), label = { Text("Contraseña") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = !state.loading)
        ErrorCard(state.error)
        if (state.loading) Loading()
        Action("INICIAR SESIÓN", !state.loading && username.isNotBlank() && password.isNotEmpty()) { vm.login(username, password); password = "" }
        TextButton(change, enabled = !state.loading) { Text("Cambiar servidor") }
    }
}
