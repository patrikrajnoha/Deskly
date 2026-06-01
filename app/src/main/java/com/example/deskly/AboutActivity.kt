package com.example.deskly

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        // všetky sekcie (header, content, arrow)
        setupCollapsible(R.id.headerAbout,    R.id.contentAbout,    R.id.arrowAbout,    startExpanded = false)
        setupCollapsible(R.id.headerControls, R.id.contentControls, R.id.arrowControls, startExpanded = false)
        setupCollapsible(R.id.headerFeatures, R.id.contentFeatures, R.id.arrowFeatures, startExpanded = false)
        setupCollapsible(R.id.headerSecurity, R.id.contentSecurity, R.id.arrowSecurity, startExpanded = false)
        setupCollapsible(R.id.headerStatus,   R.id.contentStatus,   R.id.arrowStatus,   startExpanded = false)
        setupCollapsible(R.id.headerUntested, R.id.contentUntested, R.id.arrowUntested, startExpanded = false)

        // ✅ Q&A sekcia (pozor: v XML je "Q&A" ako text, ale ID sú headerQna/contentQna/arrowQna)
        setupCollapsible(R.id.headerQna,      R.id.contentQna,      R.id.arrowQna,      startExpanded = false)

        setupCollapsible(R.id.headerAuthor,   R.id.contentAuthor,   R.id.arrowAuthor,   startExpanded = false)
    }

    private fun setupCollapsible(headerId: Int, contentId: Int, arrowId: Int, startExpanded: Boolean) {
        val header = findViewById<LinearLayout>(headerId)
        val content = findViewById<View>(contentId)
        val arrow = findViewById<TextView>(arrowId)

        fun setExpanded(expanded: Boolean) {
            content.visibility = if (expanded) View.VISIBLE else View.GONE
            arrow.text = if (expanded) "⌃" else "⌄"
        }

        setExpanded(startExpanded)

        header.setOnClickListener {
            val isOpen = content.visibility == View.VISIBLE
            setExpanded(!isOpen)
        }
    }
}
