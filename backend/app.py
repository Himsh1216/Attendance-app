import os
import uuid
import time
from datetime import datetime, timedelta
from typing import Dict

import boto3
from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.responses import JSONResponse

AWS_REGION = os.environ.get("AWS_REGION", "ap-south-1")
S3_BUCKET = os.environ.get("S3_BUCKET", "attendance-prod-photos")

s3 = boto3.client("s3", region_name=AWS_REGION)
rekognition = boto3.client("rekognition", region_name=AWS_REGION)

app = FastAPI()

# In-memory stores
sessions: Dict[str, Dict] = {}
students: Dict[str, str] = {}


@app.post("/session/start")
async def start_session(
    classId: str = Form(...),
    lat: float = Form(...),
    lon: float = Form(...),
    radius: float = Form(...),
    durationMinutes: int = Form(10),
):
    expires = datetime.utcnow() + timedelta(minutes=durationMinutes)
    sessions[classId] = {
        "centerLat": lat,
        "centerLon": lon,
        "radius": radius,
        "expiresAt": expires,
    }
    return {"ok": True}


@app.get("/session/current")
async def current_session(classId: str):
    sess = sessions.get(classId)
    if not sess or sess["expiresAt"] < datetime.utcnow():
        raise HTTPException(404, "No active session")
    data = dict(sess)
    data["expiresAt"] = sess["expiresAt"].isoformat()
    data["ok"] = True
    return data


@app.post("/enroll")
async def enroll(studentId: str = Form(...), image: UploadFile = File(...)):
    key = f"enroll/{studentId}.jpg"
    s3.upload_fileobj(image.file, S3_BUCKET, key)
    students[studentId] = key
    return {"ok": True}


def _haversine(lat1, lon1, lat2, lon2):
    from math import radians, cos, sin, asin, sqrt
    R = 6371000
    dlat = radians(lat2 - lat1)
    dlon = radians(lon2 - lon1)
    a = sin(dlat/2)**2 + cos(radians(lat1))*cos(radians(lat2))*sin(dlon/2)**2
    c = 2*asin(sqrt(a))
    return R * c


@app.post("/attendance/checkin")
async def checkin(
    classId: str = Form(...),
    lat: float = Form(...),
    lon: float = Form(...),
    studentId: str = Form(...),
    image: UploadFile = File(...),
):
    sess = sessions.get(classId)
    if not sess or sess["expiresAt"] < datetime.utcnow():
        return JSONResponse({"ok": False, "message": "No active session"}, status_code=400)

    dist = _haversine(lat, lon, sess["centerLat"], sess["centerLon"])
    if dist > sess["radius"]:
        return {"ok": False, "message": f"Outside radius ({dist:.1f}m)"}

    ref_key = students.get(studentId)
    if not ref_key:
        return {"ok": False, "message": "Student not enrolled"}

    # Upload selfie to temp S3 and compare
    selfie_key = f"checkin/{studentId}-{uuid.uuid4().hex}.jpg"
    image.file.seek(0)
    s3.upload_fileobj(image.file, S3_BUCKET, selfie_key)

    try:
        resp = rekognition.compare_faces(
            SourceImage={"S3Object": {"Bucket": S3_BUCKET, "Name": selfie_key}},
            TargetImage={"S3Object": {"Bucket": S3_BUCKET, "Name": ref_key}},
            SimilarityThreshold=80,
        )
        matches = resp.get("FaceMatches", [])
        confidence = matches[0]["Similarity"] if matches else 0.0
    except Exception as e:
        return {"ok": False, "message": str(e)}

    return {"ok": True, "matchConfidence": confidence}
