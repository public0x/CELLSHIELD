# CellShield: Rogue Base Station Detection & Prevention System

![Platform](https://img.shields.io/badge/Platform-Android-green)
![Language](https://img.shields.io/badge/Language-Kotlin-purple)
![Status](https://img.shields.io/badge/Status-Completed-blue)

## 📌 Project Overview
[cite_start]**CellShield** is a comprehensive mobile security system designed to identify and mitigate threats posed by **Fake Base Transceiver Stations (FBTS)**, also known as IMSI catchers or Stingrays[cite: 275].

As mobile networks expand, vulnerabilities in 2G/3G/4G/5G protocols allow malicious actors to impersonate legitimate cell towers. [cite_start]CellShield runs on Android to continuously monitor network parameters, detecting anomalies in real-time and deploying active countermeasures to protect user privacy[cite: 276, 277].

[cite_start]This project was developed as a Final Year Project for the **German-Malaysian Institute (GMI)**[cite: 4].

## 🚀 Key Features

### 1. Real-Time Detection Engine
The app continuously scans cellular network parameters including:
- [cite_start]**Cell ID & Signal Strength:** Monitors for abnormally high signal strength or unknown Cell IDs[cite: 276].
- **Consistency Checks:** Validates Mobile Country Code (MCC) and Mobile Network Code (MNC) against known trusted networks.
- **Downgrade Attack Detection:** Identifies forced downgrades to less secure protocols (e.g., 4G to 2G) often used by attackers.

### 2. Active Countermeasures
[cite_start]Upon detecting a high-confidence threat, CellShield empowers the user with immediate actions[cite: 277, 1348]:
- **Link Routing:** Recommendations to switch to secure Wi-Fi or VPN.
- **Airplane Mode / Network Reset:** Instructions to sever the connection to the rogue tower immediately.

### 3. Visualization & Logging
- [cite_start]**Threat Heatmap:** Visualizes network activity and locations of potential rogue BTS on a map[cite: 727].
- [cite_start]**Cloud & Local Logging:** Syncs detection history to **Firebase Firestore** while maintaining a local **SQLite** backup for offline analysis[cite: 836].
- [cite_start]**Push Notifications:** Delivers real-time critical alerts via Firebase Cloud Messaging[cite: 694].

## 🛠️ Technical Architecture

### Tech Stack
- **Language:** Kotlin
- **Backend:** Firebase (Authentication, Firestore, Cloud Messaging)
- **Local Database:** SQLite
- [cite_start]**Hardware for Testing:** HackRF One (Software Defined Radio) was used to simulate rogue signals during the validation phase[cite: 683].

### Architecture Modules
The codebase is structured into modular components for scalability:
- **`btsdetection`**: Core logic for analyzing signal anomalies.
- **`CountermeasuresManager`**: Handles user interventions (VPN, settings redirection).
- **`service`**: Manages background scanning tasks to ensure protection even when the app is closed.

## 📸 Screenshots
| Dashboard (Safe) | Threat Detected |
|:---:|:---:|
| <img src="screenshots/dashboard_safe.png" width="250"> | <img src="screenshots/alert_critical.png" width="250"> |
*(Note: Place your screenshots in a folder named `screenshots`)*

## ⚠️ Disclaimer
This application is a proof-of-concept developed for educational and research purposes. It is intended to demonstrate vulnerabilities in cellular networks and methods for detection. The authors and institution are not responsible for any misuse of this software.

## 👥 Authors
- **Muhammad Ammar Bin Adi Harrizam**
- **Muhammad Ashrani Bin Ariff**
- **Muhammad Airil Muqrish Bin Muhammad Izuan**
- **Nur Hazirah Binti Mohammad Ajlan**

**Supervisor:** Sir Muhammad Shafiq Bin Othman  
[cite_start]**Institute:** German-Malaysian Institute (GMI) [cite: 30-39]

## 📄 License
This project is part of the GMI Network Security Diploma curriculum. [cite_start]Intellectual property rights belong to the German-Malaysian Institute[cite: 66].
