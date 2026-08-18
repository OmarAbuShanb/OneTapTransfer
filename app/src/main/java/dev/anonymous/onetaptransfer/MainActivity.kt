package dev.anonymous.onetaptransfer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.provider.ContactsContract
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.text.Editable
import android.text.TextWatcher
import android.transition.ChangeBounds
import android.transition.Fade
import android.transition.TransitionManager
import android.transition.TransitionSet
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout
import dev.anonymous.onetaptransfer.databinding.ActivityMainBinding
import dev.anonymous.onetaptransfer.ui.HistoryBottomSheet
import dev.anonymous.onetaptransfer.ui.PinContactDialogFragment
import dev.anonymous.onetaptransfer.ui.PinnedContactAdapter
import dev.anonymous.onetaptransfer.ui.PrivacyPolicyDialog
import dev.anonymous.onetaptransfer.ui.TransactionAdapter
import dev.anonymous.onetaptransfer.ui.WhatsNewDialog
import dev.anonymous.onetaptransfer.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()
    private lateinit var historyAdapter: TransactionAdapter
    private lateinit var pinnedAdapter: PinnedContactAdapter

    private var currentType = "WALLET_1"
    private var selectedSimSlot = 1
    private var activeSimOptions: List<SimOption> = emptyList()
    private var didApplyInitialFocus = false
    private var skipNextSimSelectorRefresh = false
    private var isUpdatingSimSelection = false
    private var isContactPickerLaunching = false
    private var lastContactPickerClickTime = 0L

    /** Cached reference to InputMethodManager to avoid repeated getSystemService calls. */
    private val imm: InputMethodManager by lazy {
        getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
    }

    private data class SimOption(
        val slot: Int,
        val subscriptionId: Int,
        val displayName: String
    )

    companion object {
        private const val PIN_CONTACT_DIALOG_TAG = "pin_contact_dialog"
        private const val DEFAULT_SIM_SLOT = 1
        private const val SECOND_SIM_SLOT = 2
        private const val EXTRA_SUBSCRIPTION_ID = "android.telephony.extra.SUBSCRIPTION_ID"
        private const val EXTRA_SUBSCRIPTION_INDEX = "android.telephony.extra.SUBSCRIPTION_INDEX"

        /** Maps a tab position index to the corresponding transaction type string. */
        fun tabIndexToType(index: Int): String = when (index) {
            0 -> "WALLET_1"
            1 -> "MERCHANT_1"
            2 -> "WALLET_2"
            3 -> "MERCHANT_2"
            else -> "WALLET_1"
        }

        /** Maps a transaction type string back to its tab position index. */
        fun typeToTabIndex(type: String): Int = when (type) {
            "WALLET_1" -> 0
            "MERCHANT_1" -> 1
            "WALLET_2" -> 2
            "MERCHANT_2" -> 3
            else -> 0
        }
    }

    // region Activity Result Launchers

    private val contactPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        isContactPickerLaunching = false
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { handleContactResult(it) }
        }
    }

    private val requestDirectCallPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grantResults ->
            val hasAllPermissions = requiredDirectCallPermissions().all { permission ->
                grantResults[permission] == true || isPermissionGranted(permission)
            }
            if (hasAllPermissions) {
                makeDirectCall()
            } else {
                showDirectCallPermissionDialog()
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
        checkAppUpdateNotice()
    }

    private fun checkAppUpdateNotice() {
        val currentVersionCode = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionCode
            }
        } catch (_: Exception) {
            1
        }
        if (viewModel.checkAppUpdate(currentVersionCode)) {
            binding.root.post {
                WhatsNewDialog.newInstance().show(supportFragmentManager, "whats_new")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isContactPickerLaunching = false
        if (skipNextSimSelectorRefresh) {
            skipNextSimSelectorRefresh = false
            return
        }
        refreshSimSelector()
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
        setupSimSelector()
        setupInputWatchers()
        setupQuickTransfer()
        setupContactPicker()
        setupActionButtons()
        setupHistoryButton()
        setupPrivacyPolicy()
    }

    private fun animateInputCardChange() {
        val transition = TransitionSet().apply {
            ordering = TransitionSet.ORDERING_TOGETHER
            addTransition(ChangeBounds().apply {
                duration = 250
                interpolator = FastOutSlowInInterpolator()
            })
            addTransition(Fade(Fade.OUT).apply {
                duration = 150
            })
            addTransition(Fade(Fade.IN).apply {
                duration = 250
            })
        }
        TransitionManager.beginDelayedTransition(binding.inputContainer, transition)
    }

    private fun setupRecyclerViews() {
        historyAdapter = TransactionAdapter(
            onDelete = { viewModel.deleteTransaction(it.id) },
            onPin = { handlePinTransaction(it.recipient, it.type) },
            onClick = { fillInputs(it.recipient, it.amount, it.type, it.simSlot) }
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

    private fun setupSimSelector() {
        binding.simToggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val simSlot = when (checkedId) {
                R.id.btnSim2 -> SECOND_SIM_SLOT
                else -> DEFAULT_SIM_SLOT
            }
            selectedSimSlot = simSlot
            if (!isUpdatingSimSelection) {
                viewModel.saveLastSimSlot(simSlot)
            }
        }
        refreshSimSelector()
    }

    private fun refreshSimSelector() {
        if (!hasDirectCallPermissions()) {
            activeSimOptions = emptyList()
            selectedSimSlot = DEFAULT_SIM_SLOT
            binding.simToggleGroup.visibility = View.GONE
            return
        }

        activeSimOptions = loadActiveSimOptions()
        if (activeSimOptions.size < 2) {
            selectedSimSlot = DEFAULT_SIM_SLOT
            binding.simToggleGroup.visibility = View.GONE
            return
        }

        binding.btnSim1.text = activeSimOptions.getOrNull(0)?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.sim_1_default)
        binding.btnSim2.text = activeSimOptions.getOrNull(1)?.displayName
            ?.takeIf { it.isNotBlank() }
            ?: getString(R.string.sim_2_default)

        val persistedSlot = if (viewModel.lastSimSlot.value == SECOND_SIM_SLOT) {
            SECOND_SIM_SLOT
        } else {
            DEFAULT_SIM_SLOT
        }
        selectSimSlot(persistedSlot, persist = false)
        binding.simToggleGroup.visibility = View.VISIBLE
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

    private fun setupQuickTransfer() {
        binding.switchQuickTransfer.isChecked = viewModel.isQuickTransferEnabled.value

        binding.switchQuickTransfer.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setQuickTransferEnabled(isChecked)
            animateInputCardChange()
            updateQuickTransferVisibility()
            updateUssdPreview()
        }

        // Remove previous btnSaveEditPin handling
        // pinLayout now has a start icon (save) that triggers save action when editing
        binding.pinLayout.setStartIconOnClickListener {
            if (viewModel.isPinEditing.value) {
                val pin = binding.etQuickPin.text.toString().trim()
                if (pin.length != 4) {
                    binding.pinLayout.error = getString(R.string.error_invalid_pin)
                    return@setStartIconOnClickListener
                }
                binding.pinLayout.error = null
                viewModel.saveQuickTransferPin(pin)
                hideKeyboardAndClearInputFocus()
                Toast.makeText(this, R.string.msg_pin_saved, Toast.LENGTH_SHORT).show()
                updateUssdPreview()
                // Switch to edit mode after saving
                viewModel.setPinEditing(false)
            } else {
                // Enter edit mode
                viewModel.setPinEditing(true)
                binding.etQuickPin.requestFocus()
                binding.etQuickPin.setSelection(binding.etQuickPin.text?.length ?: 0)
                imm.showSoftInput(binding.etQuickPin, InputMethodManager.SHOW_IMPLICIT)
            }
        }

        binding.etQuickPin.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.pinLayout.error = null
                updateUssdPreview()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        updateQuickTransferVisibility()
    }

    private fun updateQuickTransferVisibility() {
        val isJawwalPay = currentType == "WALLET_2" || currentType == "MERCHANT_2"
        val isQuickEnabled = viewModel.isQuickTransferEnabled.value

        if (isJawwalPay) {
            binding.quickTransferContainer.visibility = View.VISIBLE
            if (isQuickEnabled) {
                binding.pinLayout.visibility = View.VISIBLE
                binding.warningCard.visibility = View.VISIBLE
            } else {
                binding.pinLayout.visibility = View.GONE
                binding.warningCard.visibility = View.GONE
            }
        } else {
            binding.quickTransferContainer.visibility = View.GONE
            binding.warningCard.visibility = View.GONE
        }
    }

    private fun setupContactPicker() {
        binding.recipientLayout.setEndIconOnClickListener {
            if (isContactPickerLaunching) return@setEndIconOnClickListener
            val now = SystemClock.elapsedRealtime()
            if (now - lastContactPickerClickTime < 800) return@setEndIconOnClickListener
            lastContactPickerClickTime = now

            isContactPickerLaunching = true
            hideKeyboardAndClearInputFocus()

            val pickPhoneIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
            try {
                contactPickerLauncher.launch(pickPhoneIntent)
            } catch (_: Exception) {
                isContactPickerLaunching = false
            }
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
            bottomSheet.onTransactionClick = { recipient, amount, type, simSlot ->
                fillInputs(recipient, amount, type, simSlot)
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
            PrivacyPolicyDialog.newInstance().show(supportFragmentManager, "privacy")
        }
        binding.toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_whats_new -> {
                    WhatsNewDialog.newInstance().show(supportFragmentManager, "whats_new")
                    true
                }
                R.id.action_privacy -> {
                    PrivacyPolicyDialog.newInstance().show(supportFragmentManager, "privacy")
                    true
                }
                else -> false
            }
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
                launch {
                    viewModel.isQuickTransferEnabled.collect { isEnabled ->
                        if (binding.switchQuickTransfer.isChecked != isEnabled) {
                            binding.switchQuickTransfer.isChecked = isEnabled
                        }
                        updateQuickTransferVisibility()
                        updateUssdPreview()
                    }
                }
                launch {
                    viewModel.quickTransferPin.collect { pin ->
                        if (!viewModel.isPinEditing.value) {
                            if (binding.etQuickPin.text.toString() != pin) {
                                binding.etQuickPin.setText(pin)
                            }
                        }
                        updateUssdPreview()
                    }
                }
                launch {
                    viewModel.isPinEditing.collect { isEditing ->
                        binding.etQuickPin.isEnabled = isEditing
                        binding.etQuickPin.isFocusable = isEditing
                        binding.etQuickPin.isFocusableInTouchMode = isEditing
                        if (isEditing) {
                            binding.pinLayout.endIconMode = com.google.android.material.textfield.TextInputLayout.END_ICON_PASSWORD_TOGGLE
                            binding.pinLayout.startIconDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_save)
                            binding.pinLayout.setStartIconTintList(ContextCompat.getColorStateList(this@MainActivity, R.color.text_main))
                        } else {
                            binding.pinLayout.startIconDrawable = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_edit)
                            binding.pinLayout.setStartIconTintList(ContextCompat.getColorStateList(this@MainActivity, R.color.text_main))
                        }
                        updateUssdPreview()
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

        animateInputCardChange()

        if (currentType == "BANK") {
            binding.recipientLayout.visibility = View.GONE
            binding.amountLayout.visibility = View.GONE
            binding.quickTransferContainer.visibility = View.GONE
            binding.warningCard.visibility = View.GONE
            hideKeyboardAndClearInputFocus()
            val bankCode = viewModel.generateUssdCode(currentType, "", "")
            binding.tvUssdPreview.text = formatUssdForDisplay(bankCode)
        } else {
            binding.recipientLayout.visibility = View.VISIBLE
            binding.amountLayout.visibility = View.VISIBLE
            updateQuickTransferVisibility()
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

        val isJawwalPay = currentType == "WALLET_2" || currentType == "MERCHANT_2"
        val pinOverride = if (isJawwalPay && viewModel.isPinEditing.value) binding.etQuickPin.text.toString().trim() else null

        val code = viewModel.generateUssdCode(currentType, recipient, amount, pinOverride = pinOverride)
        binding.tvUssdPreview.text = if (code.isEmpty()) {
            getString(R.string.ussd_preview_hint)
        } else {
            formatUssdForDisplay(code)
        }
    }

    // endregion

    // region Action Handling

    private fun validateInputs(recipient: String, amount: String): Boolean {
        binding.recipientLayout.error = null
        binding.amountLayout.error = null
        binding.pinLayout.error = null

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

        val isJawwalPay = currentType == "WALLET_2" || currentType == "MERCHANT_2"
        if (isJawwalPay && viewModel.isQuickTransferEnabled.value) {
            val currentPin = if (viewModel.isPinEditing.value) {
                binding.etQuickPin.text.toString().trim()
            } else {
                viewModel.quickTransferPin.value
            }
            if (currentPin.length != 4) {
                binding.pinLayout.error = getString(R.string.error_invalid_pin)
                isValid = false
            }
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

        val isJawwalPay = currentType == "WALLET_2" || currentType == "MERCHANT_2"
        val pinOverride = if (isJawwalPay && viewModel.isPinEditing.value) binding.etQuickPin.text.toString().trim() else null

        val code = viewModel.generateUssdCode(currentType, recipient, amount, pinOverride = pinOverride)

        when (action) {
            "CALL" -> checkCallPermissionAndMakeCall()
            "TRANSFER" -> {
                val intent = Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(code)}".toUri())
                startActivity(intent)
                if (currentType != "BANK") {
                    viewModel.saveTransaction(recipient, amount, currentType, selectedSimSlot)
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
        val missingPermissions = requiredDirectCallPermissions().filterNot { isPermissionGranted(it) }
        if (missingPermissions.isEmpty()) {
            makeDirectCall()
        } else {
            skipNextSimSelectorRefresh = true
            requestDirectCallPermissionsLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    private fun makeDirectCall() {
        val recipient = normalizePalestinianMobile(binding.etRecipient.text.toString())
        val amount = binding.etAmount.text.toString().trim()

        if (!validateInputs(recipient, amount)) return

        if (!hasDirectCallPermissions()) {
            checkCallPermissionAndMakeCall()
            return
        }

        val telecomManager = getSystemService(TelecomManager::class.java)
        if (telecomManager == null) {
            Toast.makeText(this, R.string.error_direct_call_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        activeSimOptions = loadActiveSimOptions()
        if (activeSimOptions.isEmpty()) {
            selectedSimSlot = DEFAULT_SIM_SLOT
            Toast.makeText(this, R.string.error_direct_call_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        if (activeSimOptions.size < 2) {
            selectedSimSlot = DEFAULT_SIM_SLOT
        }

        val selectedPhoneAccount = if (activeSimOptions.size >= 2) {
            resolvePhoneAccountHandleForSelectedSim(telecomManager)
        } else {
            null
        }

        if (activeSimOptions.size >= 2 && selectedPhoneAccount == null) {
            Toast.makeText(this, R.string.error_direct_call_unavailable, Toast.LENGTH_SHORT).show()
            return
        }

        val isJawwalPay = currentType == "WALLET_2" || currentType == "MERCHANT_2"
        val pinOverride = if (isJawwalPay && viewModel.isPinEditing.value) binding.etQuickPin.text.toString().trim() else null

        val code = viewModel.generateUssdCode(currentType, recipient, amount, pinOverride = pinOverride)
        val encodedCode = Uri.encode(code)
        val phoneUri = "tel:$encodedCode".toUri()
        val callExtras = Bundle().apply {
            selectedPhoneAccount?.let {
                putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it)
            }
        }

        try {
            telecomManager.placeCall(phoneUri, callExtras)
            viewModel.saveLastSimSlot(selectedSimSlot)
            if (currentType != "BANK") {
                viewModel.saveTransaction(recipient, amount, currentType, selectedSimSlot)
            }
        } catch (_: SecurityException) {
            showDirectCallPermissionDialog()
        } catch (_: Exception) {
            Toast.makeText(this, R.string.error_direct_call_unavailable, Toast.LENGTH_SHORT).show()
        }
    }

    private fun requiredDirectCallPermissions(): Array<String> {
        return arrayOf(
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_PHONE_STATE
        )
    }

    private fun hasDirectCallPermissions(): Boolean {
        return requiredDirectCallPermissions().all { isPermissionGranted(it) }
    }

    private fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun showDirectCallPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.permission_direct_call_title)
            .setMessage(R.string.permission_direct_call_message)
            .setPositiveButton(R.string.btn_close, null)
            .show()
    }

    private fun loadActiveSimOptions(): List<SimOption> {
        if (!isPermissionGranted(Manifest.permission.READ_PHONE_STATE)) return emptyList()

        val subscriptionManager = getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val subscriptions = try {
            subscriptionManager.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            return emptyList()
        }

        return subscriptions
            .sortedWith(
                compareBy<SubscriptionInfo> {
                    if (it.simSlotIndex >= 0) it.simSlotIndex else Int.MAX_VALUE
                }.thenBy { it.subscriptionId }
            )
            .take(2)
            .mapIndexed { index, subscription ->
                val displayName = subscription.displayName
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: getString(
                        if (index == 0) R.string.sim_1_default else R.string.sim_2_default
                    )
                SimOption(
                    slot = index + 1,
                    subscriptionId = subscription.subscriptionId,
                    displayName = displayName
                )
            }
    }

    private fun resolvePhoneAccountHandleForSelectedSim(
        telecomManager: TelecomManager
    ): PhoneAccountHandle? {
        val selectedOption = activeSimOptions.firstOrNull { it.slot == selectedSimSlot }
            ?: activeSimOptions.firstOrNull()
            ?: return null

        selectedSimSlot = selectedOption.slot

        val callCapableAccounts = try {
            telecomManager.callCapablePhoneAccounts.orEmpty()
        } catch (_: SecurityException) {
            return null
        }

        return callCapableAccounts.firstOrNull { handle ->
            getSubscriptionIdForPhoneAccount(telecomManager, handle) == selectedOption.subscriptionId
        }
    }

    private fun getSubscriptionIdForPhoneAccount(
        telecomManager: TelecomManager,
        handle: PhoneAccountHandle
    ): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val telephonyManager = getSystemService(TelephonyManager::class.java)
            val subscriptionId = try {
                telephonyManager?.getSubscriptionId(handle) ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
            } catch (_: SecurityException) {
                SubscriptionManager.INVALID_SUBSCRIPTION_ID
            }
            if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                return subscriptionId
            }
        }

        val phoneAccount = try {
            telecomManager.getPhoneAccount(handle)
        } catch (_: SecurityException) {
            null
        } ?: return SubscriptionManager.INVALID_SUBSCRIPTION_ID

        return getSubscriptionIdFromPhoneAccount(phoneAccount)
    }

    private fun getSubscriptionIdFromPhoneAccount(phoneAccount: PhoneAccount): Int {
        val extras = phoneAccount.extras ?: return SubscriptionManager.INVALID_SUBSCRIPTION_ID
        val subscriptionId = extras.getInt(
            EXTRA_SUBSCRIPTION_ID,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )
        if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            return subscriptionId
        }
        return extras.getInt(
            EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID
        )
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

    private fun fillInputs(recipient: String, amount: String, type: String? = null, simSlot: Int? = null) {
        if (type != null) {
            val tabIndex = typeToTabIndex(type)
            binding.tabLayout.getTabAt(tabIndex)?.select()
        }

        simSlot?.let { selectSimSlot(it, persist = false) }

        val normalizedRecipient = normalizePalestinianMobile(recipient)

        if (currentType != "BANK") {
            binding.etRecipient.setText(normalizedRecipient.ifEmpty { recipient })
            binding.etAmount.setText(amount)
        }

        updateUssdPreview()

        binding.scrollView.smoothScrollTo(0, 0)

        if (currentType != "BANK" && amount.isEmpty()) {
            focusAmountDelayed()
        }
    }

    private fun selectSimSlot(simSlot: Int, persist: Boolean) {
        val normalizedSlot = if (simSlot == SECOND_SIM_SLOT) SECOND_SIM_SLOT else DEFAULT_SIM_SLOT
        selectedSimSlot = normalizedSlot

        val buttonId = if (normalizedSlot == SECOND_SIM_SLOT) R.id.btnSim2 else R.id.btnSim1
        if (binding.simToggleGroup.checkedButtonId != buttonId) {
            isUpdatingSimSelection = true
            try {
                binding.simToggleGroup.check(buttonId)
            } finally {
                isUpdatingSimSelection = false
            }
        }

        if (persist) {
            viewModel.saveLastSimSlot(normalizedSlot)
        }
    }

    private fun hideKeyboardAndClearInputFocus() {
        binding.etRecipient.clearFocus()
        binding.etAmount.clearFocus()
        binding.etQuickPin.clearFocus()
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

    private fun focusAmount() {
        if (binding.amountLayout.visibility != View.VISIBLE) return
        binding.etAmount.requestFocus()
        imm.showSoftInput(binding.etAmount, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun focusAmountDelayed() {
        binding.etAmount.postDelayed({
            binding.etAmount.requestFocus()
            imm.showSoftInput(binding.etAmount, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    // endregion
}
