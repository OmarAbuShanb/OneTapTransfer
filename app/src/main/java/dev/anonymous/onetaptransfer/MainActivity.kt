package dev.anonymous.onetaptransfer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.telecom.TelecomManager
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import dev.anonymous.onetaptransfer.databinding.ActivityMainBinding
import dev.anonymous.onetaptransfer.ui.HistoryBottomSheet
import dev.anonymous.onetaptransfer.ui.PinContactDialogFragment
import dev.anonymous.onetaptransfer.ui.PinnedContactAdapter
import dev.anonymous.onetaptransfer.ui.PrivacyPolicyDialog
import dev.anonymous.onetaptransfer.ui.TransactionAdapter
import dev.anonymous.onetaptransfer.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var historyAdapter: TransactionAdapter
    private lateinit var pinnedAdapter: PinnedContactAdapter

    private var currentType = "WALLET_1"
    private var didApplyInitialFocus = false

    /** Cached reference to InputMethodManager to avoid repeated getSystemService calls. */
    private val imm: InputMethodManager by lazy {
        getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    }

    companion object {
        private const val PIN_CONTACT_DIALOG_TAG = "pin_contact_dialog"

        /** Maps a tab position index to the corresponding transaction type string. */
        fun tabIndexToType(index: Int): String = when (index) {
            0 -> "BANK"
            1 -> "WALLET_1"
            2 -> "MERCHANT"
            3 -> "WALLET_2"
            else -> "WALLET_1"
        }

        /** Maps a transaction type string back to its tab position index. */
        fun typeToTabIndex(type: String): Int = when (type) {
            "BANK" -> 0
            "WALLET_1" -> 1
            "MERCHANT" -> 2
            "WALLET_2" -> 3
            else -> 1
        }
    }

    // region Activity Result Launchers

    private val contactPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { handleContactResult(it) }
        }
    }

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            makeDirectCall()
        } else {
            Toast.makeText(this, R.string.permission_call_denied, Toast.LENGTH_SHORT).show()
        }
    }

    // endregion

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        configureSystemBars()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        setupUI()
        setupFragmentResultListeners()
        observeViewModel()
    }

    // region System Bars & Window Insets

    private fun configureSystemBars() {
        val systemBarColor = ContextCompat.getColor(this, R.color.surface)
        val isDarkMode = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        enableEdgeToEdge(
            statusBarStyle = if (isDarkMode) {
                SystemBarStyle.dark(systemBarColor)
            } else {
                SystemBarStyle.light(systemBarColor, systemBarColor)
            },
            navigationBarStyle = if (isDarkMode) {
                SystemBarStyle.dark(systemBarColor)
            } else {
                SystemBarStyle.light(systemBarColor, systemBarColor)
            }
        )
        window.statusBarColor = systemBarColor
        window.navigationBarColor = systemBarColor
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    private fun applyWindowInsets() {
        val footerExtraBottomPadding = resources.getDimensionPixelSize(R.dimen.footer_extra_bottom_padding)
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.main.updatePadding(left = systemBars.left, right = systemBars.right)
            binding.appBarLayout.updatePadding(top = systemBars.top)
            binding.footerContainer.updatePadding(bottom = systemBars.bottom + footerExtraBottomPadding)
            insets
        }
    }

    // endregion

    // region UI Setup

    private fun setupUI() {
        setupRecyclerViews()
        setupTabs()
        setupInputWatchers()
        setupContactPicker()
        setupActionButtons()
        setupHistoryButton()
        setupPrivacyPolicy()
    }

    private fun setupRecyclerViews() {
        historyAdapter = TransactionAdapter(
            onDelete = { viewModel.deleteTransaction(it.id) },
            onPin = { handlePinTransaction(it.recipient, it.type) },
            onClick = { fillInputs(it.recipient, it.amount, it.type) }
        )
        binding.rvHistory.layoutManager = LinearLayoutManager(this)
        binding.rvHistory.adapter = historyAdapter

        pinnedAdapter = PinnedContactAdapter(
            onDelete = { viewModel.togglePinContact(it.name, it.number, it.type) },
            onClick = {
                fillInputs(it.number, "")
                focusAmount()
            }
        )
        binding.rvPinned.layoutManager = LinearLayoutManager(this)
        binding.rvPinned.adapter = pinnedAdapter
    }

    private fun setupTabs() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                tab?.let { handleTabSelection(it.position) }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })

        // Fix tab text truncation: TabLayout internally forces ellipsize
        // on TextViews, so we override it programmatically after layout.
        binding.tabLayout.post {
            val slidingTabStrip = binding.tabLayout.getChildAt(0) as? android.view.ViewGroup ?: return@post
            for (i in 0 until slidingTabStrip.childCount) {
                val tabView = slidingTabStrip.getChildAt(i) as? android.view.ViewGroup ?: continue
                for (j in 0 until tabView.childCount) {
                    val child = tabView.getChildAt(j)
                    if (child is android.widget.TextView) {
                        child.maxLines = 2
                        child.ellipsize = null
                        child.textAlignment = android.view.View.TEXT_ALIGNMENT_CENTER
                    }
                }
            }
        }
    }

    private fun setupInputWatchers() {
        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.recipientLayout.error = null
                binding.amountLayout.error = null
                updateUssdPreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        }
        binding.etRecipient.addTextChangedListener(watcher)
        binding.etAmount.addTextChangedListener(watcher)
        binding.etAmount.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                hideKeyboardAndClearInputFocus()
                true
            } else {
                false
            }
        }
    }

    private fun setupContactPicker() {
        binding.recipientLayout.setEndIconOnClickListener {
            val pickPhoneIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            contactPickerLauncher.launch(pickPhoneIntent)
        }
    }

    private fun setupActionButtons() {
        binding.btnCall.setOnClickListener { handleAction("CALL") }
        binding.btnTransfer.setOnClickListener { handleAction("TRANSFER") }
        binding.btnCopy.setOnClickListener { handleAction("COPY") }
    }

    private fun setupHistoryButton() {
        binding.tvViewHistory.setOnClickListener {
            val bottomSheet = HistoryBottomSheet()
            bottomSheet.onTransactionClick = { recipient, amount, type ->
                fillInputs(recipient, amount, type)
            }
            bottomSheet.onPinTransaction = { recipient, type ->
                handlePinTransaction(recipient, type)
            }
            bottomSheet.show(supportFragmentManager, "history")
        }
    }

    private fun setupPrivacyPolicy() {
        binding.toolbar.inflateMenu(R.menu.main_menu)
        binding.toolbar.overflowIcon?.setTint(ContextCompat.getColor(this, R.color.text_main))
        binding.tvPrivacyBottom.setOnClickListener {
            PrivacyPolicyDialog().show(supportFragmentManager, "privacy")
        }
        binding.toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_privacy) {
                PrivacyPolicyDialog().show(supportFragmentManager, "privacy")
                true
            } else false
        }
    }

    // endregion

    // region ViewModel Observation

    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pinnedContacts.collect {
                        pinnedAdapter.submitList(it)
                        historyAdapter.updatePinnedContacts(it)
                        val hasPinned = it.isNotEmpty()
                        binding.rvPinned.visibility = if (hasPinned) View.VISIBLE else View.GONE
                        binding.tvNoPinnedContacts.visibility = if (hasPinned) View.GONE else View.VISIBLE
                    }
                }
                launch {
                    viewModel.lastTab.collect {
                        binding.tabLayout.getTabAt(it)?.select()
                        handleTabSelection(it)
                        if (!didApplyInitialFocus) {
                            didApplyInitialFocus = true
                            if (currentType == "BANK") {
                                hideKeyboardAndClearInputFocus()
                            } else {
                                // Use a longer delay on initial launch because the
                                // window may not have gained focus yet, causing
                                // showSoftInput to silently fail.
                                binding.etRecipient.postDelayed({
                                    focusRecipient()
                                }, 350)
                            }
                        }
                    }
                }
                launch {
                    viewModel.history.collect {
                        historyAdapter.submitList(it.take(10))
                        val hasHistory = it.isNotEmpty()
                        binding.rvHistory.visibility = if (hasHistory) View.VISIBLE else View.GONE
                        binding.tvNoRecentTransactions.visibility = if (hasHistory) View.GONE else View.VISIBLE
                    }
                }
            }
        }
    }

    // endregion

    // region Tab & USSD Logic

    private fun handleTabSelection(position: Int) {
        viewModel.saveLastTab(position)
        currentType = tabIndexToType(position)

        if (currentType == "BANK") {
            binding.recipientLayout.visibility = View.GONE
            binding.amountLayout.visibility = View.GONE
            hideKeyboardAndClearInputFocus()
            val bankCode = viewModel.generateUssdCode(currentType, "", "")
            binding.tvUssdPreview.text = formatUssdForDisplay(bankCode)
        } else {
            binding.recipientLayout.visibility = View.VISIBLE
            binding.amountLayout.visibility = View.VISIBLE
            updateUssdPreview()
        }
    }

    private fun updateUssdPreview() {
        val recipient = normalizePalestinianMobile(binding.etRecipient.text.toString())
        val amount = binding.etAmount.text.toString().trim()

        if (currentType == "BANK") {
            val bankCode = viewModel.generateUssdCode(currentType, "", "")
            binding.tvUssdPreview.text = formatUssdForDisplay(bankCode)
            return
        }

        if (!isValidPalestinianMobile(recipient) || amount.isEmpty()) {
            binding.tvUssdPreview.text = getString(R.string.ussd_preview_hint)
            return
        }

        val code = viewModel.generateUssdCode(currentType, recipient, amount)
        binding.tvUssdPreview.text = if (code.isEmpty()) {
            getString(R.string.ussd_preview_hint)
        } else {
            formatUssdForDisplay(code)
        }
    }

    // endregion

    // region Action Handling

    /**
     * Validates that the current inputs (recipient + amount) are valid for the active tab.
     * Bank tab always passes validation since it doesn't require inputs.
     * @return true if inputs are valid, false otherwise (shows a Snackbar on failure).
     */
    private fun validateInputs(recipient: String, amount: String): Boolean {
        binding.recipientLayout.error = null
        binding.amountLayout.error = null

        if (currentType == "BANK") return true

        var isValid = true

        if (!isValidPalestinianMobile(recipient)) {
            binding.recipientLayout.error = getString(R.string.error_invalid_phone)
            isValid = false
        }

        val amountVal = amount.toIntOrNull()
        if (amountVal == null || amountVal !in 1..999) {
            binding.amountLayout.error = getString(R.string.error_invalid_amount)
            isValid = false
        }

        return isValid
    }

    private fun handleAction(action: String) {
        val recipient = normalizePalestinianMobile(binding.etRecipient.text.toString())
        val amount = binding.etAmount.text.toString().trim()

        if (!validateInputs(recipient, amount)) return

        if (action == "CALL" || action == "TRANSFER") {
            hideKeyboardAndClearInputFocus()
        }

        val code = viewModel.generateUssdCode(currentType, recipient, amount)

        when (action) {
            "CALL" -> checkCallPermissionAndMakeCall()
            "TRANSFER" -> {
                val intent = Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(code)}".toUri())
                startActivity(intent)
                if (currentType != "BANK") {
                    viewModel.saveTransaction(recipient, amount, currentType)
                }
            }
            "COPY" -> {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("USSD", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(this, R.string.msg_copied, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkCallPermissionAndMakeCall() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            makeDirectCall()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    private fun makeDirectCall() {
        val recipient = normalizePalestinianMobile(binding.etRecipient.text.toString())
        val amount = binding.etAmount.text.toString().trim()

        if (!validateInputs(recipient, amount)) return

        val code = viewModel.generateUssdCode(currentType, recipient, amount)
        val encodedCode = Uri.encode(code)
        val phoneUri = "tel:$encodedCode".toUri()
        val preferredDialerPackages = resolvePreferredDialerPackages(phoneUri)

        for (dialerPackage in preferredDialerPackages) {
            val callIntent = Intent(Intent.ACTION_CALL, phoneUri).setPackage(dialerPackage)
            if (startActivitySafely(callIntent)) {
                if (currentType != "BANK") {
                    viewModel.saveTransaction(recipient, amount, currentType)
                }
                return
            }
        }

        Toast.makeText(this, R.string.error_no_dialer, Toast.LENGTH_SHORT).show()
    }

    // endregion

    // region Pinned Contacts

    private fun handlePinTransaction(recipient: String, type: String) {
        val normalizedRecipient = normalizePalestinianMobile(recipient)

        val existingPin = viewModel.pinnedContacts.value.firstOrNull { it.number == normalizedRecipient }
        if (existingPin != null) {
            viewModel.togglePinContact(existingPin.name, existingPin.number, existingPin.type)
            return
        }

        if (supportFragmentManager.findFragmentByTag(PIN_CONTACT_DIALOG_TAG) != null) return
        PinContactDialogFragment.newInstance(normalizedRecipient, type)
            .show(supportFragmentManager, PIN_CONTACT_DIALOG_TAG)
    }

    private fun setupFragmentResultListeners() {
        supportFragmentManager.setFragmentResultListener(
            PinContactDialogFragment.RESULT_KEY,
            this
        ) { _, result ->
            val name = result.getString(PinContactDialogFragment.RESULT_NAME).orEmpty().trim()
            val number = result.getString(PinContactDialogFragment.RESULT_NUMBER).orEmpty()
            val type = result.getString(PinContactDialogFragment.RESULT_TYPE).orEmpty()

            if (name.length < 3 || !isValidPalestinianMobile(number) || type.isBlank()) {
                return@setFragmentResultListener
            }

            hideKeyboardAndClearInputFocus()
            viewModel.togglePinContact(name, number, type)
        }
    }

    // endregion

    // region Contact Handling

    private fun handleContactResult(uri: Uri) {
        val projection = arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER)
        contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                if (numberIndex == -1) return

                val rawNumber = cursor.getString(numberIndex).orEmpty()
                val filteredNumber = normalizePalestinianMobile(rawNumber)
                binding.etRecipient.setText(filteredNumber.ifEmpty { rawNumber })
                focusAmountDelayed()
            }
        }
    }

    // endregion

    // region Number Utilities

    private fun normalizePalestinianMobile(input: String): String {
        val digitsOnly = input.mapNotNull { char ->
            when (char) {
                in '0'..'9' -> char
                in '\u0660'..'\u0669' -> ('0'.code + (char.code - '\u0660'.code)).toChar()
                in '\u06F0'..'\u06F9' -> ('0'.code + (char.code - '\u06F0'.code)).toChar()
                else -> null
            }
        }.joinToString("")

        if (digitsOnly.isEmpty()) return ""

        var normalized = digitsOnly

        while (normalized.startsWith("00")) {
            normalized = normalized.drop(2)
        }

        when {
            normalized.startsWith("972") -> normalized = normalized.drop(3)
            normalized.startsWith("970") -> normalized = normalized.drop(3)
        }

        normalized = normalized.trimStart('0')

        if (normalized.startsWith("5")) {
            normalized = "0$normalized"
        }

        if (!normalized.startsWith("05")) return ""
        if (normalized.length < 10) return ""

        return normalized.take(10)
    }

    private fun isValidPalestinianMobile(number: String): Boolean {
        return number.length == 10 && number.startsWith("05")
    }

    // endregion

    // region USSD Formatting

    private fun formatUssdForDisplay(code: String): String {
        if (code.isEmpty()) return code
        val leftToRightMark = '\u200E'
        return "$leftToRightMark$code$leftToRightMark"
    }

    // endregion

    // region Input & Focus Management

    /**
     * Fills the input fields with the given values, optionally switches to the correct tab,
     * and scrolls to the top of the page.
     *
     * @param type If non-null, switches to the tab corresponding to this transaction type.
     */
    private fun fillInputs(recipient: String, amount: String, type: String? = null) {
        // Switch tab if type is specified
        if (type != null) {
            val tabIndex = typeToTabIndex(type)
            binding.tabLayout.getTabAt(tabIndex)?.select()
        }

        val normalizedRecipient = normalizePalestinianMobile(recipient)

        if (currentType != "BANK") {
            binding.etRecipient.setText(normalizedRecipient.ifEmpty { recipient })
            binding.etAmount.setText(amount)
        }

        updateUssdPreview()

        // Scroll to top so user can see the input fields and preview
        binding.scrollView.smoothScrollTo(0, 0)

        // If amount is empty, focus on amount field for quick entry
        if (currentType != "BANK" && amount.isEmpty()) {
            focusAmountDelayed()
        }
    }

    private fun hideKeyboardAndClearInputFocus() {
        binding.etRecipient.clearFocus()
        binding.etAmount.clearFocus()
        binding.main.isFocusableInTouchMode = true
        binding.main.requestFocus()

        val focusedView = currentFocus ?: binding.main
        imm.hideSoftInputFromWindow(focusedView.windowToken, 0)
    }

    private fun focusRecipient() {
        if (binding.recipientLayout.visibility != View.VISIBLE) return
        binding.etRecipient.requestFocus()
        imm.showSoftInput(binding.etRecipient, InputMethodManager.SHOW_IMPLICIT)
    }

    /** Focuses the amount field and opens the keyboard. Uses a small delay to ensure layout is ready. */
    private fun focusAmount() {
        if (binding.amountLayout.visibility != View.VISIBLE) return
        binding.etAmount.requestFocus()
        imm.showSoftInput(binding.etAmount, InputMethodManager.SHOW_IMPLICIT)
    }

    /** Focuses amount field with a delay — useful after layout changes (e.g., tab switch, contact pick). */
    private fun focusAmountDelayed() {
        binding.etAmount.postDelayed({
            binding.etAmount.requestFocus()
            imm.showSoftInput(binding.etAmount, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    // endregion

    // endregion

    // region Dialer Resolution

    private fun startActivitySafely(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun resolvePreferredDialerPackages(phoneUri: Uri): List<String> {
        val packages = mutableListOf<String>()

        val systemDialer = getSystemDialerPackageName()
        if (!systemDialer.isNullOrBlank()) {
            packages += systemDialer
        }

        val defaultDialer = getDefaultDialerPackageName()
        if (!defaultDialer.isNullOrBlank() && isSystemPackage(defaultDialer)) {
            packages += defaultDialer
        }

        val callIntent = Intent(Intent.ACTION_CALL, phoneUri)
        val systemCallHandler = packageManager
            .queryIntentActivities(callIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .mapNotNull { it.activityInfo?.packageName }
            .firstOrNull { isSystemPackage(it) }

        if (!systemCallHandler.isNullOrBlank()) {
            packages += systemCallHandler
        }

        return packages.distinct()
    }

    private fun getDefaultDialerPackageName(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return null
        val telecomManager = getSystemService(TelecomManager::class.java) ?: return null
        return telecomManager.defaultDialerPackage
    }

    private fun getSystemDialerPackageName(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val telecomManager = getSystemService(TelecomManager::class.java) ?: return null
        return telecomManager.systemDialerPackage
    }

    private fun isSystemPackage(packageName: String): Boolean {
        return try {
            val appInfo = packageManager.getApplicationInfo(packageName, 0)
            val isSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
            val isUpdatedSystem = (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            isSystem || isUpdatedSystem
        } catch (_: Exception) {
            false
        }
    }

    // endregion
}
