package com.first.meragaana

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.first.meragaana.databinding.ActivityMainBinding
import com.first.meragaana.ui.home.HomeFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val PERMISSION_REQUEST_CODE = 123
    private lateinit var navController: NavController
    private var permissionGranted = false
    private lateinit var appBarConfiguration: AppBarConfiguration

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set the toolbar as the action bar
        setSupportActionBar(binding.toolbar)

        setupNavigation()
        checkPermissions()
    }

    private fun setupNavigation() {
        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController

        // Define top level destinations
        appBarConfiguration = AppBarConfiguration(
            setOf(R.id.navigation_home, R.id.navigation_player)
        )

        // Setup the ActionBar with NavController
        setupActionBarWithNavController(navController, appBarConfiguration)

        // Setup Bottom Navigation
        binding.bottomNavigation.apply {
            setupWithNavController(navController)
            setOnItemSelectedListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.navigation_home -> {
                        if (navController.currentDestination?.id != R.id.navigation_home) {
                            val navOptions = NavOptions.Builder()
                                .setPopUpTo(R.id.navigation_home, true)
                                .build()
                            navController.navigate(R.id.navigation_home, null, navOptions)
                        }
                        true
                    }
                    R.id.navigation_player -> {
                        if (navController.currentDestination?.id != R.id.navigation_player) {
                            navController.navigate(R.id.navigation_player)
                        }
                        true
                    }
                    else -> false
                }
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        return NavigationUI.navigateUp(navController, appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private fun checkPermissions() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), PERMISSION_REQUEST_CODE)
        } else {
            permissionGranted = true
            notifyHomeFragmentPermissionGranted()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                permissionGranted = true
                notifyHomeFragmentPermissionGranted()
            }
        }
    }

    private fun notifyHomeFragmentPermissionGranted() {
        if (!permissionGranted) return

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment)
        navHostFragment?.childFragmentManager?.fragments?.forEach { fragment ->
            if (fragment is HomeFragment) {
                fragment.loadAudioFiles()
            }
        }
    }
}
