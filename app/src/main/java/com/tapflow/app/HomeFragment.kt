package com.tapflow.app

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tapflow.app.databinding.FragmentHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: AppPreferences
    private lateinit var buildStore: BuildStore
    private val githubClient = GitHubClient(
        owner = "prashantjagtap2002",
        repo = "TapFlow",
        workflowFile = "build.yml"
    )
    private val actionsUrl = "https://github.com/prashantjagtap2002/TapFlow/actions"

    private var tapCount = 0
    private var sessionStartTime = 0L
    private var isSessionActive = false

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerTick = object : Runnable {
        override fun run() {
            // Bug fix: guard against _binding being null if the view was destroyed
            // while the handler was still queued.
            if (!isSessionActive || _binding == null) return
            val elapsed = (System.currentTimeMillis() - sessionStartTime) / 1000
            binding.timerText.text = formatTime(elapsed)
            timerHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = AppPreferences(requireContext())
        buildStore = BuildStore(requireContext())

        setupBuildSection()
        refreshBuildStatus()

        // Bug fix: restore state after rotation so in-progress sessions survive.
        savedInstanceState?.let {
            tapCount = it.getInt(KEY_TAP_COUNT, 0)
            isSessionActive = it.getBoolean(KEY_SESSION_ACTIVE, false)
            sessionStartTime = it.getLong(KEY_SESSION_START, 0L)
        }

        if (isSessionActive) {
            timerHandler.post(timerTick)
        } else {
            binding.timerText.text = formatTime(0)
        }

        updateCountDisplay()
        refreshRecordBadge()

        // Bug fix: @SuppressLint added because returning true from onTouchListener
        // blocks the accessibility framework — this is intentional here since we handle
        // the tap entirely in ACTION_UP ourselves.
        binding.tapButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    v.animate().scaleX(0.90f).scaleY(0.90f).setDuration(80).start()
                    v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
                MotionEvent.ACTION_UP -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                    handleTap()
                }
                MotionEvent.ACTION_CANCEL -> {
                    v.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
                }
            }
            true
        }

        binding.resetButton.setOnClickListener {
            saveAndReset()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Bug fix: persist in-progress session state across rotation.
        outState.putInt(KEY_TAP_COUNT, tapCount)
        outState.putBoolean(KEY_SESSION_ACTIVE, isSessionActive)
        outState.putLong(KEY_SESSION_START, sessionStartTime)
    }

    private fun handleTap() {
        if (!isSessionActive) {
            isSessionActive = true
            sessionStartTime = System.currentTimeMillis()
            timerHandler.post(timerTick)
        }
        tapCount++
        updateCountDisplay()
        refreshRecordBadge()
    }

    private fun saveAndReset() {
        if (tapCount > 0) {
            val elapsed = (System.currentTimeMillis() - sessionStartTime) / 1000
            val date = SimpleDateFormat("MMM d, yyyy h:mm a", Locale.getDefault()).format(Date())
            prefs.addSession(tapCount, elapsed, date)
            prefs.totalTaps += tapCount
            if (tapCount > prefs.bestSession) prefs.bestSession = tapCount
            prefs.sessionCount++
        }
        tapCount = 0
        isSessionActive = false
        timerHandler.removeCallbacks(timerTick)
        updateCountDisplay()
        binding.timerText.text = formatTime(0)
        refreshRecordBadge()
    }

    private fun updateCountDisplay() {
        binding.tapCountText.text = tapCount.toString()
    }

    private fun refreshRecordBadge() {
        val best = prefs.bestSession
        binding.recordBadge.apply {
            when {
                tapCount == 0 -> visibility = View.INVISIBLE
                tapCount > best -> {
                    visibility = View.VISIBLE
                    text = getString(R.string.new_record)
                }
                tapCount == best && best > 0 -> {
                    visibility = View.VISIBLE
                    text = getString(R.string.matching_record, best)
                }
                else -> visibility = View.INVISIBLE
            }
        }
    }

    private fun setupBuildSection() {
        binding.buildInstalledText.text = getString(R.string.home_build_installed_label) +
                " v${BuildConfig.VERSION_NAME}"

        val savedPat = buildStore.getPat()
        if (savedPat.isNotBlank()) binding.buildPatEdit.setText(savedPat)

        binding.buildSavePatButton.setOnClickListener { saveGithubPat() }
        binding.buildPushButton.setOnClickListener { triggerBuild() }
        binding.buildRefreshButton.setOnClickListener { refreshBuildStatus() }
        binding.buildOpenActionsButton.setOnClickListener { openActionsInBrowser() }
    }

    private fun refreshBuildStatus() {
        val loadingText = getString(R.string.home_build_loading)
        binding.buildLatestText.text = getString(R.string.home_build_latest_label) + " $loadingText"
        binding.buildLastRunText.text = getString(R.string.home_build_last_run_label) + " $loadingText"

        val pat = buildStore.getPat().ifBlank { null }

        viewLifecycleOwner.lifecycleScope.launch {
            val (releaseResult, runResult) = withContext(Dispatchers.IO) {
                val r = githubClient.getLatestRelease()
                val w = githubClient.getLatestRun(pat)
                r to w
            }
            if (_binding == null) return@launch

            when (releaseResult) {
                is GitHubClient.Result.Ok -> {
                    val rel = releaseResult.value
                    if (rel == null) {
                        binding.buildLatestText.text = getString(R.string.home_build_latest_label) + " —"
                    } else {
                        val installed = BuildConfig.VERSION_NAME
                        val tag = rel.tagName.trimStart('v')
                        val badge = if (tag != installed)
                            getString(R.string.home_build_update_available)
                        else
                            getString(R.string.home_build_up_to_date)
                        binding.buildLatestText.text =
                            getString(R.string.home_build_latest_label) + " ${rel.tagName}  $badge"
                    }
                }
                is GitHubClient.Result.Err ->
                    binding.buildLatestText.text = getString(R.string.home_build_latest_label) +
                            " " + getString(R.string.home_build_status_error, releaseResult.message)
            }

            when (runResult) {
                is GitHubClient.Result.Ok -> {
                    val run = runResult.value
                    if (run == null) {
                        binding.buildLastRunText.text =
                            getString(R.string.home_build_last_run_label) + " " +
                                    getString(R.string.home_build_status_no_runs)
                    } else {
                        val statusStr = when {
                            run.status == "in_progress" || run.status == "queued" ->
                                getString(R.string.home_build_status_running, run.displayTitle)
                            run.conclusion == "success" ->
                                getString(R.string.home_build_status_success, run.displayTitle)
                            run.conclusion == "failure" ->
                                getString(R.string.home_build_status_failed, run.displayTitle, run.headBranch)
                            run.conclusion == "cancelled" ->
                                getString(R.string.home_build_status_cancelled, run.displayTitle)
                            else ->
                                getString(R.string.home_build_status_queued, run.displayTitle)
                        }
                        binding.buildLastRunText.text =
                            getString(R.string.home_build_last_run_label) + " $statusStr"
                    }
                }
                is GitHubClient.Result.Err ->
                    binding.buildLastRunText.text = getString(R.string.home_build_last_run_label) +
                            " " + getString(R.string.home_build_status_error, runResult.message)
            }
        }
    }

    private fun triggerBuild() {
        val pat = buildStore.getPat()
        if (pat.isBlank()) {
            Toast.makeText(requireContext(), getString(R.string.home_build_pat_missing), Toast.LENGTH_SHORT).show()
            return
        }
        binding.buildPushButton.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { githubClient.triggerWorkflow(pat) }
            if (_binding == null) return@launch
            when (result) {
                is GitHubClient.Result.Ok -> {
                    Toast.makeText(requireContext(), getString(R.string.home_build_push_triggered), Toast.LENGTH_SHORT).show()
                    delay(3000)
                    refreshBuildStatus()
                }
                is GitHubClient.Result.Err ->
                    Toast.makeText(requireContext(),
                        getString(R.string.home_build_push_failed, result.message),
                        Toast.LENGTH_LONG).show()
            }
            if (_binding != null) binding.buildPushButton.isEnabled = true
        }
    }

    private fun saveGithubPat() {
        val pat = binding.buildPatEdit.text?.toString().orEmpty()
        buildStore.savePat(pat)
        Toast.makeText(requireContext(), getString(R.string.home_build_pat_saved), Toast.LENGTH_SHORT).show()
        refreshBuildStatus()
    }

    private fun openActionsInBrowser() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(actionsUrl)))
    }

    private fun formatTime(seconds: Long) = "%d:%02d".format(seconds / 60, seconds % 60)

    override fun onDestroyView() {
        super.onDestroyView()
        timerHandler.removeCallbacks(timerTick)
        _binding = null
    }

    companion object {
        private const val KEY_TAP_COUNT = "key_tap_count"
        private const val KEY_SESSION_ACTIVE = "key_session_active"
        private const val KEY_SESSION_START = "key_session_start"
    }
}
