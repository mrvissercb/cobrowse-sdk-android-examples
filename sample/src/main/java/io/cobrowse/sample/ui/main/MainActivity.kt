package io.cobrowse.sample.ui.main

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import io.cobrowse.sample.ui.InteractionTrackingActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.ActionMenuView
import androidx.appcompat.widget.LinearLayoutCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.view.MenuProvider
import androidx.core.view.postDelayed
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.NavigationUI
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.ShapeAppearanceModel
import io.cobrowse.CobrowseIO
import io.cobrowse.Session
import io.cobrowse.sample.R
import io.cobrowse.sample.data.CobrowseSessionDelegate
import io.cobrowse.sample.databinding.ActivityMainBinding
import io.cobrowse.sample.ui.CobrowseRedactionContainer
import io.cobrowse.sample.ui.CobrowseViewModelFactory
import io.cobrowse.sample.ui.ICobrowseRedactionContainer
import io.cobrowse.sample.ui.ToolbarNotchTreatment
import io.cobrowse.sample.ui.actionBarSize
import io.cobrowse.sample.ui.canPopNavigation
import io.cobrowse.sample.ui.dpToPx
import io.cobrowse.sample.ui.getThemeColor
import io.cobrowse.sample.ui.login.LoginActivity
import io.cobrowse.sample.ui.popNavigation
import io.cobrowse.sample.ui.VoiceChatIframeController

/**
 * Activity that hosts all fragments when user is logged in.
 */
