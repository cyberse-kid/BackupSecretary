package com.clickwise.backupsecretary

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Menu
import android.view.MenuItem
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.clickwise.backupsecretary.databinding.ActivityMainBinding
import com.clickwise.backupsecretary.network.RetrofitClient
import com.clickwise.backupsecretary.ui.LeadsAdapter
import com.clickwise.backupsecretary.util.TokenManager
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var leadsAdapter: LeadsAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        setupButtons()
        loadLeads()
        checkAccessibilityService()
    }

    override fun onResume() {
        super.onResume()
        loadLeads()
    }

    private fun setupRecyclerView() {
        leadsAdapter = LeadsAdapter()
        binding.rvLeads.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = leadsAdapter
        }
    }

    private fun setupButtons() {
        binding.btnActivateBot.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        binding.swipeRefresh.setOnRefreshListener {
            loadLeads()
        }
    }

    private fun loadLeads() {
        lifecycleScope.launch {
            try {
                val token = TokenManager.getAccessToken(applicationContext) ?: return@launch
                val response = RetrofitClient.api.getLeads(
                    token = TokenManager.bearerToken(token)
                )
                if (response.isSuccessful) {
                    val leads = response.body()?.results ?: emptyList()
                    leadsAdapter.submitList(leads)
                    binding.tvEmptyState.visibility =
                        if (leads.isEmpty()) android.view.View.VISIBLE
                        else android.view.View.GONE
                }
            } catch (e: Exception) {
                // Error de red
            } finally {
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun checkAccessibilityService() {
        val isEnabled = isAccessibilityServiceEnabled()
        binding.tvBotStatus.text = if (isEnabled) "🟢 Bot activo" else "🔴 Bot inactivo"

        if (!isEnabled) {
            AlertDialog.Builder(this)
                .setTitle("Activar el bot")
                .setMessage("Para que el bot responda WhatsApp automáticamente, debes activar el servicio de accesibilidad.")
                .setPositiveButton("Activar") { _, _ ->
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
                .setNegativeButton("Después", null)
                .show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/.service.WhatsAppAccessibilityService"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(service)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_logout -> {
                lifecycleScope.launch {
                    TokenManager.clearTokens(applicationContext)
                    startActivity(Intent(this@MainActivity, LoginActivity::class.java))
                    finish()
                }
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}