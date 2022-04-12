package com.stripe.android.connections.example

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.stripe.android.connections.ConnectionsSheet
import com.stripe.android.connections.example.ConnectionsExampleViewEffect.OpenConnectionsSheetExample
import com.stripe.android.connections.example.ConnectionsExampleViewEffect.OpenConnectionsSheetForTokenExample
import com.stripe.android.connections.example.databinding.ActivityConnectionsExampleBinding

class ConnectionsExampleActivity : AppCompatActivity() {

    private val viewModel by viewModels<ConnectionsExampleViewModel>()

    /**
     * Instance used on regular connection flows.
     */
    private lateinit var connectionsSheet: ConnectionsSheet

    /**
     * Instance used when connecting bank account for token.
     */
    private lateinit var connectionsSheetForToken: ConnectionsSheet

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        with(ActivityConnectionsExampleBinding.inflate(layoutInflater)) {
            setContentView(root)
            setupViews()
            observeViews()
            observeState()
        }
    }

    private fun ActivityConnectionsExampleBinding.setupViews() {
        setSupportActionBar(toolbar)
        connectionsSheet = ConnectionsSheet.create(
            activity = this@ConnectionsExampleActivity,
            callback = viewModel::onConnectionsSheetResult
        )
        connectionsSheetForToken = ConnectionsSheet.createForToken(
            activity = this@ConnectionsExampleActivity,
            callback = viewModel::onConnectionsSheetForTokenResult
        )
    }

    private fun ActivityConnectionsExampleBinding.observeViews() {
        launchConnectionsSheet.setOnClickListener { viewModel.startLinkAccountSession() }
        launchConnectionsSheetForToken.setOnClickListener { viewModel.startLinkAccountSessionForToken() }
    }

    private fun ActivityConnectionsExampleBinding.observeState() {
        lifecycleScope.launchWhenStarted {
            viewModel.state.collect {
                bindState(it, this@observeState)
            }
        }
        lifecycleScope.launchWhenStarted {
            viewModel.viewEffect.collect {
                when (it) {
                    is OpenConnectionsSheetExample ->
                        connectionsSheet.present(it.configuration)
                    is OpenConnectionsSheetForTokenExample ->
                        connectionsSheetForToken.present(it.configuration)
                }
            }
        }
    }

    private fun bindState(
        connectionsExampleState: ConnectionsExampleState,
        viewBinding: ActivityConnectionsExampleBinding
    ) {
        viewBinding.status.text = connectionsExampleState.status
        viewBinding.launchConnectionsSheet.isEnabled = connectionsExampleState.loading.not()
        viewBinding.launchConnectionsSheetForToken.isEnabled = connectionsExampleState.loading.not()
    }
}
