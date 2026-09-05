package com.example.mobile_image_retrieval.ui.screens

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.intl.LocaleList

/** Keep the IME's composing region while Vietnamese Telex/VNI keyboards build accented words. */
@Composable
internal fun VietnameseTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable (() -> Unit)? = null,
    placeholder: @Composable (() -> Unit)? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    supportingText: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    shape: Shape = MaterialTheme.shapes.small,
    onSearch: (() -> Unit)? = null,
) {
    var editing by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(value)) }
    // External changes (history/suggestions) replace text; normal keystrokes retain composition.
    val displayed = if (editing.text == value) editing else TextFieldValue(value, TextRange(value.length))
    SideEffect { if (editing != displayed) editing = displayed }
    OutlinedTextField(
        value = displayed,
        onValueChange = { editing = it; onValueChange(it.text) },
        modifier = modifier, label = label, placeholder = placeholder,
        leadingIcon = leadingIcon, trailingIcon = trailingIcon, supportingText = supportingText,
        singleLine = singleLine, enabled = enabled, isError = isError, shape = shape,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text,
            hintLocales = LocaleList("vi,en"), imeAction = if (onSearch == null) ImeAction.Done else ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch?.invoke() }),
    )
}
