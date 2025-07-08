package io.cobrowse.sample.ui.login

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.annotation.StringRes
import io.cobrowse.sample.ui.InteractionTrackingActivity
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.view.MenuProvider
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import io.cobrowse.CobrowseIO
import io.cobrowse.sample.R
import io.cobrowse.sample.databinding.ActivityLoginBinding
import io.cobrowse.sample.ui.CobrowseViewModelFactory
import io.cobrowse.sample.ui.afterTextChanged
import io.cobrowse.sample.ui.main.MainActivity

/**
 * Activity with the login form.
 */
class LoginActivity : InteractionTrackingActivity(), CobrowseIO.Redacted {

    private lateinit var loginViewModel: LoginViewModel
    private lateinit var binding: ActivityLoginBinding
    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loginViewModel = ViewModelProvider(this, CobrowseViewModelFactory())
            .get(LoginViewModel::class.java)

        intent?.data?.let { loginViewModel.parseDeepLink(it) }

        if (loginViewModel.isLoggedIn) {
            // The app UI has been previously destroyed and then restarted
            onLoginSucceeded()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                menuInflater.inflate(R.menu.menu_login, menu)
                this@LoginActivity.menu = menu
                updateUiWithSession(loginViewModel.cobrowseDelegate.current.value)
            }
            override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                if (menuItem.itemId == R.id.end_cobrowse_session) {
                    loginViewModel.endCobrowseSession()
                    return true
                }
                return false
            }
        })

        val username = binding.username
        val password = binding.password
        val login = binding.login
        val loading = binding.loading

        loginViewModel.loginFormState.observe(this@LoginActivity, Observer {
            val loginState = it ?: return@Observer

            // disable login button unless both username / password is valid
            login.isEnabled = loginState.isDataValid

            if (loginState.usernameError != null) {
                username.error = getString(loginState.usernameError)
            }
            if (loginState.passwordError != null) {
                password.error = getString(loginState.passwordError)
            }
        })

        loginViewModel.loginResult.observe(this@LoginActivity, Observer {
            val loginResult = it ?: return@Observer

            username.isEnabled = true
            password.isEnabled = true
            if (loginResult.error != null) {
                loading.visibility = View.GONE
                showLoginFailed(loginResult.error)
            }
            if (loginResult.success != null) {
                onLoginSucceeded()
            }
        })

        username.afterTextChanged {
            loginViewModel.loginDataChanged(
                username.text.toString(),
                password.text.toString()
            )
        }

        password.apply {
            afterTextChanged {
                loginViewModel.loginDataChanged(
                    username.text.toString(),
                    password.text.toString()
                )
            }

            setOnEditorActionListener { _, actionId, _ ->
                when (actionId) {
                    EditorInfo.IME_ACTION_DONE ->
                        tryLogIn(username, password, loading)
                }
                false
            }

            login.setOnClickListener {
                tryLogIn(username, password, loading)
            }
        }

        loginViewModel.cobrowseDelegate.current.observe(this@LoginActivity, Observer {
            updateUiWithSession(it)
        })

        val privacyPolicy = binding.privacyPolicy
        privacyPolicy.setOnClickListener {
            CustomTabsIntent.Builder()
                .build()
                .launchUrl(this, Uri.parse(getString(R.string.privacy_policy_url)))
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.data?.let { loginViewModel.parseDeepLink(it) }
    }

    private fun tryLogIn(username: EditText, password: EditText, loading: ProgressBar) {
        loading.visibility = View.VISIBLE
        username.isEnabled = false
        password.isEnabled = false
        loginViewModel.login(username.text.toString(), password.text.toString())
    }

    private fun onLoginSucceeded() {
        startActivity(Intent(this, MainActivity::class.java))
        //Complete and destroy login activity once successful
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun showLoginFailed(@StringRes errorString: Int) {
        Toast.makeText(applicationContext, errorString, Toast.LENGTH_SHORT).show()
    }

    private fun updateUiWithSession(session: io.cobrowse.Session?) {
        menu?.findItem(R.id.end_cobrowse_session)?.let {
            it.isVisible = session?.isActive == true
        }
    }

    override fun redactedViews(): MutableList<View> {
        // The view binding is not initialized if LoginActivity was launched after user has already
        // logged in.
        // Return no views to be redacted as there isn't a view to be screen-shared anyway.
        return if (this::binding.isInitialized)
            mutableListOf(binding.username, binding.password)
            else mutableListOf()
    }
}
