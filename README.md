# Attendance-app

# Attendance App (Android + FastAPI + AWS Rekognition) — README

A production-leaning MVP for selfie-based classroom attendance with a strict time window and in-room proximity guard.

> **Stack**
>
> * **Android**: Kotlin + Jetpack Compose, CameraX selfie capture, fused high-accuracy location, Retrofit.
> * **Backend**: FastAPI (Python), AWS Rekognition for face verification, S3 for storage.
> * **Auth**: stubbed (ready to add Firebase Auth or Cognito).
> * **Swap-ready**: You can replace Rekognition with **Azure Face API** with minimal backend changes (see §8).

---

## 1) What’s included

* 📱 **Android app**: Student & Instructor tabs, selfie capture, location check (≤10 m target), 10-minute window guard, multipart upload.
* ☁️ **FastAPI backend**:

  * `POST /session/start` — open a timed attendance window with center (lat/lon) + radius.
  * `GET /session/current` — fetch current open window for a class.
  * `POST /enroll` — one-time student selfie enrollment → stores in S3.
  * `POST /attendance/checkin` — window + distance checks → Rekognition `CompareFaces` → returns `{ ok, confidence }`.
* 🔐 **Best practices baked in**: no cloud secrets in the app; the client only talks to your backend.

> ⚠️ The demo keeps sessions in memory for simplicity. For real usage, wire **DynamoDB** per §7.

---

## 2) Repo layout (suggested)

```
attendance/
├─ android/                       # Android Studio project
│  └─ app/src/...                 # Code you pasted
└─ backend/
   ├─ app.py                      # FastAPI app (given)
   ├─ requirements.txt            # deps
   ├─ Dockerfile                  # (below)
   └─ README.md                   # (this file)
```

### `backend/Dockerfile` (optional but recommended for cloud deploys)

```dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY app.py .
ENV PORT=8000
CMD ["uvicorn", "app:app", "--host", "0.0.0.0", "--port", "8000"]
```

---

## 3) Prerequisites

* **Android**

  * Android Studio Koala (or newer), **compileSdk 34**.
  * Physical device recommended (camera + GPS), Android 9+ preferred.

* **AWS**

  * An AWS account in **ap-south-1** (Mumbai) or **ap-south-2** (Hyderabad) to keep data in India.
  * An **S3 bucket** (e.g., `attendance-prod-photos`).
  * A backend compute option (choose one):

    * **AWS App Runner** (simplest for containers), or
    * **ECS Fargate**, or
    * **EC2** (long-running `uvicorn`), or
    * **Lambda + API Gateway** (with a framework like Mangum/FastAPI on Lambda).

* **IAM policy** for your backend role/user (minimum):

```json
{
  "Version": "2012-10-17",
  "Statement": [
    { "Effect": "Allow",
      "Action": ["rekognition:CompareFaces"],
      "Resource": "*" },
    { "Effect": "Allow",
      "Action": ["s3:GetObject","s3:PutObject"],
      "Resource": [
        "arn:aws:s3:::attendance-prod-photos/*"
      ] }
  ]
}
```

---

## 4) Backend — configure & run

### 4.1 Local run (for quick smoke tests)

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt

# Set env (use your values)
export AWS_REGION=ap-south-1
export S3_BUCKET=attendance-prod-photos
uvicorn app:app --reload --port 8000
```

The API will be at `http://127.0.0.1:8000/`.

### 4.2 Cloud deployment (two easy paths)

**Option A — App Runner (container):**

1. Build and push your image to ECR.
2. Create an App Runner service from ECR.
3. Set ENV: `AWS_REGION=ap-south-1`, `S3_BUCKET=<your-bucket>`.
4. Attach an **IAM role** with the policy above.
5. Copy the service HTTPS URL → use as Android `BASE_URL`.

**Option B — ECS Fargate:**

1. Create a Fargate service with the same image.
2. Place behind an ALB (HTTPS).
3. Task execution role: allow pulling image from ECR.
4. Task role: attach the **IAM policy** above.
5. Add ENV vars; use the ALB domain as `BASE_URL`.

> You can also run on **EC2** with systemd + Nginx, or **Lambda** (you’d add Mangum + a small handler). Choose what your team operates comfortably.

---

## 5) Android — configure & run

1. Open the `android/` project in Android Studio.
2. In `network/Client.kt`, set the **BASE\_URL** to your backend HTTPS, e.g.:

   ```kotlin
   private const val BASE_URL = "https://api.yourdomain.com/"
   ```
3. Build & run on a physical device.
4. In the demo UI:

   * **Instructor** tab → “Open attendance window”.
   * **Student** tab → “Take selfie” → “Submit”.

> Permissions requested at first launch: **Camera** and **Fine Location**.

---

## 6) API Reference (FastAPI)

### 6.1 `POST /session/start`

Open an attendance window for a class.

**Body (JSON)**:

```json
{
  "classId": "ME101",
  "centerLat": 20.1483,
  "centerLon": 85.6720,
  "radiusMeters": 10,
  "durationMinutes": 10
}
```

**Response**:

```json
{ "ok": true }
```

### 6.2 `GET /session/current?classId=ME101`

Fetch the active window.

**Response**:

```json
{
  "classId":"ME101",
  "centerLat":20.1483,
  "centerLon":85.6720,
  "radiusMeters":10,
  "expiresAtEpochMs": 1734100000000
}
```

