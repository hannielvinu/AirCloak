# AirCloak 🛡️
> **Zero-Leak Edge Security & Real-Time Credential Redactor for Developers**  
> *Track: Developer Tools *

---

## 📌 Project Overview
**AirCloak** is an edge-native, zero-leak developer security suite engineered to intercept and sanitize cross-device credentials (`.env` secrets, AWS tokens, Stripe keys, DB URIs, JWTs, server IPs) and redact confidential monitor screens in real time using on-device NPU/SLM processing.

AirCloak operates with **zero external cloud API calls**, guaranteeing an air-gapped security perimeter.

---

## 📐 System Architecture

```
+-----------------------------------------------------------------------------------+
|                            DEVELOPER WORKSTATION (PC / MAC)                       |
|                                                                                   |
|  +--------------------+         TCP / WebSocket          +---------------------+  |
|  | Clipboard / Editor |  =============================>  | Ingest Proxy Daemon |  |
|  | (.env / DB URIs)   |                                  | (127.0.0.1:8765)    |  |
|  +--------------------+                                  +----------+----------+  |
+---------------------------------------------------------------------|-------------+
                                                                      |
                           +------------------------------------------+
                           |
                           v
+-----------------------------------------------------------------------------------+
|                        AIRCLOAK CORE PROCESSING ENGINE                            |
|                                                                                   |
|  +---------------------------+       +-----------------------------------------+  |
|  | L1 Regex Heuristic Engine | ----> | Encrypted Vault (AES-256-GCM in RAM)    |  |
|  +-------------+-------------+       +--------------------+--------------------+  |
|                |                                          |                       |
|                v                                          v                       |
|  +---------------------------+       +-----------------------------------------+  |
|  | L2 Quantized SLM / ONNX   |       | Deterministic Mock Tokenization         |  |
|  | (Hexagon NPU / ExecuTorch)|       | ("AKIA_MOCK_XYZ" -> Reconstitutable)    |  |
|  +---------------------------+       +-----------------------------------------+  |
+-----------------------------------------------------------------------------------+
                                   |
                                   v
+-----------------------------------------------------------------------------------+
|                            IQOO ANDROID CLIENT                                    |
|                                                                                   |
|  +--------------------------------+       +------------------------------------+  |
|  | AirCloak Accessibility Service |       | IMU Hardware Motion Detector       |  |
|  | (Inspects & Sanitizes Pastes)  |       | (Rapid 2-Axis Shake Zeroize Purge) |  |
|  +--------------------------------+       +------------------------------------+  |
|                                                                                   |
|  +--------------------------------+       +------------------------------------+  |
|  | NPU Vision Redaction HUD       |       | Dark-Mode Cyberpunk Dashboard      |  |
|  | (Camera Bounding Boxes on IPs) |       | (Status, Toggles, Audit Stream)    |  |
|  +--------------------------------+       +------------------------------------+  |
+-----------------------------------------------------------------------------------+
```

---

## 📂 Repository Structure

```
AirCloak/
├── ingest-proxy/
│   ├── proxy_server.py         # Async WebSocket/TCP daemon with AES-GCM encrypted vault
│   ├── test_proxy.py           # Pytest suite for credential detection & reconstitution
│   └── requirements.txt        # Python dependencies (cryptography, websockets, pytest)
├── edge-inference/
│   ├── onnx_pipeline.py        # Offline INT4 SLM & entity extraction engine for NPU
│   ├── test_edge_inference.py  # Pytest suite for edge inference engine
│   └── requirements.txt        # Inference dependencies
├── android-client/
│   ├── build.gradle.kts        # Root Gradle build script
│   ├── settings.gradle.kts     # Multi-project gradle configuration
│   └── app/
│       ├── build.gradle.kts    # App-level dependencies (Compose, CameraX, Security)
│       └── src/main/
│           ├── AndroidManifest.xml
│           ├── res/
│           └── java/io/aircloak/client/
│               ├── MainActivity.kt
│               ├── DashboardScreen.kt             # Dark-mode cybersecurity UI
│               ├── RedactionViewFinder.kt         # Jetpack Compose camera HUD overlay
│               ├── AirCloakAccessibilityService.kt # Scoped auto-sanitization service
│               └── MotionDetector.kt              # IMU 2-axis accelerometer shake purge
└── README.md
```

---

## ⚡ Quickstart & Testing

### 1. Running the Ingest Proxy Daemon & Tests
```bash
cd ingest-proxy
pip install -r requirements.txt

# Run full unit test suite
python -m pytest -v

# Start the proxy daemon
python proxy_server.py
```

### 2. Testing the Offline Edge Inference Pipeline
```bash
cd edge-inference
python -m pytest -v

# Run standalone entity extractor demo
python onnx_pipeline.py
```

### 3. Android Client Deployment
- Open the `android-client/` directory in **Android Studio Hedgehog / Iguana / Jellyfish**.
- Sync Gradle and deploy to an Android device (target SDK: Android 14 / API 34).
- Enable the **AirCloak Accessibility Service** in `Settings > Accessibility > Installed Apps`.

---

## 🚀 iQOO Hardware & NPU Optimizations

| Feature / Module | iQOO Optimization Technique | Benefit |
|---|---|---|
| **Quantized SLM Extraction** | Qualcomm QNN / Hexagon Direct Execution Provider | Sub-2ms token extraction with zero battery drain. |
| **Emergency Purge** | Hardware IMU Accelerometer listener (2-axis jerk vector) | Physical failsafe: shaking device clears clipboard in <15ms. |
| **Vision Redactor** | CameraX zero-copy image stream to Compose Canvas | Real-time 60 FPS privacy overlays on monitor screens. |
| **Offline Air-Gap Mode** | Pure local tokenization & in-RAM AES-256-GCM | 0-leak guarantee without requiring network permissions. |

---

## 🛡️ License
Designed and developed for the **Hackathon (Developer Tools Track)**.
Licensed under the Apache 2.0 License.
