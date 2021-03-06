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

package ru.yandex.money.android.sdk.impl.payment

import android.content.SharedPreferences
import ru.yandex.money.android.sdk.AnonymousUser
import ru.yandex.money.android.sdk.AuthorizedUser
import ru.yandex.money.android.sdk.CurrentUser
import ru.yandex.money.android.sdk.impl.extensions.edit
import ru.yandex.money.android.sdk.payment.CurrentUserGateway

private const val KEY_CURRENT_USER_NAME = "current_user_name"

internal class SharedPreferencesCurrentUserGateway(
        private val sharedPreferences: SharedPreferences
) : CurrentUserGateway {

    override var currentUser: CurrentUser
        get() {
            return if (sharedPreferences.contains(KEY_CURRENT_USER_NAME)) {
                AuthorizedUser(sharedPreferences.getString(KEY_CURRENT_USER_NAME, null))
            } else {
                AnonymousUser
            }
        }
        set(value) {
            sharedPreferences.edit {
                when (value) {
                    AnonymousUser -> remove(KEY_CURRENT_USER_NAME)
                    is AuthorizedUser -> putString(KEY_CURRENT_USER_NAME, value.userName)
                }
            }
        }
}
