package com.tapflow.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.tapflow.app.databinding.FragmentStatsBinding

class StatsFragment : Fragment() {

    private var _binding: FragmentStatsBinding? = null
    private val binding get() = _binding!!
    private lateinit var prefs: AppPreferences

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentStatsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = AppPreferences(requireContext())

        binding.clearButton.setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(getString(R.string.clear_confirm_title))
                .setMessage(getString(R.string.clear_confirm_message))
                .setPositiveButton(getString(R.string.clear_confirm_yes)) { _, _ ->
                    prefs.clearAll()
                    refreshStats()
                }
                .setNegativeButton(getString(R.string.cancel), null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStats()
    }

    private fun refreshStats() {
        val total = prefs.totalTaps
        val best = prefs.bestSession
        val count = prefs.sessionCount
        val avg = if (count > 0) total / count else 0

        binding.totalTapsValue.text = "%,d".format(total)
        binding.bestSessionValue.text = best.toString()
        binding.totalSessionsValue.text = count.toString()
        binding.averageValue.text = avg.toString()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
