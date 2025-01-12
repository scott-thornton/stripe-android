package com.stripe.android.link.injection

import androidx.annotation.RestrictTo

/**
 * Identifies the customer-facing business name.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
const val MERCHANT_NAME = "merchantName"

/**
 * Identifies the email of the customer using the app, used to pre-fill the form.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
const val CUSTOMER_EMAIL = "customerEmail"

/**
 * Identifies the phone number of the customer using the app, used to pre-fill the form.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
const val CUSTOMER_PHONE = "customerPhone"

/**
 * Identifies the shipping address passed in from the customer, used to pre-fill address forms.
 */
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
const val INITIAL_FORM_VALUES_MAP = "initialFormValuesMap"

/**
 * Identifies the Stripe Intent being processed by Link.
 */
internal const val LINK_INTENT = "linkIntent"
