package com.stripe.android.link.ui.wallet

import androidx.lifecycle.Lifecycle
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import com.google.common.truth.Truth.assertThat
import com.stripe.android.core.Logger
import com.stripe.android.core.injection.Injectable
import com.stripe.android.link.LinkActivityContract
import com.stripe.android.link.LinkActivityResult
import com.stripe.android.link.LinkScreen
import com.stripe.android.link.account.LinkAccountManager
import com.stripe.android.link.confirmation.ConfirmationManager
import com.stripe.android.link.confirmation.PaymentConfirmationCallback
import com.stripe.android.link.injection.SignedInViewModelSubcomponent
import com.stripe.android.link.model.LinkAccount
import com.stripe.android.link.model.Navigator
import com.stripe.android.link.model.PaymentDetailsFixtures
import com.stripe.android.link.model.StripeIntentFixtures
import com.stripe.android.link.ui.ErrorMessage
import com.stripe.android.link.ui.PrimaryButtonState
import com.stripe.android.link.ui.cardedit.CardEditViewModel
import com.stripe.android.model.ConfirmPaymentIntentParams
import com.stripe.android.model.ConfirmStripeIntentParams
import com.stripe.android.model.ConsumerPaymentDetails
import com.stripe.android.model.PaymentMethodCreateParams
import com.stripe.android.payments.paymentlauncher.PaymentResult
import com.stripe.android.ui.core.injection.NonFallbackInjector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argWhere
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.clearInvocations
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.spy
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner
import javax.inject.Provider

@ExperimentalCoroutinesApi
@RunWith(RobolectricTestRunner::class)
class WalletViewModelTest {
    private val args = mock<LinkActivityContract.Args>()
    private lateinit var linkAccountManager: LinkAccountManager
    private val navigator = mock<Navigator>()
    private val confirmationManager = mock<ConfirmationManager>()
    private val logger = Logger.noop()

