# OneTapTransfer (حوّل بكبسة زر)

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)
[![Google Play](https://img.shields.io/badge/Google%20Play-Published-brightgreen.svg)](https://play.google.com/store/apps/details?id=dev.anonymous.onetaptransfer)

**OneTapTransfer** is a lightweight, open-source Android application designed to simplify electronic wallet transfers in Palestine using short USSD dialer codes with a single tap, without requiring an active internet connection.

---

## 🇵🇸 Context & Purpose

In Palestine—and particularly in **Gaza**—the destruction of telecom and power infrastructure, along with continuous network disruptions and cash shortages, has made digital financial transactions an absolute necessity for daily survival. People rely heavily on mobile wallets like **PalPay** and **Jawwal Pay** for buying essentials, peer-to-peer transfers, and merchant payments.

Because mobile internet (3G/4G/Wi-Fi) is frequently unavailable or unstable, USSD shortcodes via mobile cellular networks are often the only functioning way to move funds. **OneTapTransfer** eliminates the tedious and error-prone process of manually typing complex USSD dialer combinations under stressful conditions, enabling fast, reliable, and offline financial transfers with a single click.

---

## ✨ Features

- **PalPay Integration (Wallet 1):**
  - Transfer to Friend (`*370*1*1*recipient*amount#`)
  - Transfer to Merchant (`*370*2*recipient*amount#`)

- **Jawwal Pay Integration (Wallet 2):**
  - Transfer to Friend (`*268*1*recipient*amount#`)
  - Transfer to Merchant (`*268*2*recipient*amount#`)
  - **Quick Transfer Mode:** Jawwal Pay allows embedding a secure 4-digit PIN directly into the USSD code (`*110*1*PIN*recipient*amount*1#` / `*110*2*PIN*recipient*amount*1#`) for instant, one-step completion without manual confirmation prompts.
    > *Note: The `*110*` Quick Transfer shortcode functions exclusively on **Jawwal** SIM cards and does **not** work on **Ooredoo** SIM cards.*

- **Dual SIM Support:**
  - Detects active SIM slots (SIM 1 / SIM 2) and allows direct dialing through the chosen carrier slot.

- **Contact Picker & Pinned Contacts:**
  - Select recipients directly via the official Android System Contact Picker.
  - Pin frequent contacts for rapid access.

- **Transaction History:**
  - Keeps a local log of recent transfers on your device for easy tracking.

- **100% Offline & Private:**
  - Requires **NO Internet permission**.
  - Zero telemetry, data collection, or external servers. All data remains stored locally on your device.

---

## 📱 Tech Stack

- **Language:** Kotlin
- **UI Architecture:** Android Views with Material Design 3 (ViewBinding, ViewModel, StateFlow)
- **Min SDK:** 24 (Android 7.0)
- **Target SDK:** 35 (Android 15)

---

## 🔒 Privacy & Security

OneTapTransfer acts strictly as a user interface shortcut generator that interfaces with your device's native phone dialer:
- Uses official Android system APIs for phone calls and contact selection.
- Does not store or transmit financial credentials or PIN codes externally.
- Fully transparent and open-source for community auditing.

---

## 👤 Author & Contribution

Developed with care by **Omar Mustafa Abu Shanb**.

*This application was built as a temporary tool to assist our people during difficult circumstances. May it bring ease and utility to those in need.*
