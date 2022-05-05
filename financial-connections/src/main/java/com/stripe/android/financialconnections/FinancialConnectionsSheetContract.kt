package com.stripe.android.financialconnections

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
import androidx.activity.result.contract.ActivityResultContract
import androidx.annotation.RestrictTo
import androidx.annotation.RestrictTo.Scope.LIBRARY_GROUP
import androidx.core.os.bundleOf
import com.stripe.android.financialconnections.model.FinancialConnectionsSession
import com.stripe.android.model.Token
import kotlinx.parcelize.Parcelize
import java.security.InvalidParameterException

@RestrictTo(LIBRARY_GROUP) class FinancialConnectionsSheetContract :
    ActivityResultContract<FinancialConnectionsSheetContract.Args, FinancialConnectionsSheetContract.Result>() {

    override fun createIntent(
        context: Context,
        input: Args
    ): Intent {
        return Intent(context, FinancialConnectionsSheetActivity::class.java).putExtra(
            EXTRA_ARGS,
            input
        )
    }

    override fun parseResult(
        resultCode: Int,
        intent: Intent?
    ): Result {
        return intent?.getParcelableExtra(EXTRA_RESULT) ?: Result.Failed(
            IllegalArgumentException("Failed to retrieve a ConnectionsSheetResult.")
        )
    }

    @RestrictTo(LIBRARY_GROUP) sealed class Args constructor(
        open val configuration: FinancialConnectionsSheet.Configuration,
    ) : Parcelable {

        @Parcelize
        @RestrictTo(LIBRARY_GROUP) data class Default(
            override val configuration: FinancialConnectionsSheet.Configuration
        ) : Args(configuration)

        @Parcelize
        @RestrictTo(LIBRARY_GROUP) data class ForToken(
            override val configuration: FinancialConnectionsSheet.Configuration
        ) : Args(configuration)

        fun validate() {
            if (configuration.financialConnectionsSessionClientSecret.isBlank()) {
                throw InvalidParameterException(
                    "The session client secret cannot be an empty string."
                )
            }
            if (configuration.publishableKey.isBlank()) {
                throw InvalidParameterException(
                    "The publishable key cannot be an empty string."
                )
            }
        }

        @RestrictTo(LIBRARY_GROUP) companion object {
            internal fun fromIntent(intent: Intent): Args? {
                return intent.getParcelableExtra(EXTRA_ARGS)
            }
        }
    }

    @RestrictTo(LIBRARY_GROUP) sealed class Result : Parcelable {
        /**
         * The customer completed the connections session.
         * @param financialConnectionsSession The financial connections session connected
         */
        @Parcelize
        @RestrictTo(LIBRARY_GROUP) data class Completed(
            val financialConnectionsSession: FinancialConnectionsSession,
            val token: Token? = null
        ) : Result()

        /**
         * The customer canceled the connections session attempt.
         */
        @Parcelize
        @RestrictTo(LIBRARY_GROUP) object Canceled : Result()

        /**
         * The connections session attempt failed.
         * @param error The error encountered by the customer.
         */
        @Parcelize
        @RestrictTo(LIBRARY_GROUP) data class Failed(
            val error: Throwable
        ) : Result()

        fun toBundle(): Bundle {
            return bundleOf(EXTRA_RESULT to this)
        }
    }

    private companion object {
        const val EXTRA_ARGS =
            "com.stripe.android.financialconnections.ConnectionsSheetContract.extra_args"
        private const val EXTRA_RESULT =
            "com.stripe.android.financialconnections.ConnectionsSheetContract.extra_result"
    }
}