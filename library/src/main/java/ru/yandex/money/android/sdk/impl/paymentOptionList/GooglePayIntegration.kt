/*
 * The MIT License (MIT)
 * Copyright © 2018 NBCO Yandex.Money LLC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the “Software”), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do so, subject to the
 * following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED “AS IS”, WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED,
 * INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR
 * PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT
 * OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

package ru.yandex.money.android.sdk.impl.paymentOptionList

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.support.v4.app.Fragment
import android.util.Log
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.CardRequirements
import com.google.android.gms.wallet.IsReadyToPayRequest
import com.google.android.gms.wallet.PaymentData
import com.google.android.gms.wallet.PaymentDataRequest
import com.google.android.gms.wallet.PaymentMethodTokenizationParameters
import com.google.android.gms.wallet.PaymentsClient
import com.google.android.gms.wallet.TransactionInfo
import com.google.android.gms.wallet.Wallet
import com.google.android.gms.wallet.WalletConstants
import ru.yandex.money.android.sdk.GooglePayInfo
import ru.yandex.money.android.sdk.impl.PendingIntentActivity
import ru.yandex.money.android.sdk.payment.GetLoadedPaymentOptionListGateway
import ru.yandex.money.android.sdk.payment.loadOptionList.CheckGooglePayAvailableGateway
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

private const val GOOGLE_PAY_REQUEST_CODE = 0xAB1D

internal class GooglePayIntegration(
    context: Context,
    private val shopId: String,
    useTestEnvironment: Boolean,
    private val loadedPaymentOptionsGateway: GetLoadedPaymentOptionListGateway
) : CheckGooglePayAvailableGateway {

    private var paymentOptionId: Int? = null
    private var recurringPaymentsPossible: Boolean? = null
    private var waitingForResult = false

    private val paymentsClient: PaymentsClient =
        Wallet.getPaymentsClient(
            context.applicationContext,
            Wallet.WalletOptions.Builder()
                .setEnvironment(
                    if (useTestEnvironment) {
                        WalletConstants.ENVIRONMENT_TEST
                    } else {
                        WalletConstants.ENVIRONMENT_PRODUCTION
                    }
                )
                .build()
        )

    override fun checkGooglePayAvailable(): Boolean {
        val request = IsReadyToPayRequest.newBuilder()
            .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_CARD)
            .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_TOKENIZED_CARD)
            .build()
        val result = ArrayBlockingQueue<Boolean?>(1)
        paymentsClient.isReadyToPay(request).addOnCompleteListener { task ->
            result.offer(task.result)
        }
        return result.poll(10, TimeUnit.SECONDS) ?: false
    }

    fun startGooglePayTokenization(
        fragment: Fragment,
        paymentOptionId: Int,
        recurringPaymentsPossible: Boolean
    ) {
        if (waitingForResult) {
            return
        }

        this.paymentOptionId = paymentOptionId
        this.recurringPaymentsPossible = recurringPaymentsPossible

        val paymentOption = loadedPaymentOptionsGateway.getLoadedPaymentOptions().first { it.id == paymentOptionId }

        val request = PaymentDataRequest.newBuilder()
            .setTransactionInfo(
                TransactionInfo.newBuilder()
                    .setTotalPriceStatus(WalletConstants.TOTAL_PRICE_STATUS_FINAL)
                    .setTotalPrice(paymentOption.charge.value.toPlainString())
                    .setCurrencyCode(paymentOption.charge.currency.currencyCode)
                    .build()
            )
            .addAllowedPaymentMethod(WalletConstants.PAYMENT_METHOD_TOKENIZED_CARD)
            .setCardRequirements(
                CardRequirements.newBuilder()
                    .addAllowedCardNetwork(WalletConstants.CARD_NETWORK_AMEX)
                    .addAllowedCardNetwork(WalletConstants.CARD_NETWORK_DISCOVER)
                    .addAllowedCardNetwork(WalletConstants.CARD_NETWORK_JCB)
                    .addAllowedCardNetwork(WalletConstants.CARD_NETWORK_VISA)
                    .addAllowedCardNetwork(WalletConstants.CARD_NETWORK_MASTERCARD)
                    .setAllowPrepaidCards(false)
                    .build()
            )
            .setPaymentMethodTokenizationParameters(
                PaymentMethodTokenizationParameters.newBuilder()
                    .setPaymentMethodTokenizationType(WalletConstants.PAYMENT_METHOD_TOKENIZATION_TYPE_PAYMENT_GATEWAY)
                    .addParameter("gateway", "yandexcheckout")
                    .addParameter("gatewayMerchantId", shopId)
                    .build()
            )
            .build()


        paymentsClient.loadPaymentData(request).addOnCompleteListener { task ->
            task.exception?.takeIf { it is ResolvableApiException }?.also {
                fragment.startActivityForResult(
                    PendingIntentActivity.createIntent(
                        checkNotNull(fragment.context),
                        (it as ResolvableApiException).resolution
                    ), GOOGLE_PAY_REQUEST_CODE
                )
            }
        }
        waitingForResult = true
    }

    fun handleGooglePayTokenization(requestCode: Int, resultCode: Int, data: Intent?): GooglePayTokenizationResult =
        if (requestCode == GOOGLE_PAY_REQUEST_CODE) {
            waitingForResult = false
            if (resultCode == Activity.RESULT_OK) {
                checkNotNull(PaymentData.getFromIntent(requireNotNull(data))).let {
                    GooglePayTokenizationSuccess(
                        paymentOptionId = checkNotNull(paymentOptionId),
                        recurringPaymentsPossible = checkNotNull(recurringPaymentsPossible),
                        paymentOptionInfo = GooglePayInfo(
                            paymentMethodToken = checkNotNull(it.paymentMethodToken).token,
                            googleTransactionId = it.googleTransactionId
                        )
                    )
                }
            } else {
                Log.d("GOOGLE_PAY_RESULT", data?.let { AutoResolveHelper.getStatusFromIntent(it) }?.statusMessage ?: "")
                GooglePayTokenizationCanceled()
            }
        } else {
            GooglePayNotHandled()
        }

    fun reset() {
        waitingForResult = false
        paymentOptionId = null
        recurringPaymentsPossible = null
    }
}

internal sealed class GooglePayTokenizationResult
internal data class GooglePayTokenizationSuccess(
    val paymentOptionId: Int,
    val recurringPaymentsPossible: Boolean,
    val paymentOptionInfo: GooglePayInfo
) : GooglePayTokenizationResult()

internal class GooglePayTokenizationCanceled : GooglePayTokenizationResult()
internal class GooglePayNotHandled : GooglePayTokenizationResult()
