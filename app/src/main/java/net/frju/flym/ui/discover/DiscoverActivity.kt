package net.frju.flym.ui.discover

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.URLUtil
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import kotlinx.android.synthetic.main.alert_dialog_username_password.view.*
import net.fred.feedex.R
import net.frju.flym.App
import net.frju.flym.data.entities.Feed
import net.frju.flym.data.entities.SearchFeedResult
import net.frju.flym.service.FetcherService
import net.frju.flym.utils.setupTheme
import org.jetbrains.anko.design.snackbar
import org.jetbrains.anko.doAsync
import org.jetbrains.anko.sdk21.listeners.onClick
import org.jetbrains.anko.sdk21.listeners.onEditorAction
import org.jetbrains.anko.sdk21.listeners.textChangedListener
import org.jetbrains.anko.startActivity
import org.jetbrains.anko.uiThread
import java.util.Timer
import java.util.TimerTask


class DiscoverActivity : AppCompatActivity(), FeedManagementInterface {

    companion object {

        @JvmStatic
        fun newInstance(context: Context) = context.startActivity<DiscoverActivity>()

        @JvmStatic
        fun newInstance(context: Context, query: String) =
                context.startActivity<DiscoverActivity>(FeedSearchFragment.ARG_QUERY to query)

        private const val MAX_AUTHENTICATION_ATTEMPTS = 10
    }

    private var searchInput: AutoCompleteTextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setupTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed_search)
        this.initSearchInputs()

        val query = savedInstanceState?.getString(FeedSearchFragment.ARG_QUERY) ?: intent.getStringExtra(FeedSearchFragment.ARG_QUERY)
        if (!query.isNullOrEmpty()) {
            this.searchForFeed(query)
        } else {
            this.goDiscover()
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putString(FeedSearchFragment.ARG_QUERY, searchInput?.text?.toString())
    }

    private fun initSearchInputs() {
        var timer = Timer()
        searchInput = this.findViewById(R.id.et_search_input)
        searchInput?.let { searchInput ->

            // Handle IME Options Search Action
            searchInput.onEditorAction { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val term = searchInput.text.toString().trim()
                    if (term.isNotEmpty()) {
                        goSearch(term)
                    }
                    true
                } else
                    false
            }

            // Handle search after N ms pause after input
            searchInput.textChangedListener {
                afterTextChanged {
                    val term = searchInput.text.toString().trim()
                    if (term.isNotEmpty()) {
                        timer.cancel()
                        timer = Timer()
                        timer.schedule(object : TimerTask() {
                            override fun run() {
                                goSearch(term)
                            }
                        }, 400)
                    }
                }
            }

            // Handle manually adding URL
            this.findViewById<Button>(R.id.btn_add_feed).onClick {
                val text = searchInput.text.toString()
                if (URLUtil.isNetworkUrl(text)) {
                    addFeed(searchInput, text, text)
                    searchInput.setText("")
                }
            }
        }
    }

    private fun goSearch(query: String) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fcv_discover_fragments)
        if (currentFragment is FeedSearchFragment) {
            currentFragment.search(query)
        } else {
            val fragment = FeedSearchFragment.newInstance(query)
            supportFragmentManager
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.fcv_discover_fragments, fragment, FeedSearchFragment.TAG)
                    .addToBackStack(DiscoverFragment.TAG)
                    .commitAllowingStateLoss()
        }
    }

    private fun goDiscover() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fcv_discover_fragments)
        if (currentFragment !is DiscoverFragment) {
            val fragment = DiscoverFragment.newInstance()
            supportFragmentManager
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.fcv_discover_fragments, fragment, DiscoverFragment.TAG)
                    .commitAllowingStateLoss()
        }
    }

    override fun searchForFeed(query: String) {
        searchInput?.setText(query)
    }

    override fun addFeed(view: View, title: String, link: String) {
        addFeedWithAuthCheck(view, title, link)
    }

    private fun addFeedWithAuthCheck(view: View, title: String, link: String, username: String? = null, password:String? = null) {
        doAsync {
            FetcherService.createCall(link, username, password).execute().use { response ->
                if (response.code == 401) {
                    uiThread {
                        showAuthenticationAlertDialog(view, link, title)
                    }
                } else {
                    val feedToAdd = Feed(link = link, title = title, username = username, password = password)
                    App.db.feedDao().insert(feedToAdd)
                    uiThread {
                        view.snackbar(R.string.feed_added)
                    }
                }
            }
        }
    }

    private fun showAuthenticationAlertDialog(view: View, link: String, title: String) {
        view?.let { _ ->
            val dialogLayout = layoutInflater.inflate(R.layout.alert_dialog_username_password, null)
            var builder = AlertDialog.Builder(view.context)
            with (builder) {
                setTitle(R.string.authentication_dialog_title).setMessage(link)
                setNegativeButton(android.R.string.no) { dialogInterface, _ ->
                    dialogInterface.dismiss()
                }
                setPositiveButton(android.R.string.yes) { _: DialogInterface, _: Int ->
                    addFeedWithAuthCheck(view, title, link, dialogLayout.feed_username.text.toString(), dialogLayout.feed_password.text.toString())
                }
                setView(dialogLayout).show()
            }
        }
    }

    override fun deleteFeed(view: View, feed: SearchFeedResult) {
        doAsync {
            App.db.feedDao().deleteByLink(feed.link)
        }
    }

    override fun previewFeed(view: View, feed: SearchFeedResult) {
        TODO("Not yet implemented")
    }
}