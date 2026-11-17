package quest.saigon.android.ui.auth

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import quest.saigon.Copy
import quest.saigon.Limits
import quest.saigon.android.theme.LocalSaigonTokens
import quest.saigon.android.ui.components.SaigonDialog
import quest.saigon.android.ui.components.SaigonPrimaryButton
import quest.saigon.android.ui.components.SaigonSecondaryButton
import quest.saigon.android.ui.components.SaigonTextButton
import quest.saigon.android.ui.components.SaigonTextField
import quest.saigon.shared.store.TownSessionStore

@Composable
fun AuthDialog(
    sessionStore: TownSessionStore,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state by sessionStore.state.collectAsState()
    val lang = state.language
    val tokens = LocalSaigonTokens.current
    val scope = rememberCoroutineScope()

    var creating by remember { mutableStateOf(true) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    val errorMessage = localError ?: state.error

    SaigonDialog(onDismiss = onDismiss) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                Copy.text(lang, if (creating) "createAccount" else "signIn"),
                color = tokens.text,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(end = 36.dp),
            )
            Text(
                Copy.text(lang, "needSignInToPost"),
                style = MaterialTheme.typography.bodyMedium,
                color = tokens.muted,
            )

            if (creating) {
                SaigonTextField(
                    value = name,
                    onValueChange = { name = it; localError = null; sessionStore.clearError() },
                    label = Copy.text(lang, "name"),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SaigonTextField(
                value = phone,
                onValueChange = { phone = it; localError = null; sessionStore.clearError() },
                label = Copy.text(lang, "phone"),
                supportingText = Copy.text(lang, "phoneHint"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            SaigonTextField(
                value = password,
                onValueChange = { password = it; localError = null; sessionStore.clearError() },
                label = Copy.text(lang, "password"),
                supportingText = Copy.text(lang, "passwordHint"),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            if (creating) {
                SaigonTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; localError = null },
                    label = Copy.text(lang, "confirmPassword"),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    errorMessage,
                    color = tokens.danger,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
            ) {
                SaigonPrimaryButton(
                    text = Copy.text(lang, if (creating) "createAccount" else "signIn"),
                    loading = state.isSubmitting,
                    onClick = {
                        localError = null
                        if (creating && name.trim().length !in 1..Limits.NAME_MAX) {
                            localError = Copy.text(lang, "invalidName")
                            return@SaigonPrimaryButton
                        }
                        if (password.length !in Limits.PASSWORD_MIN..Limits.PASSWORD_MAX) {
                            localError = Copy.text(lang, "invalidPassword")
                            return@SaigonPrimaryButton
                        }
                        if (creating && password != confirmPassword) {
                            localError = Copy.text(lang, "passwordMismatch")
                            return@SaigonPrimaryButton
                        }
                        scope.launch {
                            val ok = if (creating) {
                                sessionStore.register(name.trim(), phone, password)
                            } else {
                                sessionStore.login(phone, password)
                            }
                            if (ok) onSuccess()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                SaigonSecondaryButton(
                    text = Copy.text(lang, if (creating) "switchToSignIn" else "switchToCreate"),
                    onClick = {
                        creating = !creating
                        localError = null
                        sessionStore.clearError()
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                SaigonTextButton(
                    text = Copy.text(lang, "close"),
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
