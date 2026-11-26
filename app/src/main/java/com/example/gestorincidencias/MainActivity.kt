package com.example.gestorincidencias

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.gestorincidencias.ui.theme.GestorIncidenciasTheme
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

private val Context.dataStore by preferencesDataStore(name = "incidencias_prefs")

class MainActivity : ComponentActivity() {

    data class Incidencia(
        @SerializedName("id") val id: Int,
        @SerializedName("equipo") val equipo: String,
        @SerializedName("descripcion") val descripcion: String,
        @SerializedName("prioridad") val prioridad: String
    )

    interface IncidenciasApi {
        @GET("api/incidencias")
        suspend fun obtenerIncidencias(): List<Incidencia>
    }

    private val claveSeleccionada = stringPreferencesKey("incidencia_seleccionada")

    private fun observarIncidenciaGuardada(context: Context): Flow<String> {
        return context.dataStore.data.map { prefs ->
            prefs[claveSeleccionada] ?: ""
        }
    }

    private suspend fun guardarIncidencia(context: Context, texto: String) {
        context.dataStore.edit { prefs ->
            prefs[claveSeleccionada] = texto
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val api: IncidenciasApi = Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8000/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(IncidenciasApi::class.java)

        setContent {
            GestorIncidenciasTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    PantallaIncidencias(
                        api = api,
                        observarIncidencia = { observarIncidenciaGuardada(it) },
                        guardarIncidencia = { ctx, text -> guardarIncidencia(ctx, text) },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun PantallaIncidencias(
    api: MainActivity.IncidenciasApi,
    observarIncidencia: (Context) -> Flow<String>,
    guardarIncidencia: suspend (Context, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var incidencias by remember { mutableStateOf<List<MainActivity.Incidencia>>(emptyList()) }
    var mensajeError by remember { mutableStateOf<String?>(null) }
    var cargando by remember { mutableStateOf(false) }

    val incidenciaGuardada by remember(context) { observarIncidencia(context) }
        .collectAsState(initial = "")

    val textoSeleccionada = incidenciaGuardada.ifBlank { "Ninguna" }

    fun cargarDatos() {
        scope.launch {
            cargando = true
            mensajeError = null
            try {
                incidencias = api.obtenerIncidencias()
            } catch (e: Exception) {
                mensajeError = "Error al cargar incidencias: ${e.localizedMessage ?: "sin conexión"}"
            } finally {
                cargando = false
            }
        }
    }

    LaunchedEffect(Unit) {
        cargarDatos()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Incidencias pendientes",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Text(
                text = "Incidencia seleccionada: $textoSeleccionada",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { cargarDatos() },
            enabled = !cargando,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (cargando) "Cargando..." else "Cargar / Recargar incidencias")
        }

        if (mensajeError != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = mensajeError!!,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (cargando && incidencias.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (incidencias.isEmpty() && mensajeError == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No hay incidencias registradas.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(incidencias, key = { it.id }) { incidencia ->
                    val formatoGuardado = "${incidencia.id} - ${incidencia.equipo}"
                    val estaSeleccionada = incidenciaGuardada == formatoGuardado

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (estaSeleccionada) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        ),
                        border = if (estaSeleccionada) {
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(
                                    text = "ID: ${incidencia.id} | Equipo: ${incidencia.equipo}",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(text = "Descripción: ${incidencia.descripcion}")
                                Text(text = "Prioridad: ${incidencia.prioridad}")
                            }

                            Button(
                                onClick = {
                                    scope.launch {
                                        guardarIncidencia(context, formatoGuardado)
                                    }
                                }
                            ) {
                                Text(if (estaSeleccionada) "Seleccionada" else "Seleccionar")
                            }
                        }
                    }
                }
            }
        }
    }
}
