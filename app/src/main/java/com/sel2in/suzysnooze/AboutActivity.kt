package com.sel2in.suzysnooze

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.sel2in.suzysnooze.databinding.ActivityAboutBinding

class AboutActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAboutBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAboutBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.txtVersionAbout.text = getString(R.string.build_version_label, BuildConfig.BUILD_VERSION)
        binding.txtBuildDateAbout.text = getString(R.string.build_date_label, BuildConfig.BUILD_DATE)

        binding.btnWebsiteAbout.setOnClickListener {
            openWebsite()
        }

        binding.btnCloseAbout.setOnClickListener { finish() }
    }

    private fun openWebsite() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WEB_URL))
        try {
            startActivity(intent)
        } catch (ex: Exception) {
            // Show simple fallback toast using context extension
            android.widget.Toast.makeText(this, ex.localizedMessage ?: getString(R.string.error_opening_link), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val WEB_URL = "https://sel2in.com/snooze/"
    }
}
