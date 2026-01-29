package com.scout.routeplanner.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.scout.routeplanner.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ConfigFragment())
                .commit()
        }
    }

    fun showResults() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ResultsFragment())
            .addToBackStack(null)
            .commit()
    }

    fun showProgress() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, ProgressFragment())
            .addToBackStack(null)
            .commit()
    }
}
