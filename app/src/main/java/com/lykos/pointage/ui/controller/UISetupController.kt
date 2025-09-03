package com.lykos.pointage.ui.controller

import android.annotation.SuppressLint
import android.util.Log
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.lykos.pointage.R
import com.lykos.pointage.adapter.SafeZoneAdapter
import com.lykos.pointage.databinding.ActivityMainBinding
import com.lykos.pointage.ui.viewmodel.MainViewModel
import com.lykos.pointage.utils.NavigationHelper

class UISetupController(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val viewModel: MainViewModel
) {

    private lateinit var textCurrentArea: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: SafeZoneAdapter
    fun initializeAllViews() {
        initViews()
        setupToolbar()
        setupBottomSheet()
        setupNavigationButtons()
        setupSafeZonesRecyclerView()
    }

    private fun initViews() {
        textCurrentArea = binding.textCurrentArea
        recycler = binding.recyclerSafeZones
    }

    private fun setupToolbar() {
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar?.title = "Safe Zone Tracking"
    }

    private fun setupBottomSheet() {
        val bottomSheet = activity.findViewById<FrameLayout>(R.id.bottomSheet)
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = 150
        behavior.isHideable = false
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED
    }


    @SuppressLint("NotifyDataSetChanged")
    private fun setupSafeZonesRecyclerView() {
        adapter = SafeZoneAdapter { zone, index ->
            viewModel.selectZone(index)
            recycler.scrollToPosition(0)
        }
        recycler.adapter = adapter

        viewModel.safeZones.observe(activity) { zones ->
            Log.i("UISetupController", "Received ${zones.size} zones")

            if (zones.isEmpty()) {
                Log.d("UISetupController", "No zones to display")
                return@observe
            }
            adapter.submitList(zones)

            val selectedIndex = viewModel.selectedZoneIndex.value ?: 0
            adapter.selectedPosition = selectedIndex.coerceAtMost(zones.size - 1)
        }

        viewModel.selectedZoneIndex.observe(activity) { index ->
            Log.d("UISetupController", "Selected index: $index")
            adapter.selectedPosition = index
        }
    }

    private fun setupNavigationButtons() {

        binding.btnReportImages.setOnClickListener {
            NavigationHelper.navigateTo(activity, "DailyReportActivity")
        }
        binding.btnPV.setOnClickListener {
            NavigationHelper.navigateTo(activity, "PvReportActivity")
        }
        binding.btnDepend.setOnClickListener {
            NavigationHelper.navigateTo(activity, "ExpensesActivity")
        }
    }
}