    @Before
    fun before() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        whenever(args.stripeIntent).thenReturn(StripeIntentFixtures.PI_SUCCEEDED)
        val mockLinkAccount = mock<LinkAccount>().apply {
            whenever(clientSecret).thenReturn(CLIENT_SECRET)
            whenever(email).thenReturn("email@stripe.com")
        }
        linkAccountManager = mock<LinkAccountManager>().apply {
            whenever(linkAccount).thenReturn(MutableStateFlow(mockLinkAccount))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `On initialization start collecting CardEdit result`() = runTest {
        createViewModel()

        verify(navigator).getResultFlow<CardEditViewModel.Result>(any())
    }

    @Test
    fun `On initialization payment details are loaded`() = runTest {
        val card1 = mock<ConsumerPaymentDetails.Card>()
        val card2 = mock<ConsumerPaymentDetails.Card>()
        val paymentDetails = mock<ConsumerPaymentDetails>()
        whenever(paymentDetails.paymentDetails).thenReturn(listOf(card1, card2))

        whenever(linkAccountManager.listPaymentDetails())
            .thenReturn(Result.success(paymentDetails))

        val viewModel = createViewModel()

        assertThat(viewModel.paymentDetailsList.value).containsExactly(card1, card2)
    }

    @Test
    fun `On initialization when no payment details then navigate to AddPaymentMethod`() = runTest {
        val response = mock<ConsumerPaymentDetails>()
        whenever(response.paymentDetails).thenReturn(emptyList())

        whenever(linkAccountManager.listPaymentDetails())
            .thenReturn(Result.success(response))

        createViewModel()

        verify(navigator).navigateTo(argWhere { it is LinkScreen.PaymentMethod }, eq(true))
    }

    @Test
    fun `On initialization when prefilledCardParams is not null then navigate to AddPaymentMethod`() =
        runTest {
            whenever(args.prefilledCardParams).thenReturn(mock())
            whenever(linkAccountManager.listPaymentDetails())
                .thenReturn(Result.success(PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS))

            createViewModel()

            verify(navigator).navigateTo(
                argWhere {
                    it.route == LinkScreen.PaymentMethod(true).route
                },
                eq(false)
            )
        }

    @Test
    fun `onSelectedPaymentDetails starts payment confirmation`() {
        val paymentDetails = PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS.paymentDetails.first()
        val viewModel = createViewModel()

        viewModel.onItemSelected(paymentDetails)
        viewModel.onConfirmPayment()

        val paramsCaptor = argumentCaptor<ConfirmStripeIntentParams>()
        verify(confirmationManager).confirmStripeIntent(paramsCaptor.capture(), any())

        assertThat(paramsCaptor.firstValue).isEqualTo(
            ConfirmPaymentIntentParams.createWithPaymentMethodCreateParams(
                PaymentMethodCreateParams.createLink(
                    paymentDetails.id,
                    CLIENT_SECRET
                ),
                StripeIntentFixtures.PI_SUCCEEDED.clientSecret!!
            )
        )
    }

    @Test
    fun `onItemSelected updates selected item`() {
        val paymentDetails = PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS.paymentDetails.first()
        val viewModel = createViewModel()

        viewModel.onItemSelected(paymentDetails)

        assertThat(viewModel.selectedItem.value).isEqualTo(paymentDetails)
    }

    @Test
    fun `when selected item is removed then default item is selected`() = runTest {
        val deletedPaymentDetails =
            PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS.paymentDetails[1]
        val viewModel = createViewModel()
        viewModel.onItemSelected(deletedPaymentDetails)

        assertThat(viewModel.selectedItem.value).isEqualTo(deletedPaymentDetails)

        whenever(linkAccountManager.deletePaymentDetails(anyOrNull()))
            .thenReturn(Result.success(Unit))
        whenever(linkAccountManager.listPaymentDetails())
            .thenReturn(
                Result.success(
                    PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS.copy(
                        paymentDetails = PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS.paymentDetails
                            .filter { it != deletedPaymentDetails }
                    )
                )
            )

        viewModel.deletePaymentMethod(deletedPaymentDetails)

        assertThat(viewModel.selectedItem.value)
            .isEqualTo(PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS.paymentDetails.first())
    }

    @Test
    fun `when default item is not supported then first supported item is selected`() = runTest {
        whenever(args.stripeIntent).thenReturn(
            StripeIntentFixtures.PI_SUCCEEDED.copy(
                linkFundingSources = listOf(ConsumerPaymentDetails.BankAccount.type)
            )
        )
        whenever(linkAccountManager.listPaymentDetails())
            .thenReturn(Result.success(PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS))

        val viewModel = createViewModel()

        val bankAccount = PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS.paymentDetails[2]
        assertThat(viewModel.selectedItem.value).isEqualTo(bankAccount)
    }

    @Test
    fun `when payment confirmation fails then an error message is shown`() {
        val errorThrown = "Error message"
        val viewModel = createViewModel()

        viewModel.onItemSelected(PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS.paymentDetails.first())
        viewModel.onConfirmPayment()

        val callbackCaptor = argumentCaptor<PaymentConfirmationCallback>()
        verify(confirmationManager).confirmStripeIntent(any(), callbackCaptor.capture())

        callbackCaptor.firstValue(Result.success(PaymentResult.Failed(RuntimeException(errorThrown))))

        assertThat(viewModel.errorMessage.value).isEqualTo(ErrorMessage.Raw(errorThrown))
    }

    @Test
    fun `deletePaymentMethod fetches payment details when successful`() = runTest {
        val paymentDetails = PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS
        whenever(linkAccountManager.listPaymentDetails())
            .thenReturn(Result.success(paymentDetails))

        val viewModel = createViewModel()
        verify(linkAccountManager).listPaymentDetails()
        clearInvocations(linkAccountManager)

        // Initially has two elements
        assertThat(viewModel.paymentDetailsList.value)
            .containsExactlyElementsIn(paymentDetails.paymentDetails)

        whenever(linkAccountManager.deletePaymentDetails(anyOrNull()))
            .thenReturn(Result.success(Unit))

        // Delete the first element
        viewModel.deletePaymentMethod(paymentDetails.paymentDetails.first())

        // Fetches payment details again
        verify(linkAccountManager).listPaymentDetails()
    }

    @Test
    fun `when payment method deletion fails then an error message is shown`() = runTest {
        whenever(linkAccountManager.listPaymentDetails())
            .thenReturn(Result.success(PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS))

        val errorThrown = "Error message"
        val viewModel = createViewModel()

        whenever(linkAccountManager.deletePaymentDetails(anyOrNull()))
            .thenReturn(Result.failure(RuntimeException(errorThrown)))

        viewModel.deletePaymentMethod(PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS.paymentDetails.first())

        assertThat(viewModel.errorMessage.value).isEqualTo(ErrorMessage.Raw(errorThrown))
    }

    @Test
    fun `onSelectedPaymentDetails dismisses on success`() = runTest {
        whenever(confirmationManager.confirmStripeIntent(any(), any())).thenAnswer { invocation ->
            (invocation.getArgument(1) as? PaymentConfirmationCallback)?.let {
                it(Result.success(PaymentResult.Completed))
            }
        }
        whenever(linkAccountManager.listPaymentDetails())
            .thenReturn(Result.success(PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS))

        val paymentDetails = PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS.paymentDetails.first()
        val viewModel = createViewModel()
        viewModel.onConfirmPayment()

        assertThat(viewModel.primaryButtonState.value).isEqualTo(PrimaryButtonState.Completed)

        advanceTimeBy(PrimaryButtonState.COMPLETED_DELAY_MS + 1)

        verify(navigator).dismiss(LinkActivityResult.Completed)
    }

    @Test
    fun `Pay another way dismisses Link`() {
        val viewModel = createViewModel()

        viewModel.payAnotherWay()

        verify(navigator).dismiss()
    }

    @Test
    fun `Add new payment method navigates to AddPaymentMethod screen`() {
        val viewModel = createViewModel()

        viewModel.addNewPaymentMethod()

        verify(navigator).navigateTo(argWhere { it is LinkScreen.PaymentMethod }, eq(false))
    }

    @Test
    fun `Update payment method navigates to CardEdit screen`() = runTest {
        val paymentDetails = PaymentDetailsFixtures.CONSUMER_PAYMENT_DETAILS
        whenever(linkAccountManager.listPaymentDetails())
            .thenReturn(Result.success(paymentDetails))

        val viewModel = createViewModel()

        viewModel.editPaymentMethod(paymentDetails.paymentDetails.first())

        verify(navigator).navigateTo(
            argWhere {
                it.route.startsWith(LinkScreen.CardEdit.route.substringBefore('?'))
            },
            any()
        )
    }

    @Test
    fun `On CardEdit result successful then it reloads payment details`() = runTest {
        val flow = MutableStateFlow<CardEditViewModel.Result?>(null)
        whenever(navigator.getResultFlow<CardEditViewModel.Result>(any())).thenReturn(flow)

        createViewModel()
        verify(linkAccountManager).listPaymentDetails()
        clearInvocations(linkAccountManager)

        flow.emit(CardEditViewModel.Result.Success)
        verify(linkAccountManager).listPaymentDetails()
    }

    @Test
    fun `On CardEdit result failure then it shows error`() = runTest {
        val flow = MutableStateFlow<CardEditViewModel.Result?>(null)
        whenever(navigator.getResultFlow<CardEditViewModel.Result>(any())).thenReturn(flow)

        val viewModel = createViewModel()

        val error = ErrorMessage.Raw("Error message")
        flow.emit(CardEditViewModel.Result.Failure(error))

        assertThat(viewModel.errorMessage.value).isEqualTo(error)
    }

    @Test
    fun `Factory gets initialized by Injector`() {
        val mockBuilder = mock<SignedInViewModelSubcomponent.Builder>()
        val mockSubComponent = mock<SignedInViewModelSubcomponent>()
        val vmToBeReturned = mock<WalletViewModel>()

        whenever(mockBuilder.linkAccount(any())).thenReturn(mockBuilder)
        whenever(mockBuilder.build()).thenReturn(mockSubComponent)
        whenever((mockSubComponent.walletViewModel)).thenReturn(vmToBeReturned)

        val mockSavedStateRegistryOwner = mock<SavedStateRegistryOwner>()
        val mockSavedStateRegistry = mock<SavedStateRegistry>()
        val mockLifeCycle = mock<Lifecycle>()

        whenever(mockSavedStateRegistryOwner.savedStateRegistry).thenReturn(mockSavedStateRegistry)
        whenever(mockSavedStateRegistryOwner.lifecycle).thenReturn(mockLifeCycle)
        whenever(mockLifeCycle.currentState).thenReturn(Lifecycle.State.CREATED)

        val injector = object : NonFallbackInjector {
            override fun inject(injectable: Injectable<*>) {
                val factory = injectable as WalletViewModel.Factory
                factory.subComponentBuilderProvider = Provider { mockBuilder }
            }
        }

        val factory = WalletViewModel.Factory(
            mock(),
            injector
        )
        val factorySpy = spy(factory)
        val createdViewModel = factorySpy.create(WalletViewModel::class.java)
        assertThat(createdViewModel).isEqualTo(vmToBeReturned)
    }

    private fun createViewModel() =
        WalletViewModel(
            args,
            linkAccountManager,
            navigator,
            confirmationManager,
            logger
        )

    companion object {
        const val CLIENT_SECRET = "client_secret"
    }
}
