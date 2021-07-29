package com.stripe.android.paymentsheet.address

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import com.google.common.truth.Truth.assertThat
import com.stripe.android.paymentsheet.R
import com.stripe.android.paymentsheet.address.AddressFieldElementRepository.Companion.supportedCountries
import com.stripe.android.paymentsheet.address.TransformAddressToSpec.AddressSchema
import com.stripe.android.paymentsheet.address.TransformAddressToSpec.FieldType.AddressLine1
import com.stripe.android.paymentsheet.address.TransformAddressToSpec.FieldType.AddressLine2
import com.stripe.android.paymentsheet.address.TransformAddressToSpec.FieldType.Locality
import com.stripe.android.paymentsheet.specifications.IdentifierSpec
import com.stripe.android.paymentsheet.specifications.SectionFieldSpec
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.security.InvalidParameterException

@ExperimentalCoroutinesApi
class TransformAddressToSpecTest {
    @get:Rule
    val rule = InstantTaskExecutorRule()

    private val testDispatcher = TestCoroutineDispatcher()

    private val transformAddressToSpec = TransformAddressToSpec(testDispatcher)

    @Test
    fun `Read US Json`() {
        runBlocking {
            val addressSchema = readFile("src/main/assets/addressinfo/US.json")!!
            val simpleTextList = addressSchema.transformToSpecFieldList()

            val addressLine1 = SectionFieldSpec.SimpleText(
                IdentifierSpec("line1"),
                R.string.address_label_address,
                KeyboardCapitalization.Words,
                KeyboardType.Text,
                showOptionalLabel = false
            )

            val addressLine2 = SectionFieldSpec.SimpleText(
                IdentifierSpec("line2"),
                R.string.address_label_address_line2,
                KeyboardCapitalization.Words,
                KeyboardType.Text,
                showOptionalLabel = true
            )

            val city = SectionFieldSpec.SimpleText(
                IdentifierSpec("city"),
                R.string.address_label_city,
                KeyboardCapitalization.Words,
                KeyboardType.Text,
                showOptionalLabel = false
            )

            val state = SectionFieldSpec.SimpleText(
                IdentifierSpec("state"),
                R.string.address_label_state,
                KeyboardCapitalization.Words,
                KeyboardType.Text,
                showOptionalLabel = false
            )

            val zip = SectionFieldSpec.SimpleText(
                IdentifierSpec("postal_code"),
                R.string.address_label_zip_code,
                KeyboardCapitalization.None,
                KeyboardType.Number,
                showOptionalLabel = false
            )

            assertThat(simpleTextList.size).isEqualTo(5)
            assertThat(simpleTextList[0]).isEqualTo(addressLine1)
            assertThat(simpleTextList[1]).isEqualTo(addressLine2)
            assertThat(simpleTextList[2]).isEqualTo(city)
            assertThat(simpleTextList[3]).isEqualTo(zip)
            assertThat(simpleTextList[4]).isEqualTo(state)
        }
    }

    @Test
    fun `Make sure name schema is not found on fields not processed`() {
        runBlocking {
            supportedCountries.forEach { countryCode ->
                val schemaList = readFile("src/main/assets/addressinfo/$countryCode.json")
                val invalidNameType = schemaList?.filter { addressSchema ->
                    addressSchema.schema?.nameType != null
                }
                    ?.filter {
                        it.type == AddressLine1 &&
                            it.type == AddressLine2 &&
                            it.type == Locality
                    }
                invalidNameType?.forEach { println(it.type?.name) }
                assertThat(invalidNameType).isEmpty()
            }
        }
    }

    @Test
    fun `Make sure all country code json files are serializable`() {
        runBlocking {
            supportedCountries.forEach { countryCode ->
                val schemaList = readFile("src/main/assets/addressinfo/$countryCode.json")
                schemaList?.filter { addressSchema ->
                    addressSchema.schema?.nameType != null
                }
                    ?.filter {
                        it.type == AddressLine1 &&
                            it.type == AddressLine2 &&
                            it.type == Locality
                    }
                    ?.forEach { println(it.type?.name) }
            }
        }
    }

    private suspend fun readFile(filename: String): List<AddressSchema>? {
        val file = File(filename)

        if (file.exists()) {
            return transformAddressToSpec.parseAddressesSchema(file.inputStream())
        } else {
            throw InvalidParameterException("Error could not find the test files.")
        }
    }
}
