package com.sel2in.suzysnooze

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.sel2in.suzysnooze.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.about_title)

        binding.txtVersionAbout.text = getString(R.string.build_version_label, BuildConfig.BUILD_VERSION)
        binding.txtBuildDateAbout.text = getString(R.string.build_date_label, BuildConfig.BUILD_DATE)

        binding.btnCode.setOnClickListener {
            openUrl("https://github.com/tgkprog/androidSuzySnooze")
        }

        binding.btnWebsite.setOnClickListener {
            openUrl("https://sel2in.com/news")
        }

        binding.btnNews.setOnClickListener {
            openUrl("https://sel2in.com/news/")
        }

        binding.btnAds.setOnClickListener {
            openUrl("https://sel2in.com/news/snooze/ads")
        }

        binding.btnCloseAbout.setOnClickListener { finish() }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.secondary_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            R.id.menu_main -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        try {
            startActivity(intent)
        } catch (ex: Exception) {
            Toast.makeText(this, ex.localizedMessage ?: getString(R.string.error_opening_link), Toast.LENGTH_SHORT).show()
        }
    }
}
