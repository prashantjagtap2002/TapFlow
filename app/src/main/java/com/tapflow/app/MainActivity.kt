package com.tapflow.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.tapflow.app.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (savedInstanceState == null) {
            // Bug fix: fragments aren't findable by tag until the transaction is committed,
            // so use local references directly in the hide() calls to avoid NPE.
            val homeF = HomeFragment()
            val statsF = StatsFragment()
            val historyF = HistoryFragment()
            supportFragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, homeF, TAG_HOME)
                .add(R.id.fragmentContainer, statsF, TAG_STATS)
                .add(R.id.fragmentContainer, historyF, TAG_HISTORY)
                .hide(statsF)
                .hide(historyF)
                .commitNow()
        }

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            showTab(item.itemId)
            true
        }
    }

    private fun showTab(itemId: Int) {
        val selectedTag = when (itemId) {
            R.id.nav_counter -> TAG_HOME
            R.id.nav_stats -> TAG_STATS
            R.id.nav_history -> TAG_HISTORY
            else -> TAG_HOME
        }
        val tx = supportFragmentManager.beginTransaction()
        listOf(TAG_HOME, TAG_STATS, TAG_HISTORY).forEach { tag ->
            val f = supportFragmentManager.findFragmentByTag(tag) ?: return@forEach
            if (tag == selectedTag) tx.show(f) else tx.hide(f)
        }
        tx.commit()
    }

    companion object {
        private const val TAG_HOME = "home"
        private const val TAG_STATS = "stats"
        private const val TAG_HISTORY = "history"
    }
}