### 6.3 `POST /enroll`

Store a student’s reference selfie in S3. **Do once per student.**

**Form-data**:

* `studentId`: string (e.g., `iitbbs_21cs02010`)
* `image`: file (JPEG)

**curl**:

```bash
curl -X POST https://<BASE>/enroll \
  -F studentId=iitbbs_21cs02010 \
  -F image=@/path/to/selfie.jpg
```

**Response**:

```json
{ "ok": true }
```

### 6.4 `POST /attendance/checkin`

Checks time window, distance, and face match.

**Form-data**:

* `classId`: string (e.g., `ME101`)
* `lat`: float
* `lon`: float
* `image`: file (JPEG, the selfie)
* `studentId`: string *(temporary; in production derive from Auth token)*

**curl**:

```bash
curl -X POST https://<BASE>/attendance/checkin \
  -F classId=ME101 \
  -F lat=20.14835 \
  -F lon=85.67198 \
  -F studentId=iitbbs_21cs02010 \
  -F image=@/path/to/selfie.jpg
```

**Response (success)**:

```json
{ "ok": true, "matchConfidence": 99.7 }
```

**Response (failure)**:

```json
{ "ok": false, "message": "Outside radius (23.4m)" }
```

---

## 7) Production hardening (recommended next steps)

* **Auth**: Add Firebase Auth (Android) → send `Authorization: Bearer <idToken>` → verify in FastAPI (Firebase Admin SDK) and derive `studentId` from `uid`. Remove `studentId` from client body.
* **Persistence**: Replace in-memory maps with **DynamoDB**:

  * `Sessions` — PK: `classId`; attrs: `centerLat`, `centerLon`, `radius`, `expiresAt`.
  * `Attendance` — PK: `classId#YYYYMMDD`, SK: `studentId`; attrs: `timestamp`, `confidence`, `photoKey?`.
  * `Students` — PK: `studentId`; attrs: `refPhotoKey`, `metadata`.
* **Liveness**: Add **Rekognition Face Liveness** (active selfie challenge) before CompareFaces.
* **Device integrity**: Enforce **Play Integrity API** (reject emulators/compromised devices).
* **Proximity**: For reliable “in-room” checks:

  * Try **Wi-Fi RTT (802.11mc/az)** on supported APs → \~0.5–8 m.
  * Or drop a **BLE beacon** in each classroom; verify proximity with RSSI.
* **Security**: TLS only, least-privilege IAM, KMS for S3/DynamoDB encryption, audit logs for admin actions.
* **Privacy**: Treat embeddings/selfies as sensitive educational records; add consent UX and clear retention policies.

---

## 8) Swapping Rekognition → Azure Face API (optional)

You can keep the Android app unchanged and switch only the backend verification:

1. **Env**: add

   * `AZURE_FACE_ENDPOINT=https://<your-face>.cognitiveservices.azure.com/`
   * `AZURE_FACE_KEY=...`
2. **Backend change**: Replace the `rekognition.compare_faces` block with Azure’s **Liveness + Verify** workflow:

   * Run **Face Liveness** (client SDK or server-assisted) to get a live selfie frame.
   * Call **Verify** (`/face/v1.0/verify`) against the enrolled face (store person in a **PersonGroup** or keep a reference image and compute/compare embeddings with Azure APIs).
3. **Storage**: You can keep enrollment photos in S3 or move them to Azure Blob; adjust your storage client accordingly.

*(If you want, I can provide a drop-in `verify_azure.py` helper and a small interface layer so your FastAPI route code stays the same.)*

---

## 9) Troubleshooting

* **Android “Location unavailable”**: Ensure GPS is on; grant **Fine Location**. Try stepping outdoors for first fix.
* **“No active session”**: Instructor must open a window (`/session/start`) first.
* **“Unauthenticated”** on check-in: The demo expects `studentId` in the form; in production you’ll send an ID token instead.
* **Face mismatch**: Re-enroll with a clean, well-lit reference selfie; avoid glasses/occlusions for enrollment.
* **S3 access denied**: Recheck IAM policy principal (role/user) and the bucket name/region.

---

## 10) Configuration checklist

* [ ] **Android** `BASE_URL` points to your HTTPS backend.
* [ ] **Backend** env: `AWS_REGION`, `S3_BUCKET`.
* [ ] **IAM** role/user has `rekognition:CompareFaces` + `s3:GetObject/PutObject` on your bucket.
* [ ] **TLS** endpoint reachable from devices on campus networks.
* [ ] **Time & radius** tuned (e.g., 10–20 m radius indoors; combine with BLE/RTT for reliability).

---

## 11) License & attribution

* Sample code is provided “as is,” for you to adapt. You are responsible for compliance with campus policy, FERPA, and local privacy laws.

---

## 12) Quick demo script

1. Instructor opens **Instructor** tab → “Open attendance window”.
2. Student opens **Student** tab → taps **Take selfie** → **Submit**.
3. Backend validates time + distance → Rekognition match → returns confidence → app shows “Attendance marked”.

---

### Want extras?

I can extend this README with:

* Firebase Auth wiring (server verification code + Android token attach),
* DynamoDB schema & `boto3` DAO helpers,
* Rekognition **Face Liveness** integration,
* A Terraform starter for S3/IAM/App Runner.