class MainActivity : InteractionTrackingActivity(),
    CobrowseIO.Redacted,
    CobrowseIO.Unredacted,
    ICobrowseRedactionContainer by CobrowseRedactionContainer() {

    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding

    private lateinit var navHostFragmentMain: NavHostFragment
    private lateinit var navHostFragmentBottomSheet: NavHostFragment

    private var menu: Menu? = null
    private var menuBottomSheet: Menu? = null

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<LinearLayoutCompat>

    private var backPressedCallback: OnBackPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (navHostFragmentMain.popNavigation()) {
                return
            }
            if (navHostFragmentBottomSheet.popNavigation()) {
                return
            }
            when (bottomSheetBehavior.state) {
                BottomSheetBehavior.STATE_EXPANDED -> {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
                    return
                }
                BottomSheetBehavior.STATE_HALF_EXPANDED -> {
                    bottomSheetBehavior.state = BottomSheetBehavior.STATE_COLLAPSED
                    return
                }
                else -> updateBackPressedCallback()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        bottomSheetBehavior = BottomSheetBehavior.from(binding.transactionsBottomSheet)
        navHostFragmentMain = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navHostFragmentBottomSheet = supportFragmentManager.findFragmentById(R.id.bottom_sheet_nav_host_fragment) as NavHostFragment
        setContentView(binding.root)

        viewModel = ViewModelProvider(this, CobrowseViewModelFactory())
            .get(MainViewModel::class.java)

        if (!viewModel.isLoggedIn) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        setUpMenu()

        viewModel.cobrowseDelegate.current.observe(this@MainActivity, Observer {
            updateMainMenu()
            updateBottomSheetMenu()
        })

        setUpNavigation(savedInstanceState)
        setUpBottomSheetNavigation(savedInstanceState)
        setUpBottomSheet(savedInstanceState)

        setUpBackPressedCallback()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("bottomSheetBehaviorState", bottomSheetBehavior.state)
    }

    private fun setUpMenu() {
        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_main_host, menu)
                this@MainActivity.menu = menu
                updateMainMenu()
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.end_cobrowse_session) {
                    viewModel.endCobrowseSession()
                    return true
                }
                return false
            }
        })

        // The bottom sheet has its own toolbar and its own menu
        val actions = binding.menuBottomSheet
        actions.setOnMenuItemClickListener(object : ActionMenuView.OnMenuItemClickListener {
            override fun onMenuItemClick(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.end_cobrowse_session) {
                    viewModel.endCobrowseSession()
                    return true
                }
                return false
            }
        })
        menuInflater.inflate(R.menu.menu_main_host, actions.menu)
        actions.menu.findItem(R.id.end_cobrowse_session)?.let {
            it.icon?.constantState?.newDrawable()?.let { newDrawable ->
                DrawableCompat.setTint(newDrawable, ContextCompat.getColor(this, R.color.primaryColor))
                it.icon = newDrawable
            }
        }
        menuBottomSheet = actions.menu
    }

    private fun setUpNavigation(savedInstanceState: Bundle?) {
        val navController: NavController = navHostFragmentMain.navController
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        val toolbar = binding.toolbar
        setSupportActionBar(toolbar)

        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration)
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration)

        navController.addOnDestinationChangedListener { controller,  destination, arguments ->
            run {
                updateBottomSheetState(savedInstanceState)
                updateToolbarState()
            }
        }
    }

    private fun setUpBottomSheetNavigation(savedInstanceState: Bundle?) {
        val navController: NavController = navHostFragmentBottomSheet.navController
        val appBarConfiguration = AppBarConfiguration(navController.graph)
        val toolbar = binding.toolbarBottomSheet

        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration)
        navController.addOnDestinationChangedListener { controller,  destination, arguments ->
            run {
                updateBottomSheetState(savedInstanceState)
            }
        }
    }

    private fun setUpBackPressedCallback() {
        onBackPressedDispatcher.addCallback(this, backPressedCallback)

        navHostFragmentMain.navController.addOnDestinationChangedListener { _, _, _ ->
            run(MainActivity::updateBackPressedCallback)
        }
        navHostFragmentBottomSheet.navController.addOnDestinationChangedListener { _,  _, _ ->
            run(MainActivity::updateBackPressedCallback)
        }

        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                updateBackPressedCallback()
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) { }
        })
    }

    private fun setUpBottomSheet(savedInstanceState: Bundle?) {
        bottomSheetBehavior.addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                bottomSheetBehavior.isHideable = newState == BottomSheetBehavior.STATE_HIDDEN
                updateBottomSheetMenu()
            }
            override fun onSlide(bottomSheet: View, slideOffset: Float) { }
        })
        if (savedInstanceState != null) {
            updateBottomSheetState(savedInstanceState)
        } else {
            // Activity might be recreated on configuration changes,
            // and we want to animate the list only on its very first appearance.
            val duration = resources.getInteger(android.R.integer.config_longAnimTime).toLong() * 2
            binding.transactionsBottomSheet.postDelayed(duration) {
                bottomSheetBehavior.halfExpandedRatio = 0.4f
                bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
            }
        }
    }

    private fun updateBottomSheetState(savedInstanceState: Bundle?) {
        val mainDestinationId: Int? = navHostFragmentMain.navController.currentDestination?.id
        val mainStartDestinationId: Int = navHostFragmentMain.navController.graph.startDestinationId
        val bottomSheetDestinationId: Int? = navHostFragmentBottomSheet.navController.currentDestination?.id
        val bottomSheetStartDestinationId: Int = navHostFragmentBottomSheet.navController.graph.startDestinationId

        // The bottom sheet toolbar has extra margins when the transactions list is shown
        with(binding.toolbarBottomSheet.layoutParams as FrameLayout.LayoutParams) {
            if (bottomSheetDestinationId == bottomSheetStartDestinationId) {
                setMargins(resources.getDimension(R.dimen.list_horizontal_margin).toInt(), 0,
                           resources.getDimension(R.dimen.list_horizontal_margin).toInt(), 0)
            } else {
                setMargins(0, 0, 0, 0)
            }
        }

        if (mainDestinationId != mainStartDestinationId) {
            // No bottom sheet when the main navigation is active
            bottomSheetBehavior.isHideable = true
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HIDDEN
        } else if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_COLLAPSED) {
            // AndroidX automatically collapses the bottom sheet on configuration changes.
            // If that's the case, expand the bottom sheet to the last remembered state.
            if (savedInstanceState != null) {
                bottomSheetBehavior.state = savedInstanceState.getInt("bottomSheetBehaviorState",
                                                                      BottomSheetBehavior.STATE_HALF_EXPANDED)
            }
        } else if (bottomSheetBehavior.isHideable) {
            // If no navigation is active, just make sure the bottom sheet is shown
            bottomSheetBehavior.state = BottomSheetBehavior.STATE_HALF_EXPANDED
        }
    }

    private fun updateToolbarState() {
        val mainDestinationId: Int? = navHostFragmentMain.navController.currentDestination?.id
        val mainStartDestinationId: Int = navHostFragmentMain.navController.graph.startDestinationId

        if (mainDestinationId == mainStartDestinationId) {
            val actionBarSize: Int = this@MainActivity.actionBarSize()
            val visibleMenuCount =
                // The line below may return obsolete results
                //menu?.iterator()?.asSequence()?.count { it.isVisible } ?: 0
                if (menu?.findItem(R.id.end_cobrowse_session)?.isVisible == true) 2 else 1
            binding.toolbar.apply {
                background = MaterialShapeDrawable(ShapeAppearanceModel
                    .builder()
                    .setBottomEdge(ToolbarNotchTreatment(
                        toolbarHeight = actionBarSize.toFloat(),
                        toolbarMenuWidth = if (visibleMenuCount >= 2)
                            /* Two menu items: calculate its approximate width (refer to ActionMenuView.java) */
                            ((actionBarSize - 4.dpToPx()) * visibleMenuCount - 4.dpToPx()).toFloat()
                            /* One menu item: keep the toolbar square-ish */
                            else actionBarSize.toFloat(),
                        radius = 16.dpToPx().toFloat()))
                    .build())
                    .apply {
                        tintList = ColorStateList.valueOf(
                            this@MainActivity.getThemeColor(com.google.android.material.R.attr.colorPrimary))
                    }
            }
            supportActionBar?.setDisplayShowTitleEnabled(false)
        } else {
            binding.toolbar.background = ColorDrawable(ContextCompat.getColor(this@MainActivity, R.color.primaryColor))
            supportActionBar?.setDisplayShowTitleEnabled(true)
        }
    }

    private fun updateBackPressedCallback() {
        backPressedCallback.isEnabled =
            bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
                    || bottomSheetBehavior.state == BottomSheetBehavior.STATE_HALF_EXPANDED
                    || navHostFragmentBottomSheet.canPopNavigation()
                    || navHostFragmentMain.canPopNavigation()
    }

    private fun updateMainMenu() {
        val session: Session? = viewModel.cobrowseDelegate.current.value
        menu?.findItem(R.id.end_cobrowse_session)?.let {
            it.isVisible = session?.isActive == true
            updateToolbarState()
        }
    }

    private fun updateBottomSheetMenu() {
        val session: Session? = viewModel.cobrowseDelegate.current.value
        menuBottomSheet?.findItem(R.id.end_cobrowse_session)?.let {
            it.isVisible = session?.isActive == true
                    && bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED
        }
    }

    override fun redactedViews(): MutableList<View> {
        val redacted = mutableListOf<View>()
        redacted.addAll(collectRedactedViewsInFragments())
        return redacted
    }

    override fun unredactedViews(): MutableList<View> {
        return if (CobrowseSessionDelegate.isRedactionByDefaultEnabled(this)) {
            val unredacted = mutableListOf<View>()
            unredacted.addAll(collectUnredactedViewsInFragments())
            return unredacted
        } else {
            mutableListOf()
        }
    }
}